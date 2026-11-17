"""Spark job: read every scored transaction from MySQL over JDBC and build behaviour features per account.

Run (MySQL from docker compose or CI):
    python analytics/spark_features.py --jdbc "jdbc:mysql://localhost:3306/fraud" --out analytics/out
"""
import argparse
import json
import time
from pathlib import Path

from pyspark.sql import SparkSession, Window
from pyspark.sql import functions as F

HIGH_RISK = ["gift_cards", "crypto", "wire_transfer"]


def build_features(df):
    """One row per account. Uses window functions for time gaps and 10-minute bursts."""
    by_account = Window.partitionBy("account_id").orderBy("ts")
    burst = Window.partitionBy("account_id").orderBy("ts").rangeBetween(-600, 0)  # previous 10 minutes, in seconds
    df = (df.withColumn("ts", F.col("created_at").cast("long"))
            .withColumn("gap_s", F.col("ts") - F.lag("ts").over(by_account))
            .withColumn("in_10min", F.count("*").over(burst))
            .withColumn("prev_country", F.lag("country").over(by_account))
            .withColumn("country_jump_1h", ((F.col("prev_country") != F.col("country")) & (F.col("gap_s") < 3600)).cast("int"))
            .withColumn("amount", F.col("amount").cast("double")))
    return (df.groupBy("account_id").agg(
                F.count("*").alias("txn_count"),
                F.avg("amount").alias("avg_amount"),
                F.max("amount").alias("max_amount"),
                F.expr("percentile_approx(amount, 0.5)").alias("median_amount"),
                F.countDistinct("device_id").alias("devices"),
                F.countDistinct("country").alias("countries"),
                F.min("gap_s").alias("min_gap_s"),
                F.max("in_10min").alias("max_in_10min"),
                F.sum("country_jump_1h").alias("country_jumps_1h"),
                F.avg(F.col("merchant_category").isin(HIGH_RISK).cast("int")).alias("high_risk_share"),
                F.sum(((F.col("amount") >= 9000) & (F.col("amount") < 10000)).cast("int")).alias("near_10k_count"))
            .withColumn("max_over_median", F.col("max_amount") / F.col("median_amount"))
            .fillna({"min_gap_s": 10**7}))


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--jdbc", default="jdbc:mysql://localhost:3306/fraud")
    p.add_argument("--user", default="fraud")
    p.add_argument("--password", default="fraud")
    p.add_argument("--out", default="analytics/out")
    a = p.parse_args()

    spark = (SparkSession.builder.appName("fraud-account-features").master("local[*]")
             .config("spark.jars.packages", "com.mysql:mysql-connector-j:8.3.0")
             .config("spark.sql.session.timeZone", "UTC").getOrCreate())
    spark.sparkContext.setLogLevel("WARN")
    t0 = time.time()
    txns = (spark.read.format("jdbc").option("url", a.jdbc).option("dbtable", "transactions")
            .option("user", a.user).option("password", a.password)
            .option("partitionColumn", "id").option("lowerBound", 1).option("upperBound", 1_000_000)
            .option("numPartitions", 4).load())
    rows = txns.count()
    features = build_features(txns).cache()
    accounts = features.count()
    elapsed = time.time() - t0

    out = Path(a.out)
    out.mkdir(parents=True, exist_ok=True)
    features.toPandas().to_parquet(out / "account_features.parquet", index=False)
    stats = {"transactions_read": rows, "accounts": accounts, "spark_seconds": round(elapsed, 1),
             "partitions": txns.rdd.getNumPartitions()}
    (out / "spark_stats.json").write_text(json.dumps(stats))
    print(json.dumps(stats))
    spark.stop()


if __name__ == "__main__":
    main()

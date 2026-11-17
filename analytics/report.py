"""Writes the analytics results as a Markdown summary for the GitHub Actions run page."""
import json
from pathlib import Path

out = Path("analytics/out")
spark = json.loads((out / "spark_stats.json").read_text())
anom = json.loads((out / "anomaly_result.json").read_text())
ch = json.loads((out / "clickhouse_result.json").read_text())
lines = ["## Analytics: Spark, unsupervised ML and ClickHouse", "",
         "| Step | Result |", "|---|---|",
         f"| Spark (PySpark) read from MySQL over JDBC | {spark['transactions_read']} transactions, "
         f"{spark['accounts']} accounts, {spark['partitions']} partitions, {spark['spark_seconds']} s |",
         f"| Isolation Forest (unsupervised, no labels) | {anom['found_in_top_k']} of {anom['planted_fraud_accounts']} "
         f"planted-fraud accounts in its top {anom['planted_fraud_accounts']} (precision@k {anom['precision_at_k']}), "
         f"ROC-AUC {anom['roc_auc']} over {anom['accounts']} accounts |",
         f"| Kafka to ClickHouse (Kafka engine + materialized view) | {ch['rows_streamed_from_kafka']} decisions streamed |"]
for name in ["decisions", "rules_fired"]:
    q = ch[name]
    lines.append(f"| ClickHouse query `{name}` ({q['ms']} ms) | " +
                 "; ".join(" ".join(str(c) for c in r) for r in q["rows"]) + " |")
text = "\n".join(lines) + "\n"
(out / "analytics-report.md").write_text(text)
print(text)

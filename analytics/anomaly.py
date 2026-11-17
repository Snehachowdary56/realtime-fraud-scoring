"""Unsupervised anomaly detection: an Isolation Forest finds unusual accounts from the Spark features,
without using any fraud labels. The planted fraud accounts are used only afterwards, to score it.

Run after spark_features.py:  python analytics/anomaly.py --features analytics/out/account_features.parquet
"""
import argparse
import json
import re
from pathlib import Path

import numpy as np
import pandas as pd
from sklearn.ensemble import IsolationForest
from sklearn.metrics import roc_auc_score

FEATURES = ["txn_count", "avg_amount", "max_amount", "max_over_median", "devices", "countries",
            "min_gap_s", "max_in_10min", "country_jumps_1h", "high_risk_share", "near_10k_count"]
# Accounts from the end-to-end test look like acct-<run>-NNN; every 5th one (NNN % 5 == 0) had fraud planted.
ACCOUNT = re.compile(r"^acct-[0-9a-f]{8}-(\d{3})$")


def score(df: pd.DataFrame, seed: int = 7) -> pd.DataFrame:
    X = df[FEATURES].astype(float).copy()
    X["min_gap_s"] = -np.log1p(X["min_gap_s"])          # short gaps are suspicious, so flip the sign
    for c in ["avg_amount", "max_amount", "max_over_median", "txn_count"]:
        X[c] = np.log1p(X[c])
    model = IsolationForest(n_estimators=300, contamination="auto", random_state=seed).fit(X)
    out = df.copy()
    out["anomaly_score"] = -model.score_samples(X)        # higher = more unusual
    return out.sort_values("anomaly_score", ascending=False)


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--features", default="analytics/out/account_features.parquet")
    a = p.parse_args()
    df = pd.read_parquet(a.features)
    m = df["account_id"].str.extract(ACCOUNT)[0]
    df = df[m.notna()].copy()                              # only the labelled end-to-end accounts
    df["planted_fraud"] = (m[m.notna()].astype(int) % 5 == 0).astype(int)
    ranked = score(df)
    k = int(ranked["planted_fraud"].sum())
    top_k = ranked.head(k)
    result = {"accounts": len(ranked), "planted_fraud_accounts": k,
              "found_in_top_k": int(top_k["planted_fraud"].sum()),
              "precision_at_k": round(top_k["planted_fraud"].mean(), 3),
              "roc_auc": round(roc_auc_score(ranked["planted_fraud"], ranked["anomaly_score"]), 3)}
    Path(a.features).with_name("anomaly_result.json").write_text(json.dumps(result))
    ranked.head(10)[["account_id", "anomaly_score", "planted_fraud"]].to_csv(
        Path(a.features).with_name("top_anomalies.csv"), index=False)
    print(json.dumps(result))


if __name__ == "__main__":
    main()

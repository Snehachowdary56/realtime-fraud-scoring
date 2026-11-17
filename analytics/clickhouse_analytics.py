"""Creates the Kafka -> ClickHouse pipeline, waits for decisions to arrive, then runs analytical queries.

Run:  python analytics/clickhouse_analytics.py --host localhost --setup analytics/clickhouse_setup.sql
"""
import argparse
import json
import time
from pathlib import Path

import clickhouse_connect

QUERIES = {
    "decisions": "SELECT decision, count() AS n, round(avg(score), 1) AS avg_score FROM fraud_decisions "
                 "GROUP BY decision ORDER BY n DESC",
    "rules_fired": "SELECT splitByString(':', reason)[1] AS rule, count() AS times FROM fraud_decisions "
                   "ARRAY JOIN reasons AS reason GROUP BY rule ORDER BY times DESC",
    "riskiest_accounts": "SELECT accountId, max(score) AS max_score, countIf(decision != 'ALLOW') AS flagged "
                         "FROM fraud_decisions GROUP BY accountId ORDER BY max_score DESC, flagged DESC LIMIT 5",
}


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--host", default="localhost")
    p.add_argument("--user", default="fraud")
    p.add_argument("--password", default="fraud")
    p.add_argument("--setup", default="analytics/clickhouse_setup.sql")
    p.add_argument("--out", default="analytics/out")
    a = p.parse_args()
    ch = clickhouse_connect.get_client(host=a.host, username=a.user, password=a.password)
    for stmt in Path(a.setup).read_text().split(";"):
        body = "\n".join(l for l in stmt.splitlines() if not l.strip().startswith("--")).strip()
        if body:
            ch.command(body)

    # wait until the Kafka consumer has caught up (row count stops growing)
    last, stable, start = -1, 0, time.time()
    while time.time() - start < 120 and stable < 3:
        n = ch.command("SELECT count() FROM fraud_decisions")
        stable = stable + 1 if (n == last and n > 0) else 0
        last = n
        time.sleep(3)

    results = {"rows_streamed_from_kafka": int(last)}
    for name, sql in QUERIES.items():
        t0 = time.perf_counter()
        res = ch.query(sql)
        results[name] = {"ms": round((time.perf_counter() - t0) * 1000, 1),
                         "columns": list(res.column_names), "rows": [list(r) for r in res.result_rows]}
    Path(a.out).mkdir(parents=True, exist_ok=True)
    Path(a.out, "clickhouse_result.json").write_text(json.dumps(results, default=str))
    print(json.dumps(results, default=str, indent=1))
    assert results["rows_streamed_from_kafka"] > 0, "no decisions reached ClickHouse"


if __name__ == "__main__":
    main()

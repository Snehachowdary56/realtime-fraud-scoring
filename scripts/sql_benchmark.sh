#!/usr/bin/env bash
# SQL optimization benchmark for the fraud history queries.
# Seeds 500,000 transactions for 5,000 accounts, then runs the velocity and device lookups
# with and without the composite indexes and compares EXPLAIN ANALYZE plans and timings.
# Usage: MYSQL="mysql -h127.0.0.1 -uroot -proot" ./scripts/sql_benchmark.sh
set -euo pipefail
MYSQL=${MYSQL:-"mysql -h127.0.0.1 -uroot -proot"}
ROWS=${ROWS:-500000}
RUNS=${RUNS:-20}
q() { $MYSQL --batch --skip-column-names bench -e "$1" 2>/dev/null; }

$MYSQL -e "DROP DATABASE IF EXISTS bench; CREATE DATABASE bench;" 2>/dev/null
q "CREATE TABLE transactions (
     id BIGINT AUTO_INCREMENT PRIMARY KEY, txn_id VARCHAR(64) NOT NULL, account_id VARCHAR(64) NOT NULL,
     amount DECIMAL(12,2) NOT NULL, merchant_category VARCHAR(32) NOT NULL, country CHAR(2) NOT NULL,
     device_id VARCHAR(64) NOT NULL, created_at DATETIME(6) NOT NULL, score INT NOT NULL,
     decision VARCHAR(10) NOT NULL, reasons VARCHAR(500) NOT NULL)"

echo "Seeding $ROWS rows..."
q "SET SESSION cte_max_recursion_depth = 2000000;
   INSERT INTO transactions (txn_id, account_id, amount, merchant_category, country, device_id, created_at, score, decision, reasons)
   WITH RECURSIVE seq(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < $ROWS)
   SELECT CONCAT('t-', n), CONCAT('acct-', LPAD(n % 5000, 4, '0')), ROUND(5 + (n * 7919 % 20000) / 100, 2),
          'groceries', 'CA', CONCAT('dev-', n % 5000, '-', n % 3),
          TIMESTAMP('2026-09-01') + INTERVAL (n * 37) % (60 * 24 * 60) MINUTE, 0, 'ALLOW', ''
   FROM seq;
   ANALYZE TABLE transactions;"

VELOCITY="SELECT COUNT(*) FROM transactions WHERE account_id = 'acct-0042'
          AND created_at >= '2026-10-01 11:50:00' AND created_at < '2026-10-01 12:00:00'"
AVERAGE="SELECT AVG(amount) FROM transactions WHERE account_id = 'acct-0042'
          AND created_at >= '2026-09-01 12:00:00' AND created_at < '2026-10-01 12:00:00'"
DEVICE="SELECT 1 FROM transactions WHERE account_id = 'acct-0042' AND device_id = 'dev-42-1'
          AND created_at < '2026-10-01 12:00:00' LIMIT 1"

time_ms() { # average server-side milliseconds (EXPLAIN ANALYZE actual time) for the 3 history queries
  local total=0 t
  for _ in $(seq "$RUNS"); do
    for query in "$VELOCITY" "$AVERAGE" "$DEVICE"; do
      t=$(q "EXPLAIN ANALYZE $query" | grep -oE 'actual time=[0-9.]+\.\.[0-9.]+' | head -1 | sed -E 's/.*\.\.//')
      total=$(echo "$total + ${t:-0}" | bc)
    done
  done
  echo "scale=3; $total / $RUNS" | bc
}
plan() { q "EXPLAIN FORMAT=TREE $VELOCITY" | head -3 | tr '\n' ' '; }
examined() { q "EXPLAIN ANALYZE $VELOCITY" | grep -oE 'rows=[0-9]+' | tail -1; }

BEFORE_PLAN=$(plan); BEFORE_ROWS=$(examined); BEFORE_MS=$(time_ms)
q "CREATE INDEX idx_txn_account_time ON transactions (account_id, created_at);
   CREATE INDEX idx_txn_account_device ON transactions (account_id, device_id, created_at);
   ANALYZE TABLE transactions;"
AFTER_PLAN=$(plan); AFTER_ROWS=$(examined); AFTER_MS=$(time_ms)
SPEEDUP=$(echo "scale=1; $BEFORE_MS / $AFTER_MS" | bc)

REPORT=$(cat <<MD
## SQL optimization: history lookups on $ROWS transactions

| | Without composite indexes | With composite indexes |
|---|---|---|
| Velocity query plan | \`$BEFORE_PLAN\` | \`$AFTER_PLAN\` |
| Rows read by the scan step (EXPLAIN ANALYZE) | $BEFORE_ROWS | $AFTER_ROWS |
| Avg server time for the 3 history queries ($RUNS runs) | $BEFORE_MS ms | $AFTER_MS ms |

Speed-up: **${SPEEDUP}x**
MD
)
echo "$REPORT"
[ -n "${GITHUB_STEP_SUMMARY:-}" ] && echo "$REPORT" >> "$GITHUB_STEP_SUMMARY"
echo "$REPORT" > sql-benchmark.md

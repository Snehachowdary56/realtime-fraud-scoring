# Real-time Fraud Scoring Service

![CI](https://github.com/Snehachowdary56/realtime-fraud-scoring/actions/workflows/ci.yml/badge.svg)

A Java / Spring Boot backend that scores card transactions for fraud **in real time**. Transactions arrive on a **Kafka** topic (or a REST call), the service looks up the account's recent history in **MySQL**, runs a set of fraud rules, and publishes an `ALLOW`, `REVIEW` or `BLOCK` decision, with reasons, to a second Kafka topic.

```
 transactions ──► Kafka "transactions" ──► Spring Boot scoring service ──► Kafka "fraud-decisions"
 (keyed by account,                         │  1. history lookups (MySQL, indexed)
  so each account stays in order)           │  2. 6 fraud and AML rules (strategy pattern)
                                            │  3. score 0-100 ──► ALLOW / REVIEW / BLOCK
 REST POST /api/transactions/score ─────────┘  4. save decision + reasons
```

## Fraud rules

| Rule | Fires when | Points |
|---|---|---|
| `HIGH_AMOUNT` | amount ≥ $500 and more than 5× the account's 30-day average (or ≥ $3,000 on a new account) | 40 (30) |
| `VELOCITY` | 4+ transactions in the previous 10 minutes (card testing) | 40 |
| `IMPOSSIBLE_TRAVEL` | a different country less than 1 hour after the last transaction | 45 |
| `NEW_DEVICE` | a device the account has never used | 20 |
| `HIGH_RISK_MERCHANT` | gift cards, crypto or wire transfers | 15 |
| `AML_STRUCTURING` | anti-money laundering: the 3rd transaction of $9,000–$9,999.99 within 24 hours (splitting money to stay under the $10,000 reporting limit) | 50 |

Score = sum of points (capped at 100). **40+ → REVIEW, 70+ → BLOCK**. Thresholds are in `application.yml`. Each rule is one class implementing `FraudRule`, so adding a signal means adding one class.

Rules use the transaction's **event time**, not the clock, so a replayed stream gets the same decisions.

## SQL optimization

Every rule needs the account's recent history: "how many transactions in the last 10 minutes", "average amount over 30 days", "has this device been seen". Those queries all filter by `account_id` and a time range, so the schema adds two **composite indexes**: `(account_id, created_at)` and `(account_id, device_id, created_at)`.

`scripts/sql_benchmark.sh` seeds 500,000 rows and compares the queries with and without those indexes. Result from the **sql-optimization** job in GitHub Actions (MySQL 8.0):

| | Without composite indexes | With composite indexes |
|---|---|---|
| Velocity query plan | Table scan, **500,000 rows read** | Covering index range scan on `idx_txn_account_time` |
| Server time for the 3 history queries (avg of 20 runs, `EXPLAIN ANALYZE`) | 279 ms | **0.26 ms** |

At 279 ms per event, real-time scoring would not be possible; with the indexes, the history lookups cost almost nothing.

## Proof it works (GitHub Actions)

| Job | What it checks |
|---|---|
| `unit-tests` | 22 JUnit 5 tests for every rule, the scoring service (Mockito) and the REST controller (MockMvc, validation errors); Docker image build |
| `integration-mysql-kafka` | Starts real **MySQL 8** and **Kafka** containers, then: REST scoring with history from MySQL, duplicate rejection, and an **end-to-end run**: 648 labelled transactions streamed through Kafka with planted fraud (card testing, unusual amounts, impossible travel, account takeover, AML structuring), then the Spark, Isolation Forest and ClickHouse analytics. It reports recall, false positive rate and latency, and fails if recall drops below 95% or false positives go above 3% |
| `sql-optimization` | The index benchmark above |

Results from the end-to-end run (`integration-mysql-kafka` job):

| Metric | Result |
|---|---|
| Live transactions streamed through Kafka | 648 (on top of 7,225 history rows in MySQL) |
| Planted fraud caught | **24 / 24** (card testing 8/8, unusual amount 4/4, impossible travel 4/4, account takeover 4/4, AML structuring 4/4) |
| Normal transactions wrongly flagged | **0 / 600** |
| Throughput, all 648 sent at once, one consumer | about 100 transactions/second (96 to 123 across runs) |
| Latency from Kafka in to decision out, one at a time | **11 to 14 ms median, under 20 ms p95** |

Honest caveat: the fraud is synthetic and planted to match the rules, so 100% recall shows the pipeline works end to end, not how it would do on real fraud. Results appear on each run's summary page.

## Analytics: Spark, unsupervised ML and ClickHouse

After the end-to-end run, the same CI job runs the `analytics/` pipeline:

1. **Spark (PySpark)** reads every scored transaction from MySQL over JDBC (4 partitions) and builds behaviour features per account with window functions: amount spread, devices, countries, shortest gap, 10-minute bursts, country jumps within an hour, high-risk share, and transfers just under $10,000.
2. **Unsupervised anomaly detection:** an **Isolation Forest** (scikit-learn) ranks accounts by how unusual they are, **without using any fraud labels**. The planted fraud accounts are used only afterwards, to score it.
3. **Kafka → ClickHouse:** a ClickHouse **Kafka table engine** plus a materialized view streams every decision from the `fraud-decisions` topic into a MergeTree table, which then answers analytical queries (decisions by type, which rules fire most, riskiest accounts).

| Step | Result (latest CI run) |
|---|---|
| Spark job | 7,907 transactions, 108 accounts, 6 s |
| Isolation Forest | all **20 of 20** planted-fraud accounts ranked in its top 20 (ROC-AUC 1.0 over 100 accounts) |
| ClickHouse | 698 decisions streamed from Kafka; analytical queries in about 4 to 5 ms |
| Rules fired most (ClickHouse query) | HIGH_AMOUNT 20, HIGH_RISK_MERCHANT 16, VELOCITY 8, AML_STRUCTURING 4, NEW_DEVICE 4, IMPOSSIBLE_TRAVEL 4 |

Caveat: the planted fraud is deliberately obvious, so a perfect score shows the pipeline works, not real-world accuracy.

## Run it

Needs Docker:

```bash
docker compose up --build        # MySQL + Kafka + the service on :8080
./scripts/demo.sh                # 6 normal purchases, then an account takeover → BLOCK
```

API:

```bash
curl -X POST localhost:8080/api/transactions/score -H 'Content-Type: application/json' -d '{
  "txnId":"t-1","accountId":"acct-1","amount":120.50,"merchantCategory":"groceries",
  "country":"CA","deviceId":"phone-1","timestamp":"2026-11-01T12:00:00Z"}'
# {"txnId":"t-1","accountId":"acct-1","score":0,"decision":"ALLOW","reasons":[]}

curl localhost:8080/api/accounts/acct-1/transactions   # latest decisions for investigators
curl localhost:8080/actuator/health
```

Tests: `mvn verify` (unit tests) · `mvn -Pintegration test` (needs MySQL and Kafka, e.g. from `docker compose up mysql kafka`).

## Tech

Java 21 · Spring Boot 3.3 (Web, Data JPA, Validation, Actuator) · Spring for Apache Kafka · MySQL 8 · Flyway · JUnit 5 · Mockito · MockMvc · Apache Spark (PySpark) · scikit-learn · ClickHouse · Docker · GitHub Actions · Bash · Python

## Limits and next steps

- The data is synthetic, and the rules are hand-set. A real system would feed the Isolation Forest score (or a supervised model) back in as one more rule; see my [fraud detection model project](https://github.com/Snehachowdary56/fraud-drift-detection-ai-project) (XGBoost, ROC-AUC 0.977).
- History is read from MySQL on each event. At large scale, the counters would move to a stream processor (Kafka Streams or Flink) or a cache.
- Single-node Kafka and MySQL; no multi-region setup.

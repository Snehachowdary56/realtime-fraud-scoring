-- Real-time workflow: ClickHouse reads fraud decisions straight from Kafka (Kafka table engine);
-- a materialized view copies each message into a MergeTree table for fast analytical queries.
CREATE TABLE IF NOT EXISTS decisions_queue (
    txnId String, accountId String, score UInt8, decision LowCardinality(String), reasons Array(String)
) ENGINE = Kafka
SETTINGS kafka_broker_list = 'kafka:29092', kafka_topic_list = 'fraud-decisions',
         kafka_group_name = 'clickhouse-analytics', kafka_format = 'JSONEachRow', kafka_num_consumers = 1;

CREATE TABLE IF NOT EXISTS fraud_decisions (
    txnId String, accountId String, score UInt8, decision LowCardinality(String), reasons Array(String),
    ingested_at DateTime64(3) DEFAULT now64(3)
) ENGINE = MergeTree ORDER BY (decision, accountId, txnId);

CREATE MATERIALIZED VIEW IF NOT EXISTS decisions_mv TO fraud_decisions AS
SELECT txnId, accountId, score, decision, reasons FROM decisions_queue;

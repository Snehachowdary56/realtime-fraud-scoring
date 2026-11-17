CREATE TABLE transactions (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  txn_id            VARCHAR(64)    NOT NULL,
  account_id        VARCHAR(64)    NOT NULL,
  amount            DECIMAL(12, 2) NOT NULL,
  merchant_category VARCHAR(32)    NOT NULL,
  country           CHAR(2)        NOT NULL,
  device_id         VARCHAR(64)    NOT NULL,
  created_at        DATETIME(6)    NOT NULL,
  score             INT            NOT NULL,
  decision          VARCHAR(10)    NOT NULL,
  reasons           VARCHAR(500)   NOT NULL,
  CONSTRAINT uq_transactions_txn_id UNIQUE (txn_id)
);

-- Every rule looks up one account's recent history, so the hot queries filter by
-- account_id and a time range (or account_id and device_id). These composite indexes turn
-- those lookups into index range scans instead of full table scans
-- (see scripts/sql_benchmark.sh for the before/after EXPLAIN ANALYZE).
CREATE INDEX idx_txn_account_time   ON transactions (account_id, created_at);
CREATE INDEX idx_txn_account_device ON transactions (account_id, device_id, created_at);

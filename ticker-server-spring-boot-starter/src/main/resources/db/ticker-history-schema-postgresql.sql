-- Ticker metric history schema — PostgreSQL — run once if the app DB user lacks CREATE (else auto-created).
-- Credentials via env (guardrail #5).
CREATE TABLE IF NOT EXISTS metric_sample (
    target_id    VARCHAR(256)     NOT NULL,
    metric_key   VARCHAR(128)     NOT NULL,
    ts_millis    BIGINT           NOT NULL,
    metric_value DOUBLE PRECISION NOT NULL,
    PRIMARY KEY (target_id, metric_key, ts_millis)
);
-- Speeds the hourly retention prune + archival scan (WHERE ts_millis < ?). The PK's leftmost columns
-- are target_id/metric_key, so a time-only range needs its own index.
CREATE INDEX IF NOT EXISTS idx_metric_sample_ts ON metric_sample (ts_millis);

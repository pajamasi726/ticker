-- Ticker metric history schema — MySQL — run once if the app DB user lacks CREATE (else auto-created).
-- Credentials via env (guardrail #5).
CREATE TABLE IF NOT EXISTS metric_sample (
    target_id    VARCHAR(256) NOT NULL,
    metric_key   VARCHAR(128) NOT NULL,
    ts_millis    BIGINT       NOT NULL,
    metric_value DOUBLE       NOT NULL,
    PRIMARY KEY (target_id, metric_key, ts_millis),
    -- Speeds the hourly retention prune + archival scan (WHERE ts_millis < ?). Inline because MySQL
    -- has no CREATE INDEX IF NOT EXISTS. Upgrading an EXISTING table (created by 0.1.x)? Run once:
    --   ALTER TABLE metric_sample ADD INDEX idx_metric_sample_ts (ts_millis);
    INDEX idx_metric_sample_ts (ts_millis)
);

-- Ticker metric history schema — MySQL — run once if the app DB user lacks CREATE (else auto-created).
-- Credentials via env (guardrail #5).
CREATE TABLE IF NOT EXISTS metric_sample (
    target_id    VARCHAR(128) NOT NULL,
    metric_key   VARCHAR(128) NOT NULL,
    ts_millis    BIGINT       NOT NULL,
    metric_value DOUBLE       NOT NULL,
    PRIMARY KEY (target_id, metric_key, ts_millis)
);

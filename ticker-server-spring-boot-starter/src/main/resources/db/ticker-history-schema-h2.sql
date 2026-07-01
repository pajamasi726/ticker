-- Ticker metric history schema — H2 (auto-created at startup when ticker.history.enabled=true, db=H2).
-- Column is metric_value (VALUE is reserved in H2 2.x); portable, no quoting needed.
CREATE TABLE IF NOT EXISTS metric_sample (
    target_id    VARCHAR(128)     NOT NULL,
    metric_key   VARCHAR(128)     NOT NULL,
    ts_millis    BIGINT           NOT NULL,
    metric_value DOUBLE PRECISION NOT NULL,
    PRIMARY KEY (target_id, metric_key, ts_millis)
);

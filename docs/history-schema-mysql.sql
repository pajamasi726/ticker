-- Ticker metric_sample table — MySQL DDL
--
-- H2 (default): auto-created by HistoryAutoConfiguration at startup when ticker.history.enabled=true.
-- MySQL: run this DDL once before starting the collector. Supply credentials via environment variables
--   — NEVER commit credentials to source control (guardrail #5).
--
-- Example env-var wiring in application.yml (credentials come from env):
--   ticker:
--     history:
--       url: jdbc:mysql://db-host:3306/ticker
--       username: ${TICKER_DB_USER}
--       password: ${TICKER_DB_PASS}
--
-- Note: column is named `metric_value` (not `value`) because VALUE is a reserved word in H2 2.x.
-- This naming is portable across H2 and MySQL without quoting.

CREATE TABLE IF NOT EXISTS metric_sample (
    target_id    VARCHAR(128) NOT NULL,
    metric_key   VARCHAR(128) NOT NULL,
    ts_millis    BIGINT       NOT NULL,
    metric_value DOUBLE       NOT NULL,
    PRIMARY KEY (target_id, metric_key, ts_millis)
);

-- The PRIMARY KEY (target_id, metric_key, ts_millis) covers the range query and acts as the InnoDB
-- clustered index. No extra index is needed for the downsampled range query pattern.

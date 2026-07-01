package io.stevelabs.ticker.server.history

import org.springframework.jdbc.core.JdbcTemplate

class MetricHistoryRepository(private val jdbc: JdbcTemplate) {

    fun ensureSchema() {
        jdbc.execute(
            """
            CREATE TABLE IF NOT EXISTS metric_sample (
                target_id    VARCHAR(128) NOT NULL,
                metric_key   VARCHAR(128) NOT NULL,
                ts_millis    BIGINT       NOT NULL,
                metric_value DOUBLE       NOT NULL,
                PRIMARY KEY (target_id, metric_key, ts_millis)
            )
            """.trimIndent(),
        )
    }

    fun saveAll(targetId: String, samples: List<Pair<String, Double>>, tsMillis: Long) {
        if (samples.isEmpty()) return
        jdbc.batchUpdate(
            "INSERT INTO metric_sample (target_id, metric_key, ts_millis, metric_value) VALUES (?, ?, ?, ?)",
            samples.map { (key, v) -> arrayOf<Any>(targetId, key, tsMillis, v) },
        )
    }

    data class HistoryPoint(val t: Long, val v: Double)

    /**
     * Downsampled range query: groups rows into fixed-width time buckets and returns the average
     * per bucket. The bucket number b = (ts_millis - fromMillis) / bucketMs (integer division).
     * Params order: fromMillis, bucketMs, targetId, metricKey, fromMillis.
     */
    fun query(targetId: String, metricKey: String, fromMillis: Long, bucketMs: Long): List<HistoryPoint> =
        jdbc.query(
            """
            SELECT ((ts_millis - ?) / ?) AS b, AVG(metric_value) AS v
            FROM metric_sample
            WHERE target_id = ? AND metric_key = ? AND ts_millis >= ?
            GROUP BY b
            ORDER BY b
            """.trimIndent(),
            { rs, _ -> HistoryPoint(t = fromMillis + rs.getLong("b") * bucketMs, v = rs.getDouble("v")) },
            fromMillis, bucketMs, targetId, metricKey, fromMillis,
        )

    fun prune(beforeMillis: Long): Int =
        jdbc.update("DELETE FROM metric_sample WHERE ts_millis < ?", beforeMillis)
}

package io.stevelabs.ticker.server.history

import org.springframework.jdbc.core.JdbcTemplate

data class MetricSampleRow(
    val targetId: String,
    val metricKey: String,
    val tsMillis: Long,
    val value: Double,
)

class MetricHistoryRepository(private val jdbc: JdbcTemplate) {

    fun ensureSchema(db: HistoryDb) {
        val resource = "/db/ticker-history-schema-${db.name.lowercase()}.sql"
        val sql = MetricHistoryRepository::class.java.getResource(resource)?.readText()
            ?: throw IllegalStateException("Missing bundled schema DDL for $db (expected classpath:$resource)")
        // Run each statement separately: JdbcTemplate.execute takes a single statement and most JDBC
        // drivers reject multi-statement strings — so strip `--` line comments and split on ';'.
        // Constraint on the BUNDLED DDLs this parses: keep ';' out of anything that isn't a full-line
        // `--` comment (no trailing same-line comments with ';', no ';' inside string literals).
        sql.lineSequence()
            .filterNot { it.trim().startsWith("--") }
            .joinToString("\n")
            .split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { jdbc.execute(it) }
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

    /** Rows older than [beforeMillis] (the ones the prune is about to delete). Ordered by ts. */
    fun selectBefore(beforeMillis: Long): List<MetricSampleRow> =
        jdbc.query(
            "SELECT target_id, metric_key, ts_millis, metric_value FROM metric_sample WHERE ts_millis < ? ORDER BY ts_millis",
            { rs, _ ->
                MetricSampleRow(rs.getString(1), rs.getString(2), rs.getLong(3), rs.getDouble(4))
            },
            beforeMillis,
        )

    fun prune(beforeMillis: Long): Int =
        jdbc.update("DELETE FROM metric_sample WHERE ts_millis < ?", beforeMillis)
}

package io.stevelabs.ticker.server.history

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Writes batches of [MetricSampleRow]s to gzip-CSV archive files and verifies them.
 *
 * Metric keys and target IDs are restricted to `[A-Za-z0-9._:-]` (no commas), so plain
 * CSV without quoting is safe.
 */
open class HistoryArchiver(private val dir: Path) {

    /**
     * Write [rows] as gzip CSV to `dir/metric_sample-<stampMillis>.csv.gz`
     * (no header; columns: target_id, metric_key, ts_millis, metric_value).
     * Creates [dir] if it does not exist. Returns the path to the written file.
     */
    open fun archive(rows: List<MetricSampleRow>, stampMillis: Long): Path {
        Files.createDirectories(dir)
        val file = dir.resolve("metric_sample-$stampMillis.csv.gz")
        GZIPOutputStream(Files.newOutputStream(file)).bufferedWriter().use { writer ->
            rows.forEach { row ->
                writer.write("${row.targetId},${row.metricKey},${row.tsMillis},${row.value}")
                writer.newLine()
            }
        }
        return file
    }

    /**
     * Re-read the gzip CSV at [file] and confirm that its line count equals [expectedRows].
     * Returns `false` on any count mismatch or I/O error — the caller must NOT prune.
     */
    open fun verify(file: Path, expectedRows: Int): Boolean =
        try {
            val count = GZIPInputStream(Files.newInputStream(file)).bufferedReader()
                .useLines { lines -> lines.count() }
            count == expectedRows
        } catch (_: Exception) {
            false
        }
}

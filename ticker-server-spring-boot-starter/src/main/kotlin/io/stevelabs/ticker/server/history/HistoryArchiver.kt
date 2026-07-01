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

    /**
     * Rolling cap (Logback-style): delete archive files older than [maxAgeMillis]; then, if
     * [maxTotalBytes] > 0, delete oldest-first until the archive dir is under the size cap.
     * Best-effort, never throws, and only touches our own `metric_sample-<stamp>.csv.gz` files.
     */
    open fun enforceRetention(maxAgeMillis: Long, maxTotalBytes: Long, now: Long) {
        val cutoff = now - maxAgeMillis
        val survivors = ArrayList<Path>()
        for ((file, stamp) in listArchives()) {          // oldest-first
            if (stamp < cutoff) tryDelete(file) else survivors.add(file)
        }
        if (maxTotalBytes > 0) {
            var total = survivors.sumOf { sizeOf(it) }
            val iter = survivors.iterator()               // oldest-first eviction
            while (total > maxTotalBytes && iter.hasNext()) {
                val f = iter.next()
                val sz = sizeOf(f)
                if (tryDelete(f)) total -= sz
            }
        }
    }

    private fun listArchives(): List<Pair<Path, Long>> {
        if (!Files.isDirectory(dir)) return emptyList()
        return try {
            Files.newDirectoryStream(dir, "metric_sample-*.csv.gz").use { ds ->
                ds.mapNotNull { p ->
                    FILE_RE.matchEntire(p.fileName.toString())?.groupValues?.get(1)?.toLongOrNull()?.let { p to it }
                }
            }.sortedBy { it.second }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun sizeOf(p: Path): Long = try { Files.size(p) } catch (_: Exception) { 0L }
    private fun tryDelete(p: Path): Boolean = try { Files.deleteIfExists(p) } catch (_: Exception) { false }

    private companion object {
        val FILE_RE = Regex("""metric_sample-(\d+)\.csv\.gz""")
    }
}

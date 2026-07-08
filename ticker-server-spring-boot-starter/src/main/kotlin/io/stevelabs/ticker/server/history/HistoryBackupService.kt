package io.stevelabs.ticker.server.history

import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean

/** Result of one online backup. */
data class BackupResult(val file: String, val bytes: Long, val tookMs: Long)

/** A backup zip on disk. */
data class BackupFile(val name: String, val bytes: Long, val createdAtMillis: Long)

class BackupInProgressException : RuntimeException("a backup is already running")
class UnsupportedBackupDbException(db: HistoryDb) :
    RuntimeException("online backup is H2-only; for $db use its native dump tool (mysqldump --single-transaction / pg_dump)")

/**
 * Zero-downtime H2 backup: executes H2's built-in `BACKUP TO 'file.zip'` through the history
 * JdbcTemplate. The embedded H2 file is exclusively locked by this JVM, so the app itself is the
 * only thing that CAN take a consistent hot snapshot — recording keeps running meanwhile.
 * One backup at a time (the guard returns 409 upstream); rolling caps are OFF by default so a
 * manual backup is never silently deleted.
 */
class HistoryBackupService(
    private val jdbc: JdbcTemplate,
    private val props: HistoryProperties,
) {
    private val log = LoggerFactory.getLogger(HistoryBackupService::class.java)
    private val running = AtomicBoolean(false)
    private val dir: Path get() = Path.of(props.backup.dir)

    fun backupNow(nowMillis: Long = System.currentTimeMillis()): BackupResult {
        if (props.db != HistoryDb.H2) throw UnsupportedBackupDbException(props.db)
        if (!running.compareAndSet(false, true)) throw BackupInProgressException()
        try {
            Files.createDirectories(dir)
            val name = "ticker-history-${STAMP.format(Instant.ofEpochMilli(nowMillis))}.zip"
            val file = dir.resolve(name)
            val started = System.nanoTime()
            // H2 rejects parameters for BACKUP, so the path is inlined — it comes from OUR property
            // (never a request), and single quotes are doubled defensively.
            jdbc.execute("BACKUP TO '${file.toAbsolutePath().toString().replace("'", "''")}'")
            val tookMs = (System.nanoTime() - started) / 1_000_000
            val bytes = Files.size(file)
            log.info("History backup written: {} ({} bytes, {} ms)", file, bytes, tookMs)
            enforceCaps(nowMillis)
            return BackupResult(file.toString(), bytes, tookMs)
        } finally {
            running.set(false)
        }
    }

    fun list(): List<BackupFile> =
        listZips().map { BackupFile(it.fileName.toString(), sizeOf(it), createdAt(it)) }
            .sortedByDescending { it.createdAtMillis }

    /**
     * Resolve a DOWNLOADABLE backup by name. The name must match the exact pattern our backups use —
     * a whitelist, so no path traversal, no reading arbitrary files (guardrail #5).
     */
    fun resolve(name: String): Path? {
        if (!NAME_RE.matches(name)) return null
        val file = dir.resolve(name)
        return if (Files.isRegularFile(file)) file else null
    }

    /** Rolling caps, same semantics as the archiver — but both default OFF for backups. */
    private fun enforceCaps(now: Long) {
        val maxAge = props.backup.fileRetention.toMillis()
        val maxBytes = props.backup.maxTotalSizeMb * 1024 * 1024
        if (maxAge <= 0 && maxBytes <= 0) return
        val survivors = ArrayList<Path>()
        for (file in listZips()) { // oldest-first
            if (maxAge > 0 && createdAt(file) < now - maxAge) tryDelete(file) else survivors.add(file)
        }
        if (maxBytes > 0) {
            var total = survivors.sumOf { sizeOf(it) }
            val iter = survivors.iterator()
            while (total > maxBytes && iter.hasNext()) {
                val f = iter.next()
                val sz = sizeOf(f)
                if (tryDelete(f)) total -= sz
            }
        }
    }

    private fun listZips(): List<Path> {
        if (!Files.isDirectory(dir)) return emptyList()
        return try {
            Files.newDirectoryStream(dir, "ticker-history-*.zip").use { ds ->
                ds.filter { NAME_RE.matches(it.fileName.toString()) }.sortedBy { it.fileName.toString() }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun createdAt(file: Path): Long = try {
        Files.getLastModifiedTime(file).toMillis()
    } catch (_: Exception) {
        0L
    }

    private fun sizeOf(file: Path): Long = try {
        Files.size(file)
    } catch (_: Exception) {
        0L
    }

    private fun tryDelete(file: Path): Boolean = try {
        Files.deleteIfExists(file)
    } catch (e: Exception) {
        log.warn("Could not delete old backup {}: {}", file, e.message)
        false
    }

    companion object {
        private val STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault())
        val NAME_RE = Regex("""ticker-history-\d{8}-\d{6}\.zip""")
    }
}

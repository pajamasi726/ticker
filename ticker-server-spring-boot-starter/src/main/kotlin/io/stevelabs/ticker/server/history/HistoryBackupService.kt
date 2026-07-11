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

/** Result of restoring a backup INTO the live table. */
data class RestoreResult(val rows: Int, val tookMs: Long)

class BackupInProgressException : RuntimeException("a backup is already running")
class UnsupportedBackupDbException(db: HistoryDb) :
    RuntimeException("online backup is H2-only; for $db use its native dump tool (mysqldump --single-transaction / pg_dump)")
class RestoreFailedException(message: String) : RuntimeException(message)

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

    /** Explicit dir wins; default = `backups/` next to the H2 file (same volume as the data). */
    private val dir: Path get() = props.backup.dir?.let { Path.of(it) }
        ?: (Path.of(props.h2Path).toAbsolutePath().parent ?: Path.of(".")).resolve("backups")

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

    /** Delete one backup zip by (whitelisted) name. Returns false when it doesn't exist. */
    fun delete(name: String): Boolean {
        val file = resolve(name) ?: return false
        return tryDelete(file)
    }

    /**
     * Restore a listed backup INTO the live table — REPLACE semantics, zero downtime: the zip's
     * `.mv.db` is extracted to a temp dir and opened as a SECOND embedded H2 (the live file is
     * never swapped, so no restart), its rows streamed across, the live table truncated once the
     * snapshot opens cleanly, then batch-inserted. Serialized with backups via the same guard.
     */
    fun restoreFrom(name: String): RestoreResult {
        if (props.db != HistoryDb.H2) throw UnsupportedBackupDbException(props.db)
        val file = resolve(name) ?: throw RestoreFailedException("no such backup: $name")
        if (!running.compareAndSet(false, true)) throw BackupInProgressException()
        val started = System.nanoTime()
        val tmp = Files.createTempDirectory("ticker-restore")
        try {
            val dbBase = extractSnapshot(file, tmp)
            val snapJdbc = JdbcTemplate(
                org.springframework.jdbc.datasource.DriverManagerDataSource(
                    "jdbc:h2:file:$dbBase;IFEXISTS=TRUE", props.username ?: "", props.password ?: "",
                ),
            )
            try {
                var batch = ArrayList<Array<Any>>(BATCH)
                var total = 0
                var truncated = false
                snapJdbc.query("SELECT target_id, metric_key, ts_millis, metric_value FROM metric_sample") { rs ->
                    if (!truncated) { // snapshot opened + readable — only now is it safe to clear live data
                        jdbc.execute("TRUNCATE TABLE metric_sample")
                        truncated = true
                    }
                    batch.add(arrayOf(rs.getString(1), rs.getString(2), rs.getLong(3), rs.getDouble(4)))
                    if (batch.size >= BATCH) {
                        insert(batch)
                        total += batch.size
                        batch = ArrayList(BATCH)
                    }
                }
                if (!truncated) { // an empty (but valid) snapshot still means "replace with empty"
                    jdbc.execute("TRUNCATE TABLE metric_sample")
                }
                if (batch.isNotEmpty()) {
                    insert(batch)
                    total += batch.size
                }
                val tookMs = (System.nanoTime() - started) / 1_000_000
                log.info("Restored {} metric_sample rows from {} ({} ms)", total, name, tookMs)
                return RestoreResult(total, tookMs)
            } finally {
                try { snapJdbc.execute("SHUTDOWN") } catch (_: Exception) { /* best effort */ }
            }
        } catch (e: RestoreFailedException) {
            throw e
        } catch (e: Exception) {
            throw RestoreFailedException(e.message ?: e.javaClass.simpleName)
        } finally {
            running.set(false)
            deleteRecursively(tmp)
        }
    }

    /**
     * Accept an EXTERNAL backup zip (e.g. carried over from another server) into the backup dir
     * under a fresh whitelisted name — after verifying it really is a zip with one `.mv.db` inside.
     */
    fun upload(body: java.io.InputStream, nowMillis: Long = System.currentTimeMillis()): BackupFile {
        Files.createDirectories(dir)
        val tmp = Files.createTempFile(dir, ".upload-", ".part")
        try {
            var copied = 0L
            body.use { input ->
                Files.newOutputStream(tmp).use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        copied += n
                        if (copied > MAX_UPLOAD_BYTES) throw RestoreFailedException("upload exceeds ${MAX_UPLOAD_BYTES / 1024 / 1024}MB")
                        out.write(buf, 0, n)
                    }
                }
            }
            java.util.zip.ZipFile(tmp.toFile()).use { zip ->
                val dbEntries = zip.entries().asSequence().filter { !it.isDirectory && it.name.endsWith(".mv.db") }.count()
                if (dbEntries != 1) throw RestoreFailedException("not a Ticker backup: expected exactly one .mv.db entry, found $dbEntries")
            }
            val name = "ticker-history-${STAMP.format(Instant.ofEpochMilli(nowMillis))}.zip"
            val target = dir.resolve(name)
            Files.move(tmp, target)
            log.info("Backup uploaded: {} ({} bytes)", target, copied)
            return BackupFile(name, copied, nowMillis)
        } catch (e: RestoreFailedException) {
            throw e
        } catch (e: Exception) {
            throw RestoreFailedException(e.message ?: e.javaClass.simpleName)
        } finally {
            Files.deleteIfExists(tmp)
        }
    }

    /** Extract the single .mv.db entry (basename only — zip paths are untrusted) and return the H2 base path. */
    private fun extractSnapshot(file: Path, tmp: Path): Path {
        java.util.zip.ZipFile(file.toFile()).use { zip ->
            val entry = zip.entries().asSequence().firstOrNull { !it.isDirectory && it.name.endsWith(".mv.db") }
                ?: throw RestoreFailedException("backup zip contains no .mv.db entry")
            val base = Path.of(entry.name).fileName.toString()
            val target = tmp.resolve(base)
            zip.getInputStream(entry).use { Files.copy(it, target) }
            return tmp.resolve(base.removeSuffix(".mv.db"))
        }
    }

    private fun insert(batch: List<Array<Any>>) {
        jdbc.batchUpdate("INSERT INTO metric_sample (target_id, metric_key, ts_millis, metric_value) VALUES (?, ?, ?, ?)", batch)
    }

    private fun deleteRecursively(dir: Path) {
        try {
            Files.walk(dir).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        } catch (_: Exception) { /* temp cleanup is best effort */ }
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
        private const val BATCH = 1_000
        private const val MAX_UPLOAD_BYTES = 1024L * 1024 * 1024 // 1GB — far above any sane history zip
        private val STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault())
        val NAME_RE = Regex("""ticker-history-\d{8}-\d{6}\.zip""")
    }
}

package io.stevelabs.ticker.server.history

import io.stevelabs.ticker.server.ApiError
import org.springframework.core.io.FileSystemResource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.nio.file.Files
import java.nio.file.Path

/**
 * Storage ops for the admin page: history stats + online H2 backups.
 *
 * Registered whenever the collector is on — NOT gated by `ticker.history.enabled` — so the UI can
 * poll it without 404s and render a friendly "history is off" state instead (the 0.2.1 lesson:
 * an endpoint the UI polls must answer even when its feature is disabled). The history beans are
 * optional constructor args for the same reason.
 */
@RestController
@RequestMapping("/api/history")
class HistoryOpsController(
    private val props: HistoryProperties,
    private val repository: MetricHistoryRepository?,
    private val backupService: HistoryBackupService?,
) {

    data class ArchiveStats(val enabled: Boolean, val fileCount: Int, val totalBytes: Long)
    data class HistoryStats(
        val enabled: Boolean,
        val db: HistoryDb? = null,
        val rowCount: Long? = null,
        val oldestTsMillis: Long? = null,
        val newestTsMillis: Long? = null,
        val h2FileBytes: Long? = null,
        val retentionMillis: Long? = null,
        val sampleIntervalMillis: Long? = null,
        val archive: ArchiveStats? = null,
        val backupSupported: Boolean = false,
    )

    @GetMapping("/stats")
    fun stats(): HistoryStats {
        if (!props.enabled || repository == null) return HistoryStats(enabled = false)
        val table = try {
            repository.stats()
        } catch (_: Exception) {
            MetricHistoryRepository.TableStats(0, null, null) // e.g. table dropped — stay friendly
        }
        return HistoryStats(
            enabled = true,
            db = props.db,
            rowCount = table.rowCount,
            oldestTsMillis = table.oldestTsMillis,
            newestTsMillis = table.newestTsMillis,
            h2FileBytes = if (props.db == HistoryDb.H2) sizeOrNull(Path.of(props.h2Path + ".mv.db")) else null,
            retentionMillis = props.retention.toMillis(),
            sampleIntervalMillis = props.sampleInterval.toMillis(),
            archive = ArchiveStats(
                enabled = props.archive.enabled,
                fileCount = archiveFiles().size,
                totalBytes = archiveFiles().sumOf { sizeOrNull(it) ?: 0L },
            ),
            backupSupported = props.db == HistoryDb.H2,
        )
    }

    @PostMapping("/backup")
    fun backup(): ResponseEntity<Any> {
        if (!props.enabled || backupService == null) {
            return ResponseEntity.badRequest()
                .body(ApiError("HISTORY_DISABLED", "ticker.history.enabled=false — there is nothing to back up."))
        }
        return try {
            ResponseEntity.ok(backupService.backupNow())
        } catch (e: UnsupportedBackupDbException) {
            ResponseEntity.badRequest().body(ApiError("UNSUPPORTED_DB", e.message ?: "H2 only"))
        } catch (_: BackupInProgressException) {
            ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError("BACKUP_IN_PROGRESS", "A backup is already running; try again when it finishes."))
        }
    }

    @GetMapping("/backups")
    fun backups(): List<BackupFile> = backupService?.list() ?: emptyList()

    @GetMapping("/backups/{name}")
    fun download(@PathVariable name: String): ResponseEntity<Any> {
        val file = backupService?.resolve(name)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError("BACKUP_NOT_FOUND", "No such backup file."))
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${file.fileName}\"")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(FileSystemResource(file))
    }

    private fun archiveFiles(): List<Path> {
        val dir = Path.of(props.archive.dir)
        if (!Files.isDirectory(dir)) return emptyList()
        return try {
            Files.newDirectoryStream(dir, "metric_sample-*.csv.gz").use { it.toList() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun sizeOrNull(p: Path): Long? = try {
        if (Files.isRegularFile(p)) Files.size(p) else null
    } catch (_: Exception) {
        null
    }
}

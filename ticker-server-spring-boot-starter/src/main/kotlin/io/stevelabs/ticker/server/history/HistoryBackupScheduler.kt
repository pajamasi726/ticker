package io.stevelabs.ticker.server.history

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled

/**
 * Optional automatic backups: fires on `ticker.history.backup.schedule` (Spring cron).
 * The `-` default disables the trigger entirely when the property is unset (manual-only).
 */
class HistoryBackupScheduler(private val service: HistoryBackupService) {
    private val log = LoggerFactory.getLogger(HistoryBackupScheduler::class.java)

    @Scheduled(cron = "\${ticker.history.backup.schedule:-}")
    fun scheduledBackup() {
        try {
            service.backupNow()
        } catch (e: BackupInProgressException) {
            log.warn("Scheduled backup skipped: {}", e.message)
        } catch (e: Exception) {
            // WARN, not silent: a broken schedule (full disk, bad dir) must be visible in the log.
            log.warn("Scheduled history backup failed: {}", e.message)
        }
    }
}

package io.stevelabs.ticker.server.history

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile

class HistoryBackupServiceTest {

    @TempDir
    lateinit var tmp: Path

    private lateinit var jdbc: JdbcTemplate

    private fun props(db: HistoryDb = HistoryDb.H2, backup: HistoryProperties.BackupProperties) =
        HistoryProperties(enabled = true, db = db, h2Path = tmp.resolve("hist").toString(), backup = backup)

    /** A REAL file-based H2 (not in-memory) — BACKUP TO snapshots the store file. */
    private fun openDb(): JdbcTemplate {
        val ds = DriverManagerDataSource("jdbc:h2:file:${tmp.resolve("hist")};DB_CLOSE_DELAY=-1", "sa", "")
        jdbc = JdbcTemplate(ds)
        MetricHistoryRepository(jdbc).ensureSchema(HistoryDb.H2)
        MetricHistoryRepository(jdbc).saveAll("t@h:1", listOf("cpu" to 0.5, "heap" to 0.3), 1_000L)
        return jdbc
    }

    @AfterEach
    fun shutdown() {
        if (this::jdbc.isInitialized) jdbc.execute("SHUTDOWN")
    }

    @Test fun `backupNow writes a consistent zip while the db is open`() {
        val service = HistoryBackupService(openDb(), props(backup = HistoryProperties.BackupProperties(dir = tmp.resolve("bk").toString())))
        val result = service.backupNow(nowMillis = 1_720_000_000_000)

        val file = Path.of(result.file)
        assertThat(Files.isRegularFile(file)).isTrue
        assertThat(result.bytes).isGreaterThan(0)
        assertThat(file.fileName.toString()).matches(HistoryBackupService.NAME_RE.pattern)
        ZipFile(file.toFile()).use { zip ->
            assertThat(zip.entries().asSequence().map { it.name }.toList())
                .anyMatch { it.endsWith(".mv.db") }
        }
        assertThat(service.list()).hasSize(1)
        assertThat(service.list().first().name).isEqualTo(file.fileName.toString())
    }

    @Test fun `non-H2 db is refused with the native-tool hint`() {
        val service = HistoryBackupService(openDb(), props(db = HistoryDb.MYSQL, backup = HistoryProperties.BackupProperties(dir = tmp.resolve("bk").toString())))
        assertThatThrownBy { service.backupNow() }
            .isInstanceOf(UnsupportedBackupDbException::class.java)
            .hasMessageContaining("mysqldump")
    }

    @Test fun `resolve only serves whitelisted backup names — no traversal`() {
        val dir = tmp.resolve("bk")
        val service = HistoryBackupService(openDb(), props(backup = HistoryProperties.BackupProperties(dir = dir.toString())))
        val created = Path.of(service.backupNow().file)
        Files.writeString(dir.resolve("secrets.txt"), "nope")

        assertThat(service.resolve(created.fileName.toString())).isEqualTo(created)
        assertThat(service.resolve("secrets.txt")).isNull()
        assertThat(service.resolve("../hist.mv.db")).isNull()
        assertThat(service.resolve("ticker-history-99999999-999999.zip")).isNull() // pattern ok, file absent
    }

    @Test fun `size cap deletes oldest backups first`() {
        val dir = tmp.resolve("bk")
        val service = HistoryBackupService(
            openDb(),
            props(backup = HistoryProperties.BackupProperties(dir = dir.toString(), maxTotalSizeMb = 0)),
        )
        // Two real backups with distinct stamps…
        val first = Path.of(service.backupNow(nowMillis = 1_720_000_000_000).file)
        val second = Path.of(service.backupNow(nowMillis = 1_720_000_060_000).file)
        assertThat(service.list()).hasSize(2)

        // …then a capped service small enough that only one fits: the OLDER one goes.
        val capped = HistoryBackupService(
            jdbc,
            props(backup = HistoryProperties.BackupProperties(dir = dir.toString(), maxTotalSizeMb = 1)),
        )
        // A fresh backup triggers cap enforcement; zips are tiny so 1MB keeps them all — force the
        // pressure instead by padding the oldest file up over the cap.
        val pad = ByteArray(1024 * 1024) { 1 }
        Files.write(first, pad)
        capped.backupNow(nowMillis = 1_720_000_120_000)
        assertThat(Files.exists(first)).isFalse // oldest padded file evicted by the size cap
        assertThat(Files.exists(second)).isTrue
    }
}

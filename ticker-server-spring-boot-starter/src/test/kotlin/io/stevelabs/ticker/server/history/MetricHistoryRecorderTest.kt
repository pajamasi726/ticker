package io.stevelabs.ticker.server.history

import com.zaxxer.hikari.HikariDataSource
import io.stevelabs.ticker.core.ServiceType
import io.stevelabs.ticker.server.detail.MetricSource
import io.stevelabs.ticker.server.detail.Render
import io.stevelabs.ticker.server.detail.ResolvedGroup
import io.stevelabs.ticker.server.detail.ResolvedWidget
import io.stevelabs.ticker.server.detail.Unit
import io.stevelabs.ticker.server.poll.PollProperties
import io.stevelabs.ticker.server.state.HealthStateStore
import io.stevelabs.ticker.server.target.Target
import io.stevelabs.ticker.server.target.TargetDefinition
import io.stevelabs.ticker.server.target.TargetRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.jdbc.core.JdbcTemplate
import java.nio.file.Path

class MetricHistoryRecorderTest {

    private val ds = HikariDataSource().apply {
        jdbcUrl = "jdbc:h2:mem:recorder-test;DB_CLOSE_DELAY=-1"
        maximumPoolSize = 2
    }
    private val jdbc = JdbcTemplate(ds)
    private val repo = MetricHistoryRepository(jdbc)

    private val registry = TargetRegistry(
        listOf(TargetDefinition("spring-svc", ServiceType.SPRING, "http://spring-svc:8080")),
    )
    private val store = HealthStateStore(registry, PollProperties())

    private val stubSource = object : MetricSource {
        override fun fetch(target: Target): List<ResolvedGroup> =
            listOf(
                ResolvedGroup(
                    "JVM",
                    listOf(
                        ResolvedWidget("heap-used", "Heap", Render.GAUGE, Unit.BYTES, 512.0, 1024.0),
                        ResolvedWidget("cpu-process", "CPU", Render.GAUGE, Unit.PERCENT, 0.15, null),
                    ),
                ),
            )
    }

    private val props = HistoryProperties(enabled = true)

    /** A no-op archiver used when archive is disabled (never called). */
    private val noOpArchiver = HistoryArchiver(Path.of("."))

    private val recorder = MetricHistoryRecorder(store, stubSource, repo, noOpArchiver, props)

    @BeforeEach
    fun setUp() {
        repo.ensureSchema(HistoryDb.H2)
        jdbc.execute("DELETE FROM metric_sample")
    }

    @Test
    fun `record inserts one row per numeric widget for each SPRING target`() {
        recorder.record()

        val count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM metric_sample WHERE target_id = 'spring-svc'",
            Int::class.java,
        )
        assertThat(count).isEqualTo(2)
    }

    @Test
    fun `record skips HTTP targets`() {
        val httpRegistry = TargetRegistry(
            listOf(
                TargetDefinition("spring-svc", ServiceType.SPRING, "http://spring-svc:8080"),
                TargetDefinition("http-svc", ServiceType.HTTP, "http://http-svc"),
            ),
        )
        val httpStore = HealthStateStore(httpRegistry, PollProperties())
        val httpRecorder = MetricHistoryRecorder(httpStore, stubSource, repo, noOpArchiver, props)

        httpRecorder.record()

        val springCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM metric_sample WHERE target_id = 'spring-svc'",
            Int::class.java,
        )
        val httpCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM metric_sample WHERE target_id = 'http-svc'",
            Int::class.java,
        )
        assertThat(springCount).isEqualTo(2)
        assertThat(httpCount).isEqualTo(0)
    }

    // -------------------------------------------------------------------------
    // Archive-before-prune tests (guardrail #5)
    // -------------------------------------------------------------------------

    private fun insertOldRow(targetId: String, metricKey: String, tsMillis: Long) {
        jdbc.update(
            "INSERT INTO metric_sample (target_id, metric_key, ts_millis, metric_value) VALUES (?, ?, ?, ?)",
            targetId, metricKey, tsMillis, 1.0,
        )
    }

    private fun rowCount(): Int =
        jdbc.queryForObject("SELECT COUNT(*) FROM metric_sample", Int::class.java) ?: 0

    @Test
    fun `prune with archive DISABLED deletes old rows (existing behaviour intact)`() {
        val now = System.currentTimeMillis()
        val retention = props.retention.toMillis()
        // Insert one old row (beyond retention) and one recent row
        insertOldRow("spring-svc", "heap-used", now - retention - 1_000L)
        insertOldRow("spring-svc", "cpu-process", now - 1_000L)

        val archiveDisabledProps = HistoryProperties(enabled = true, archive = HistoryProperties.ArchiveProperties(enabled = false))
        val recorderNoArchive = MetricHistoryRecorder(store, stubSource, repo, noOpArchiver, archiveDisabledProps)
        recorderNoArchive.prune()

        // Only the recent row should remain
        assertThat(rowCount()).isEqualTo(1)
    }

    @Test
    fun `prune with archive ENABLED archives old rows to csv gz then deletes them from the table`(
        @TempDir archiveDir: Path,
    ) {
        val now = System.currentTimeMillis()
        val retention = props.retention.toMillis()
        insertOldRow("spring-svc", "heap-used", now - retention - 1_000L)
        insertOldRow("spring-svc", "cpu-process", now - retention - 2_000L)
        insertOldRow("spring-svc", "heap-used", now - 1_000L) // recent — must survive

        val archiveProps = HistoryProperties(
            enabled = true,
            archive = HistoryProperties.ArchiveProperties(enabled = true, dir = archiveDir.toString()),
        )
        val realArchiver = HistoryArchiver(archiveDir)
        val recorderWithArchive = MetricHistoryRecorder(store, stubSource, repo, realArchiver, archiveProps)
        recorderWithArchive.prune()

        // Old rows deleted from DB; recent row survives
        assertThat(rowCount()).isEqualTo(1)

        // An archive file was written to the directory
        val archiveFiles = archiveDir.toFile().listFiles { f -> f.name.endsWith(".csv.gz") }
        assertThat(archiveFiles).isNotNull().hasSize(1)
    }

    /**
     * GUARDRAIL #5 TEST — if archive verify fails, prune MUST NOT delete rows.
     *
     * We inject a fake [HistoryArchiver] whose [verify] always returns false.
     * After calling prune(), the pre-prune rows must still be present in the table.
     */
    @Test
    fun `prune with archive ENABLED but verify FAILS does NOT delete rows (guardrail 5)`(
        @TempDir archiveDir: Path,
    ) {
        val now = System.currentTimeMillis()
        val retention = props.retention.toMillis()
        insertOldRow("spring-svc", "heap-used", now - retention - 1_000L)
        insertOldRow("spring-svc", "cpu-process", now - retention - 2_000L)

        val archiveProps = HistoryProperties(
            enabled = true,
            archive = HistoryProperties.ArchiveProperties(enabled = true, dir = archiveDir.toString()),
        )

        // Fake archiver: archive() writes normally but verify() always returns false
        val verifyAlwaysFails = object : HistoryArchiver(archiveDir) {
            override fun verify(file: java.nio.file.Path, expectedRows: Int): Boolean = false
        }

        val recorderWithBadVerify = MetricHistoryRecorder(store, stubSource, repo, verifyAlwaysFails, archiveProps)
        recorderWithBadVerify.prune()

        // Rows must NOT be deleted — guardrail #5 holds
        assertThat(rowCount()).isEqualTo(2)
    }

    /**
     * GUARDRAIL #5 TEST — if archive() throws, prune MUST NOT delete rows.
     */
    @Test
    fun `prune with archive ENABLED but archive throws does NOT delete rows (guardrail 5)`(
        @TempDir archiveDir: Path,
    ) {
        val now = System.currentTimeMillis()
        val retention = props.retention.toMillis()
        insertOldRow("spring-svc", "heap-used", now - retention - 1_000L)

        val archiveProps = HistoryProperties(
            enabled = true,
            archive = HistoryProperties.ArchiveProperties(enabled = true, dir = archiveDir.toString()),
        )

        // Fake archiver: archive() always throws
        val archiveAlwaysThrows = object : HistoryArchiver(archiveDir) {
            override fun archive(rows: List<MetricSampleRow>, stampMillis: Long): java.nio.file.Path =
                throw RuntimeException("simulated I/O failure")
        }

        val recorderWithThrowingArchive = MetricHistoryRecorder(store, stubSource, repo, archiveAlwaysThrows, archiveProps)
        recorderWithThrowingArchive.prune()

        // Row must NOT be deleted — guardrail #5 holds
        assertThat(rowCount()).isEqualTo(1)
    }
}

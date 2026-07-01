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
import org.springframework.jdbc.core.JdbcTemplate

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
    private val recorder = MetricHistoryRecorder(store, stubSource, repo, props)

    @BeforeEach
    fun setUp() {
        repo.ensureSchema()
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
        val httpRecorder = MetricHistoryRecorder(httpStore, stubSource, repo, props)

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
}

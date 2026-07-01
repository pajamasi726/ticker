package io.stevelabs.ticker.server.history

import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate

class MetricHistoryRepositoryTest {

    private val ds = HikariDataSource().apply {
        jdbcUrl = "jdbc:h2:mem:repo-test;DB_CLOSE_DELAY=-1"
        maximumPoolSize = 2
    }
    private val jdbc = JdbcTemplate(ds)
    private val repo = MetricHistoryRepository(jdbc)

    @BeforeEach
    fun setUp() {
        repo.ensureSchema()
        jdbc.execute("DELETE FROM metric_sample")
    }

    @Test
    fun `saveAll inserts rows and query returns averaged buckets`() {
        val base = 1_000_000L
        val bucketMs = 5_000L

        // 3 heap-used rows: two in bucket-0, one in bucket-1
        repo.saveAll("svc", listOf("heap-used" to 100.0, "cpu" to 50.0), base)
        repo.saveAll("svc", listOf("heap-used" to 200.0), base + 1_000)   // (1000/5000)=0 → bucket 0
        repo.saveAll("svc", listOf("heap-used" to 300.0, "cpu" to 150.0), base + 5_000) // bucket 1

        val points = repo.query("svc", "heap-used", base, bucketMs)

        assertThat(points).hasSize(2)
        assertThat(points[0].t).isEqualTo(base)            // from + 0 * bucketMs
        assertThat(points[0].v).isEqualTo(150.0)           // avg(100, 200)
        assertThat(points[1].t).isEqualTo(base + 5_000)    // from + 1 * bucketMs
        assertThat(points[1].v).isEqualTo(300.0)
    }

    @Test
    fun `query returns empty list when no data`() {
        val points = repo.query("svc", "heap-used", 0L, 1_000L)
        assertThat(points).isEmpty()
    }

    @Test
    fun `prune removes rows before the given timestamp and returns count`() {
        val now = System.currentTimeMillis()
        repo.saveAll("svc", listOf("cpu" to 1.0), now - 10_000) // older → pruned
        repo.saveAll("svc", listOf("cpu" to 2.0), now - 5_000)  // newer → kept
        repo.saveAll("svc", listOf("cpu" to 3.0), now)           // newest → kept

        val pruned = repo.prune(now - 7_000)
        assertThat(pruned).isEqualTo(1)

        val remaining = repo.query("svc", "cpu", now - 20_000, 1_000L)
        assertThat(remaining).hasSize(2)
    }
}

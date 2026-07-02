package io.stevelabs.ticker.server.history

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class HistoryAutoConfigurationTest {

    private val autoConfig = HistoryAutoConfiguration()

    @Test
    fun `H2 default path produces jdbc h2 file URL`() {
        val props = HistoryProperties(enabled = true, db = HistoryDb.H2)
        val ds = autoConfig.historyDataSource(props)
        try {
            assertThat(ds.jdbcUrl).startsWith("jdbc:h2:file:")
        } finally {
            ds.close()
        }
    }

    @Test
    fun `H2 with explicit url uses provided url`() {
        val props = HistoryProperties(enabled = true, db = HistoryDb.H2, url = "jdbc:h2:mem:custom-test")
        val ds = autoConfig.historyDataSource(props)
        try {
            assertThat(ds.jdbcUrl).isEqualTo("jdbc:h2:mem:custom-test")
        } finally {
            ds.close()
        }
    }

    @Test
    fun `MYSQL with no url throws IllegalStateException`() {
        val props = HistoryProperties(enabled = true, db = HistoryDb.MYSQL, url = null)
        assertThatThrownBy { autoConfig.historyDataSource(props) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("ticker.history.url is required")
    }

    @Test
    fun `POSTGRESQL with no url throws IllegalStateException`() {
        val props = HistoryProperties(enabled = true, db = HistoryDb.POSTGRESQL, url = null)
        assertThatThrownBy { autoConfig.historyDataSource(props) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("ticker.history.url is required")
    }

    @Test
    fun `MYSQL with url but no driver on the classpath names the missing dependency`() {
        // The MySQL driver is deliberately NOT a test dependency — this asserts the friendly
        // pre-flight instead of Hikari's cryptic "No suitable driver".
        val props = HistoryProperties(enabled = true, db = HistoryDb.MYSQL, url = "jdbc:mysql://db-host:3306/ticker")
        assertThatThrownBy { autoConfig.historyDataSource(props) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("com.mysql:mysql-connector-j")
    }

    @Test
    fun `H2 driver is bundled so the pre-flight passes`() {
        val props = HistoryProperties(enabled = true, db = HistoryDb.H2, url = "jdbc:h2:mem:driver-check")
        autoConfig.historyDataSource(props).close() // must not throw
    }
}

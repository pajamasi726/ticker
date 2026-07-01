package io.stevelabs.ticker.server.history

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class HistoryPropertiesTest {

    @Test
    fun `default db is H2`() {
        val props = HistoryProperties()
        assertThat(props.db).isEqualTo(HistoryDb.H2)
    }

    @Test
    fun `default initSchema is true`() {
        val props = HistoryProperties()
        assertThat(props.initSchema).isTrue()
    }

    @Test
    fun `default enabled is false`() {
        val props = HistoryProperties()
        assertThat(props.enabled).isFalse()
    }

    @Test
    fun `non-H2 db types are available`() {
        val mysql = HistoryProperties(db = HistoryDb.MYSQL, url = "jdbc:mysql://host:3306/ticker")
        assertThat(mysql.db).isEqualTo(HistoryDb.MYSQL)

        val pg = HistoryProperties(db = HistoryDb.POSTGRESQL, url = "jdbc:postgresql://host:5432/ticker")
        assertThat(pg.db).isEqualTo(HistoryDb.POSTGRESQL)
    }
}

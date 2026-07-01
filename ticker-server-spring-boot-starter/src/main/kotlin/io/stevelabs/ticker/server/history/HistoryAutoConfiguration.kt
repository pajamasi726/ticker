package io.stevelabs.ticker.server.history

import com.zaxxer.hikari.HikariDataSource
import io.stevelabs.ticker.server.TickerServerAutoConfiguration
import io.stevelabs.ticker.server.detail.DetailProperties
import io.stevelabs.ticker.server.detail.MetricSource
import io.stevelabs.ticker.server.state.HealthStateStore
import io.stevelabs.ticker.server.target.TargetRegistry
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.jdbc.core.JdbcTemplate

@AutoConfiguration(after = [TickerServerAutoConfiguration::class])
@ConditionalOnProperty(prefix = "ticker.history", name = ["enabled"], havingValue = "true")
@EnableConfigurationProperties(HistoryProperties::class)
class HistoryAutoConfiguration {

    @Bean(destroyMethod = "close")
    fun historyDataSource(props: HistoryProperties): HikariDataSource =
        HikariDataSource().also { ds ->
            ds.jdbcUrl = props.url ?: "jdbc:h2:file:${props.h2Path};DB_CLOSE_DELAY=-1"
            props.username?.let { ds.username = it }
            props.password?.let { ds.password = it }
            ds.maximumPoolSize = 3
            ds.poolName = "ticker-history"
        }

    @Bean
    fun historyJdbcTemplate(historyDataSource: HikariDataSource): JdbcTemplate =
        JdbcTemplate(historyDataSource)

    @Bean
    fun metricHistoryRepository(historyJdbcTemplate: JdbcTemplate): MetricHistoryRepository =
        MetricHistoryRepository(historyJdbcTemplate).also { it.ensureSchema() }

    @Bean
    fun metricHistoryRecorder(
        store: HealthStateStore,
        metricSource: MetricSource,
        metricHistoryRepository: MetricHistoryRepository,
        props: HistoryProperties,
    ): MetricHistoryRecorder = MetricHistoryRecorder(store, metricSource, metricHistoryRepository, props)

    @Bean
    fun metricHistoryController(
        registry: TargetRegistry,
        metricHistoryRepository: MetricHistoryRepository,
        detailProperties: DetailProperties,
        props: HistoryProperties,
    ): MetricHistoryController = MetricHistoryController(registry, metricHistoryRepository, detailProperties, props)
}

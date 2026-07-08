package io.stevelabs.ticker.server.history

import com.zaxxer.hikari.HikariDataSource
import io.stevelabs.ticker.server.TickerServerAutoConfiguration
import io.stevelabs.ticker.server.detail.DetailProperties
import io.stevelabs.ticker.server.detail.MetricSource
import io.stevelabs.ticker.server.state.HealthStateStore
import io.stevelabs.ticker.server.target.TargetRegistry
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.jdbc.core.JdbcTemplate

@AutoConfiguration(after = [TickerServerAutoConfiguration::class])
@ConditionalOnProperty(prefix = "ticker.history", name = ["enabled"], havingValue = "true")
@EnableConfigurationProperties(HistoryProperties::class)
class HistoryAutoConfiguration {

    private val log = LoggerFactory.getLogger(HistoryAutoConfiguration::class.java)

    @Bean(destroyMethod = "close")
    fun historyDataSource(props: HistoryProperties): HikariDataSource =
        HikariDataSource().also { ds ->
            ds.jdbcUrl = when (props.db) {
                HistoryDb.H2 -> props.url ?: "jdbc:h2:file:${props.h2Path};DB_CLOSE_DELAY=-1"
                else -> props.url
                    ?: throw IllegalStateException(
                        "ticker.history.url is required when ticker.history.db=${props.db}",
                    )
            }
            requireDriver(props.db) // after the url check: config errors first, classpath errors second
            props.username?.let { ds.username = it }
            props.password?.let { ds.password = it }
            ds.maximumPoolSize = 3
            ds.poolName = "ticker-history"
        }

    @Bean
    fun historyBackupService(historyJdbcTemplate: JdbcTemplate, props: HistoryProperties): HistoryBackupService =
        HistoryBackupService(historyJdbcTemplate, props)

    @Bean
    fun historyBackupScheduler(historyBackupService: HistoryBackupService, props: HistoryProperties): HistoryBackupScheduler =
        HistoryBackupScheduler(historyBackupService).also {
            props.backup.schedule?.let { cron -> log.info("Scheduled history backups: cron '{}' -> {}", cron, props.backup.dir) }
        }

    /** A missing JDBC driver otherwise dies as Hikari's "No suitable driver" — name the dependency instead. */
    private fun requireDriver(db: HistoryDb) {
        val (driverClass, dependency) = when (db) {
            HistoryDb.H2 -> "org.h2.Driver" to "com.h2database:h2"
            HistoryDb.MYSQL -> "com.mysql.cj.jdbc.Driver" to "com.mysql:mysql-connector-j"
            HistoryDb.POSTGRESQL -> "org.postgresql.Driver" to "org.postgresql:postgresql"
        }
        if (!org.springframework.util.ClassUtils.isPresent(driverClass, javaClass.classLoader)) {
            throw IllegalStateException(
                "ticker.history.db=$db but its JDBC driver is not on the classpath — add the '$dependency' dependency.",
            )
        }
    }

    @Bean
    fun historyJdbcTemplate(historyDataSource: HikariDataSource): JdbcTemplate =
        JdbcTemplate(historyDataSource)

    @Bean
    fun metricHistoryRepository(historyJdbcTemplate: JdbcTemplate, props: HistoryProperties): MetricHistoryRepository =
        MetricHistoryRepository(historyJdbcTemplate).also { repo ->
            if (props.initSchema) repo.ensureSchema(props.db)
            log.info(
                "Ticker metric history: db={}, schema {} (classpath:db/ticker-history-schema-{}.sql)",
                props.db,
                if (props.initSchema) "auto-created" else "NOT auto-created — run the DDL yourself (init-schema=false)",
                props.db.name.lowercase(),
            )
        }

    @Bean
    fun historyArchiver(props: HistoryProperties): HistoryArchiver =
        HistoryArchiver(java.nio.file.Path.of(props.archive.dir))

    @Bean
    fun metricHistoryRecorder(
        store: HealthStateStore,
        metricSource: MetricSource,
        metricHistoryRepository: MetricHistoryRepository,
        historyArchiver: HistoryArchiver,
        props: HistoryProperties,
    ): MetricHistoryRecorder = MetricHistoryRecorder(store, metricSource, metricHistoryRepository, historyArchiver, props)

    @Bean
    fun metricHistoryController(
        registry: TargetRegistry,
        metricHistoryRepository: MetricHistoryRepository,
        detailProperties: DetailProperties,
        props: HistoryProperties,
    ): MetricHistoryController = MetricHistoryController(registry, metricHistoryRepository, detailProperties, props)
}

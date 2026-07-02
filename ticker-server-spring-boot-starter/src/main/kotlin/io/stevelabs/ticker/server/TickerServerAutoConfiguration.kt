package io.stevelabs.ticker.server

import io.stevelabs.ticker.server.alert.MetricAlertRule
import io.stevelabs.ticker.server.check.HealthChecker
import io.stevelabs.ticker.server.check.HttpHealthChecker
import io.stevelabs.ticker.server.check.SpringHealthChecker
import io.stevelabs.ticker.server.config.TickerConfig
import io.stevelabs.ticker.server.config.TickerConfigurer
import io.stevelabs.ticker.server.detail.DetailProperties
import io.stevelabs.ticker.server.detail.MetricFetcher
import io.stevelabs.ticker.server.detail.MetricSource
import io.stevelabs.ticker.server.poll.PollProperties
import io.stevelabs.ticker.server.poll.Poller
import io.stevelabs.ticker.server.state.HealthStateStore
import io.stevelabs.ticker.server.target.InMemoryUiTargetStore
import io.stevelabs.ticker.server.target.JsonFileUiTargetStore
import io.stevelabs.ticker.server.target.TargetRegistry
import io.stevelabs.ticker.server.target.TargetsProperties
import io.stevelabs.ticker.server.target.UiTargetStore
import io.stevelabs.ticker.server.web.SpaCacheControlFilter
import io.stevelabs.ticker.server.web.SpaShellController
import io.stevelabs.ticker.server.web.TickerSpaWebConfigurer
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.web.client.RestClient
import java.nio.file.Path
import tools.jackson.databind.ObjectMapper
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@AutoConfiguration
@EnableConfigurationProperties(TickerServerProperties::class, PollProperties::class, TargetsProperties::class, DetailProperties::class)
@EnableScheduling
@ConditionalOnProperty(prefix = "ticker.server", name = ["enabled"], matchIfMissing = true)
class TickerServerAutoConfiguration {
    @Bean fun uiTargetStore(props: TargetsProperties, objectMapper: ObjectMapper): UiTargetStore =
        props.uiTargetsStorePath?.let { JsonFileUiTargetStore(Path.of(it), objectMapper) } ?: InMemoryUiTargetStore()

    @Bean
    fun tickerConfig(targetsProps: TargetsProperties, configurers: ObjectProvider<TickerConfigurer>): TickerConfig {
        val config = TickerConfig(targetsProps.targets, MetricAlertRule.DEFAULTS)
        configurers.orderedStream().forEach { it.configure(config) }
        return config
    }

    @Bean fun targetRegistry(config: TickerConfig, uiTargetStore: UiTargetStore) = TargetRegistry(config.targets(), uiTargetStore)
    @Bean fun healthStateStore(registry: TargetRegistry, poll: PollProperties) = HealthStateStore(registry, poll)
    @Bean fun tickerRestClient(poll: PollProperties): RestClient {
        val factory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(poll.timeout.toMillis().toInt()); setReadTimeout(poll.timeout.toMillis().toInt())
        }
        return RestClient.builder().requestFactory(factory).build()
    }
    @Bean fun httpHealthChecker(restClient: RestClient, poll: PollProperties) = HttpHealthChecker(restClient, poll)
    @Bean fun springHealthChecker(restClient: RestClient, poll: PollProperties) = SpringHealthChecker(restClient, poll)
    @Bean(destroyMethod = "close") fun pollExecutor(): ExecutorService = Executors.newVirtualThreadPerTaskExecutor()
    @Bean fun poller(registry: TargetRegistry, checkers: List<HealthChecker>, store: HealthStateStore, executor: ExecutorService, poll: PollProperties, server: TickerServerProperties) =
        Poller(registry, checkers, store, executor, poll, server.registrationExpiry.toMillis())
    @Bean fun metricFetcher(restClient: RestClient, detailProperties: DetailProperties, executor: ExecutorService, poll: PollProperties): MetricSource =
        MetricFetcher(restClient, detailProperties, executor, poll)
    @Bean fun serviceController(store: HealthStateStore) = ServiceController(store)
    @Bean fun targetController(registry: TargetRegistry, store: HealthStateStore) = TargetController(registry, store)
    @Bean fun detailController(registry: TargetRegistry, store: HealthStateStore, metricSource: MetricSource, detailProperties: DetailProperties) =
        DetailController(registry, store, metricSource, detailProperties)
    @Bean fun spaCacheControlFilter(props: TickerServerProperties) = SpaCacheControlFilter(props.basePath)

    /** Relocates UI + API under ticker.server.base-path (no-op when unset). */
    @Bean fun tickerSpaWebConfigurer(props: TickerServerProperties): WebMvcConfigurer = TickerSpaWebConfigurer(props.basePath)

    /** SPA shell with the base path injected — only when a base path is configured. */
    @Bean
    @ConditionalOnProperty(prefix = "ticker.server", name = ["base-path"])
    fun spaShellController(props: TickerServerProperties) = SpaShellController(props)
}

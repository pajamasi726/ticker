package io.stevelabs.ticker.server

import io.stevelabs.ticker.server.check.HealthChecker
import io.stevelabs.ticker.server.check.HttpHealthChecker
import io.stevelabs.ticker.server.check.SpringHealthChecker
import io.stevelabs.ticker.server.poll.PollProperties
import io.stevelabs.ticker.server.poll.Poller
import io.stevelabs.ticker.server.state.HealthStateStore
import io.stevelabs.ticker.server.target.TargetRegistry
import io.stevelabs.ticker.server.target.TargetsProperties
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.web.client.RestClient
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@AutoConfiguration
@EnableConfigurationProperties(TickerServerProperties::class, PollProperties::class, TargetsProperties::class)
@EnableScheduling
@ConditionalOnProperty(prefix = "ticker.server", name = ["enabled"], matchIfMissing = true)
class TickerServerAutoConfiguration {
    @Bean fun targetRegistry(props: TargetsProperties) = TargetRegistry(props.targets)
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
    @Bean fun poller(registry: TargetRegistry, checkers: List<HealthChecker>, store: HealthStateStore, executor: ExecutorService, poll: PollProperties) =
        Poller(registry, checkers, store, executor, poll)
    @Bean fun serviceController(store: HealthStateStore) = ServiceController(store)
    @Bean fun targetController(registry: TargetRegistry, store: HealthStateStore) = TargetController(registry, store)
}

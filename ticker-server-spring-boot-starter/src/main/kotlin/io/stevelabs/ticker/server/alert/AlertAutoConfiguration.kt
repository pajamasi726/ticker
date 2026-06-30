package io.stevelabs.ticker.server.alert

import io.stevelabs.ticker.server.TickerServerAutoConfiguration
import io.stevelabs.ticker.server.detail.MetricSource
import io.stevelabs.ticker.server.state.HealthStateStore
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@AutoConfiguration
@AutoConfigureAfter(TickerServerAutoConfiguration::class)
@ConditionalOnProperty(prefix = "ticker.alert", name = ["enabled"], havingValue = "true")
@EnableConfigurationProperties(AlertProperties::class)
class AlertAutoConfiguration {

    @Bean
    fun alertDecider(): AlertDecider = AlertDecider()

    @Bean
    @ConditionalOnProperty(prefix = "ticker.alert", name = ["slack-webhook-url"])
    fun slackSender(properties: AlertProperties): SlackSender {
        val factory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(5000)
            setReadTimeout(5000)
        }
        return SlackSender(RestClient.builder().requestFactory(factory).build(), properties.slackWebhookUrl!!)
    }

    /**
     * Fallback executor used when TickerServerAutoConfiguration (and its pollExecutor) is not active.
     * Typed ExecutorService so it resolves unambiguously: with virtual threads on, the context also has
     * `applicationTaskExecutor` and `taskScheduler` Executors — only pollExecutor is an ExecutorService.
     */
    @Bean
    @ConditionalOnMissingBean(ExecutorService::class)
    fun alertFallbackExecutor(): ExecutorService = Executors.newVirtualThreadPerTaskExecutor()

    @Bean
    fun alertService(
        store: HealthStateStore,
        decider: AlertDecider,
        properties: AlertProperties,
        sender: ObjectProvider<AlertSender>,
        executor: ExecutorService,
    ): AlertService = AlertService(store, decider, properties, sender.ifAvailable, executor)

    @Bean
    fun metricAlertStore(): MetricAlertStore = MetricAlertStore()

    @Bean
    fun metricAlertService(
        store: HealthStateStore,
        metricSource: MetricSource,
        rules: MetricAlertStore,
        sender: ObjectProvider<AlertSender>,
        executor: ExecutorService,
    ): MetricAlertService = MetricAlertService(store, metricSource, rules, sender.ifAvailable, executor)

    @Bean
    fun metricAlertController(rules: MetricAlertStore): MetricAlertController = MetricAlertController(rules)
}

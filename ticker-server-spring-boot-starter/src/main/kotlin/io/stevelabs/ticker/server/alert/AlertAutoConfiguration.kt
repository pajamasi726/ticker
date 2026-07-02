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

/**
 * Alert EVALUATION + DISPATCH — the part `ticker.alert.enabled` gates. The rule store, the
 * silence window, and the `/api/alerts` read/edit endpoints live in [AlertApiAutoConfiguration]
 * (always on with the collector), so an alerts-off dashboard still gets severity thresholds.
 */
@AutoConfiguration
@AutoConfigureAfter(TickerServerAutoConfiguration::class, AlertApiAutoConfiguration::class)
@ConditionalOnProperty(prefix = "ticker.alert", name = ["enabled"], havingValue = "true")
@EnableConfigurationProperties(AlertProperties::class, io.stevelabs.ticker.server.TickerServerProperties::class)
class AlertAutoConfiguration {

    @Bean
    fun alertDecider(): AlertDecider = AlertDecider()

    /**
     * Blank counts as unset: templated configs like `slack-webhook-url: ${SLACK_WEBHOOK_URL:}` resolve
     * to "" when the env var is absent — that must mean "no Slack" (alerts go inert with a warning),
     * not a sender firing at an empty URL. Hence the hasText expression instead of ConditionalOnProperty.
     */
    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnExpression(
        "T(org.springframework.util.StringUtils).hasText('\${ticker.alert.slack-webhook-url:}')",
    )
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
        silence: AlertSilence,
        server: io.stevelabs.ticker.server.TickerServerProperties,
    ): AlertService = AlertService(store, decider, properties, sender.ifAvailable, executor, silence, server.publicUrl)

    @Bean
    fun metricAlertService(
        store: HealthStateStore,
        metricSource: MetricSource,
        rules: MetricAlertStore,
        sender: ObjectProvider<AlertSender>,
        executor: ExecutorService,
        silence: AlertSilence,
        server: io.stevelabs.ticker.server.TickerServerProperties,
    ): MetricAlertService = MetricAlertService(store, metricSource, rules, sender.ifAvailable, executor, silence, server.publicUrl)
}

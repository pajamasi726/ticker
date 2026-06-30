package io.stevelabs.ticker.server.alert

import io.stevelabs.ticker.server.TickerServerAutoConfiguration
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
import java.util.concurrent.Executor
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

    /** Fallback executor used when TickerServerAutoConfiguration (and its pollExecutor) is not active. */
    @Bean
    @ConditionalOnMissingBean(Executor::class)
    fun alertFallbackExecutor(): Executor = Executors.newVirtualThreadPerTaskExecutor()

    @Bean
    fun alertService(
        store: HealthStateStore,
        decider: AlertDecider,
        properties: AlertProperties,
        sender: ObjectProvider<AlertSender>,
        executor: Executor,
    ): AlertService = AlertService(store, decider, properties, sender.ifAvailable, executor)
}

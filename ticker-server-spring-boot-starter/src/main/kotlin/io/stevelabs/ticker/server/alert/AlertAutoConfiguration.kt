package io.stevelabs.ticker.server.alert

import io.stevelabs.ticker.server.state.HealthStateStore
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient

@AutoConfiguration
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

    @Bean
    fun alertService(
        store: HealthStateStore,
        decider: AlertDecider,
        properties: AlertProperties,
        sender: ObjectProvider<AlertSender>,
    ): AlertService = AlertService(store, decider, properties, sender.ifAvailable)
}

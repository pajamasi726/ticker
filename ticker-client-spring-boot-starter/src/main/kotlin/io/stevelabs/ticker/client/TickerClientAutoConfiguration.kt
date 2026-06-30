package io.stevelabs.ticker.client

import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.core.env.Environment
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient

@AutoConfiguration
@EnableConfigurationProperties(TickerClientProperties::class)
@ConditionalOnProperty(prefix = "ticker.client", name = ["enabled"], matchIfMissing = true)
class TickerClientAutoConfiguration {

    @Bean
    fun tickerClientRegistrar(
        properties: TickerClientProperties,
        environment: Environment,
    ): TickerClientRegistrar {
        val factory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(3000)
            setReadTimeout(3000)
        }
        val restClient = RestClient.builder().requestFactory(factory).build()
        return TickerClientRegistrar(properties, environment, restClient)
    }
}

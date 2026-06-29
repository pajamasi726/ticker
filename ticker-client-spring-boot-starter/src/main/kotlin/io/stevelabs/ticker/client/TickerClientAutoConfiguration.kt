package io.stevelabs.ticker.client

import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.core.env.Environment

@AutoConfiguration
@EnableConfigurationProperties(TickerClientProperties::class)
@ConditionalOnProperty(prefix = "ticker.client", name = ["enabled"], matchIfMissing = true)
class TickerClientAutoConfiguration {

    @Bean
    fun tickerClientRegistrar(properties: TickerClientProperties, environment: Environment): TickerClientRegistrar =
        TickerClientRegistrar(properties, environment)
}

package io.stevelabs.ticker.server

import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean

/** Activates the Ticker collector (REST API + bundled UI). On by default when the starter is present. */
@AutoConfiguration
@EnableConfigurationProperties(TickerServerProperties::class)
@ConditionalOnProperty(prefix = "ticker.server", name = ["enabled"], matchIfMissing = true)
class TickerServerAutoConfiguration {

    @Bean
    fun mockServices(): MockServices = MockServices()

    @Bean
    fun serviceController(mockServices: MockServices): ServiceController = ServiceController(mockServices)
}

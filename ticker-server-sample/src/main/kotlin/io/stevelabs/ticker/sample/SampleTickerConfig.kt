package io.stevelabs.ticker.sample

import io.stevelabs.ticker.core.ServiceType
import io.stevelabs.ticker.server.config.TickerConfigurer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SampleTickerConfig {
    @Bean
    fun sampleTickerConfigurer() = TickerConfigurer { t ->
        t.addTarget("code-configured", ServiceType.HTTP, "http://localhost:8080/actuator/health", tags = listOf("code"))
        t.configureAlert("cpu-process", threshold = 0.75, forSeconds = 15)
    }
}

package io.stevelabs.ticker.client

import io.stevelabs.ticker.core.ServiceType
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "ticker.client")
data class TickerClientProperties(
    val enabled: Boolean = true,
    val collectorUrl: String? = null,
    val url: String? = null,
    val name: String? = null,
    val type: ServiceType = ServiceType.SPRING,
    val tags: List<String> = emptyList(),
    val heartbeatInterval: Duration = Duration.ofSeconds(30),
)

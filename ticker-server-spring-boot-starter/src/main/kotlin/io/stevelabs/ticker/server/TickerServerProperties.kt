package io.stevelabs.ticker.server

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "ticker.server")
data class TickerServerProperties(
    val enabled: Boolean = true,
)

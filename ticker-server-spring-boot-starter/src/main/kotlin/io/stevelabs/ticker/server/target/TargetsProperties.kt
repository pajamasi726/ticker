package io.stevelabs.ticker.server.target

import io.stevelabs.ticker.core.ServiceType
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "ticker")
data class TargetsProperties(
    val targets: List<TargetDefinition> = emptyList(),
)

data class TargetDefinition(
    val name: String,
    val type: ServiceType,
    val url: String,
    val tags: List<String> = emptyList(),
)

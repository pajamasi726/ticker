package io.stevelabs.ticker.server.target

import io.stevelabs.ticker.core.ServiceType
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "ticker")
data class TargetsProperties(
    val targets: List<TargetDefinition> = emptyList(),
    val uiTargetsStorePath: String? = null,
)

data class TargetDefinition(
    val name: String = "",
    val type: ServiceType = ServiceType.HTTP,
    val url: String = "",
    val tags: List<String> = emptyList(),
) {
    // Defaults + require (instead of non-null constructor params) so a missing field fails startup
    // with THIS message inside Spring's bind report — not a bare Kotlin NullPointerException.
    init {
        require(name.isNotBlank()) { "a ticker.targets entry is missing 'name' (each entry needs: name, url; type defaults to HTTP)" }
        require(url.isNotBlank()) { "ticker.targets entry '$name' is missing 'url' (each entry needs: name, url; type defaults to HTTP)" }
    }
}

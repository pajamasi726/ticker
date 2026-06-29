package io.stevelabs.ticker.core

/** What a client app sends to register itself with the collector. Shared client→server contract. */
data class RegistrationRequest(
    val name: String,
    val type: ServiceType,
    val url: String,
    val tags: List<String> = emptyList(),
)

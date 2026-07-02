package io.stevelabs.ticker.core

/** What a client app sends to register itself with the collector. Shared client→server contract. */
data class RegistrationRequest(
    val name: String,
    val type: ServiceType,
    val url: String,
    val tags: List<String> = emptyList(),
    /**
     * This instance's identity — its hostname (e.g. the pod / container / machine name), so the
     * collector can tell apart multiple replicas that register under the same [name]. Optional and
     * added last for wire compatibility: older clients omit it (the collector falls back to the URL
     * host:port), older collectors ignore it.
     */
    val instance: String? = null,
)

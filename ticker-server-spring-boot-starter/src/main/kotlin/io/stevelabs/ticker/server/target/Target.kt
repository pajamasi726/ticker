package io.stevelabs.ticker.server.target

import io.stevelabs.ticker.core.ServiceType

enum class TargetSource { STATIC, REGISTERED, UI }

data class Target(
    val id: String,
    val name: String,
    val type: ServiceType,
    val url: String,
    val tags: List<String> = emptyList(),
    val source: TargetSource = TargetSource.STATIC,
    /**
     * Instance discriminator (host:port from [url]) for telling apart multiple replicas that register
     * under the same [name] — e.g. three `orders-api` pods. Null for static / UI targets and for the
     * displayed grouping name. Registered targets set it so the wall can show which instance is which.
     */
    val instance: String? = null,
    /** The instance's self-reported IP address (registered targets only); shown on the detail header. */
    val ip: String? = null,
)

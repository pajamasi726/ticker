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
)

package io.stevelabs.ticker.server

import io.stevelabs.ticker.core.ServiceType
import io.stevelabs.ticker.server.state.ServiceState

/**
 * UI feed row for the status wall. Phase 0 values are hardcoded mock data; the
 * shape matches the eventual real /api/services contract (target + state + tiny series).
 */
data class ServiceView(
    val id: String,
    val name: String,
    val type: ServiceType,
    val state: ServiceState,
    val tags: List<String> = emptyList(),
    val latencyMs: Int?,          // last poll latency; null if never polled / failed
    val sparkline: List<Int?>,    // recent latency samples, oldest -> newest; null = failed sample (gap)
)

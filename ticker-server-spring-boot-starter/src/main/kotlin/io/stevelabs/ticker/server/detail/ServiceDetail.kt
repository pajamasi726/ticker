package io.stevelabs.ticker.server.detail

import io.stevelabs.ticker.core.ServiceType
import io.stevelabs.ticker.server.state.ServiceState

data class ServiceDetail(
    val id: String,
    val name: String,
    val type: ServiceType,
    val state: ServiceState,
    val latencyMs: Int?,
    val sparkline: List<Int?>,
    val metrics: List<MetricValue>,
)

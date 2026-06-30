package io.stevelabs.ticker.server.detail

import io.stevelabs.ticker.core.ServiceType
import io.stevelabs.ticker.server.state.ServiceState

/** One resolved widget: a value (and optional gauge max), tagged with how to render/format it. */
data class ResolvedWidget(
    val key: String,
    val label: String,
    val render: Render,
    val unit: Unit,
    val value: Double?,
    val max: Double?,
    val cumulative: Boolean = false,
    val higherIsBetter: Boolean = false,
)

/** A titled section of resolved widgets. */
data class ResolvedGroup(
    val title: String,
    val widgets: List<ResolvedWidget>,
)

data class ServiceDetail(
    val id: String,
    val name: String,
    val type: ServiceType,
    val state: ServiceState,
    val latencyMs: Int?,
    val sparkline: List<Int?>,
    val groups: List<ResolvedGroup>,
)

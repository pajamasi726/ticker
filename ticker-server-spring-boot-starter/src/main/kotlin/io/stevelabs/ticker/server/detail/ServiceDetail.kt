package io.stevelabs.ticker.server.detail

import io.stevelabs.ticker.core.ServiceType
import io.stevelabs.ticker.server.state.ServiceState

/** One resolved widget: a value (and optional gauge max), tagged with how to render/format it.
 *  `available` is false when the target does not expose this metric — the UI shows it dimmed
 *  ("not collected") rather than hiding it, so the full curated catalog stays visible. */
data class ResolvedWidget(
    val key: String,
    val label: String,
    val render: Render,
    val unit: Unit,
    val value: Double?,
    val max: Double?,
    val cumulative: Boolean = false,
    val higherIsBetter: Boolean = false,
    val perSecond: Boolean = false,
    val ratio: RatioSpec? = null,
    val available: Boolean = true,
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
    // Identity line for the header: which instance this is (hostname:port — registered replicas only),
    // its self-reported IP, and the URL the collector polls. instance/ip are null for static/UI targets.
    val instance: String? = null,
    val ip: String? = null,
    val url: String? = null,
)

/** One row in a per-tag metric breakdown (e.g. http.server.requests by uri). */
data class TagStat(
    val value: String,
    val count: Double?,
    val mean: Double?,
    val max: Double?,
)

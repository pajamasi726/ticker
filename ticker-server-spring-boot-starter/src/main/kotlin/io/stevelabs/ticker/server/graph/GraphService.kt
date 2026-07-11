package io.stevelabs.ticker.server.graph

import io.stevelabs.ticker.core.ServiceType
import io.stevelabs.ticker.server.detail.MetricSource
import io.stevelabs.ticker.server.state.HealthStateStore
import io.stevelabs.ticker.server.state.ServiceState
import io.stevelabs.ticker.server.target.TargetRegistry
import java.net.URI
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

data class GraphNode(val name: String, val state: ServiceState, val external: Boolean)
data class GraphEdge(
    val from: String,
    val to: String,
    val external: Boolean,
    val count: Double,
    val mean: Double?, // seconds, count-weighted across replicas
    val max: Double?,
    val error5xx: Double,
)
data class ServiceGraph(val nodes: List<GraphNode>, val edges: List<GraphEdge>)

/**
 * The wall-level service map, from aggregates: every SPRING service's outbound edges
 * (http.client.requests by client.name), merged across same-named replicas and resolved back to
 * wall service NAMES where the called host maps to exactly one of them — hosts that don't are
 * external nodes. One collector-side sweep serves both the map view and the detail view's
 * "who calls me" list. Cached briefly so UI polling doesn't multiply the actuator fan-out.
 */
class GraphService(
    private val registry: TargetRegistry,
    private val store: HealthStateStore,
    private val metricSource: MetricSource,
    private val cacheTtlMillis: Long = 10_000,
) {
    private data class Cached(val at: Long, val graph: ServiceGraph)

    private val cache = AtomicReference<Cached?>(null)

    fun graph(nowMillis: Long = System.currentTimeMillis()): ServiceGraph {
        cache.get()?.takeIf { nowMillis - it.at < cacheTtlMillis }?.let { return it.graph }
        val built = build()
        cache.set(Cached(nowMillis, built))
        return built
    }

    private fun build(): ServiceGraph {
        val targets = registry.all()
        val states = store.snapshot(Instant.now()).associate { it.target.id to it.state }

        // Wall nodes: one per NAME, worst-of-replicas state (the wall's own grouping rule).
        val nodeState = targets.groupBy { it.name }.mapValues { (_, group) ->
            group.map { states[it.id] ?: ServiceState.UNKNOWN }.maxByOrNull { SEVERITY.indexOf(it) }
                ?: ServiceState.UNKNOWN
        }

        // Callee resolution table: URL host -> unique service name (any target type — nginx counts).
        val hostToName = targets
            .mapNotNull { t -> hostOf(t.url)?.let { it to t.name } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, names) -> names.distinct() }
            .filterValues { it.size == 1 }
            .mapValues { it.value.single() }

        // Sweep every SPRING instance's outbound and merge edges by (fromName -> toKey).
        data class Acc(var count: Double, var weighted: Double, var max: Double?, var errors: Double)
        val acc = LinkedHashMap<Triple<String, String, Boolean>, Acc>()
        for (t in targets.filter { it.type == ServiceType.SPRING }) {
            val rows = metricSource.tagBreakdown(t, "http.client.requests", "client.name")
            if (rows.isEmpty()) continue
            val errors = metricSource
                .tagBreakdown(t, "http.client.requests", "client.name", mapOf("outcome" to "SERVER_ERROR"))
                .associate { it.value to (it.count ?: 0.0) }
            for (row in rows) {
                val resolvedName = hostToName[row.value]
                if (resolvedName == t.name) continue // self-call noise (an app hitting its own host)
                val toKey = resolvedName ?: row.value
                val key = Triple(t.name, toKey, resolvedName == null)
                val a = acc.getOrPut(key) { Acc(0.0, 0.0, null, 0.0) }
                val c = row.count ?: 0.0
                a.count += c
                row.mean?.let { a.weighted += it * c }
                a.max = listOfNotNull(a.max, row.max).maxOrNull()
                a.errors += errors[row.value] ?: 0.0
            }
        }

        val edges = acc.map { (key, a) ->
            GraphEdge(
                from = key.first,
                to = key.second,
                external = key.third,
                count = a.count,
                mean = if (a.count > 0 && a.weighted > 0) a.weighted / a.count else null,
                max = a.max,
                error5xx = a.errors,
            )
        }.sortedByDescending { it.count }

        val serviceNodes = nodeState.map { (name, state) -> GraphNode(name, state, external = false) }
        val externalNodes = edges.filter { it.external }.map { it.to }.distinct()
            .map { GraphNode(it, ServiceState.UNKNOWN, external = true) }
        return ServiceGraph(nodes = serviceNodes + externalNodes, edges = edges)
    }

    private fun hostOf(url: String): String? = try {
        URI(url).host
    } catch (_: Exception) {
        null
    }

    companion object {
        /** Ascending severity for worst-of-replicas (index = badness). */
        private val SEVERITY = listOf(ServiceState.UP, ServiceState.UNKNOWN, ServiceState.DEGRADED, ServiceState.DOWN)
    }
}

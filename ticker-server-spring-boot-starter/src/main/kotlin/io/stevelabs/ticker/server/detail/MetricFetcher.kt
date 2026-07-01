package io.stevelabs.ticker.server.detail

import io.stevelabs.ticker.core.ServiceType
import io.stevelabs.ticker.server.poll.PollProperties
import io.stevelabs.ticker.server.target.Target
import org.slf4j.LoggerFactory
import org.springframework.web.client.RestClient
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/** Resolves a target's curated dashboard. The seam that lets DetailController be tested without HTTP. */
interface MetricSource {
    fun fetch(target: Target): List<ResolvedGroup>

    /**
     * Resolve a single metric value from a SPRING target's actuator.
     * Returns null for non-SPRING targets or if the metric is unavailable.
     * Default returns null so existing test stubs don't require changes.
     */
    fun resolveValue(target: Target, ref: MetricRef, statistic: String): Double? = null

    /**
     * Return a per-[tag]-value breakdown of [metricName] for a SPRING target.
     * Each row carries count, mean latency, and max latency for one tag value.
     * [filter] is an optional set of fixed tags that scope the breakdown (e.g. outcome=CLIENT_ERROR).
     * Default returns emptyList() so existing test stubs compile without changes.
     */
    fun tagBreakdown(target: Target, metricName: String, tag: String, filter: Map<String, String> = emptyMap()): List<TagStat> = emptyList()
}

/**
 * Resolves the curated dashboard against a target's actuator, pulling ONLY whitelisted
 * /actuator/metrics/{name} (guardrail #4). Widget resolutions fan out over virtual threads
 * (the Poller pattern), bounded by the per-call RestClient timeout. Every widget and group is
 * always returned: one whose metric is missing/unreachable comes back with available=false so the
 * UI can show the full catalog with uncollected metrics dimmed rather than silently hidden.
 */
class MetricFetcher(
    private val restClient: RestClient,
    private val properties: DetailProperties,
    private val executor: ExecutorService,
    pollProperties: PollProperties,
) : MetricSource {
    private val log = LoggerFactory.getLogger(MetricFetcher::class.java)
    private val awaitMs = (pollProperties.timeout.toMillis() * 2).coerceAtLeast(1000)

    override fun resolveValue(target: Target, ref: MetricRef, statistic: String): Double? {
        if (target.type != ServiceType.SPRING) return null
        val measurements = fetchMeasurements(target.url, ref) ?: return null
        return statisticValue(measurements, statistic)
    }

    override fun tagBreakdown(target: Target, metricName: String, tag: String, filter: Map<String, String>): List<TagStat> {
        if (target.type != ServiceType.SPRING) return emptyList()
        // Guardrail #4: reject any name that doesn't pass MetricRef validation.
        val filterRef = try { MetricRef(metricName, filter) } catch (_: IllegalArgumentException) { return emptyList() }
        val tagValues = fetchTagValues(target.url, filterRef, tag)
        if (tagValues.isEmpty()) return emptyList()
        val futures = tagValues.map { value ->
            executor.submit<TagStat?> {
                try {
                    val tagRef = MetricRef(metricName, filter + (tag to value))
                    val m = fetchMeasurements(target.url, tagRef) ?: return@submit null
                    val count = m["COUNT"]
                    val totalTime = m["TOTAL_TIME"]
                    val max = m["MAX"]
                    val mean = when {
                        totalTime == null || count == null -> null
                        count > 0 -> totalTime / count
                        else -> null
                    }
                    TagStat(value, count, mean, max)
                } catch (e: Exception) {
                    log.debug("tagBreakdown value {} failed: {}", value, e.javaClass.simpleName)
                    null
                }
            }
        }
        return futures.mapNotNull { future ->
            try {
                future.get(awaitMs, TimeUnit.MILLISECONDS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                future.cancel(true)
                null
            } catch (e: Exception) {
                future.cancel(true)
                log.debug("tagBreakdown future did not complete: {}", e.javaClass.simpleName)
                null
            }
        }.sortedWith(compareBy(nullsLast(reverseOrder<Double>())) { it.count })
    }

    override fun fetch(target: Target): List<ResolvedGroup> {
        if (target.type != ServiceType.SPRING) return emptyList()
        // One GET lists the metrics this target actually exposes. We only fan out GETs for widgets
        // whose metric is present; the rest render dimmed (available=false) with no wasted request —
        // important now the curated catalog is large and most metrics are absent on a given target.
        val present = fetchMetricNames(target.url)
        // No names → actuator metrics unreachable/not exposed: return empty (UI shows the "no metrics"
        // note) rather than a whole catalog of dimmed widgets for a target we can't read at all.
        if (present.isEmpty()) return emptyList()
        val pending = properties.dashboard.map { group ->
            group to group.widgets.map { widget ->
                val future = if (widget.metric in present) executor.submit<ResolvedWidget?> { resolveWidget(target.url, widget) } else null
                widget to future
            }
        }
        return pending.map { (group, pairs) ->
            val widgets = pairs.map { (spec, future) -> future?.let { await(it) } ?: unavailable(spec) }
            ResolvedGroup(group.title, widgets)
        }
    }

    /** Names of the metrics this target exposes (GET /actuator/metrics). Empty set if unreachable. */
    private fun fetchMetricNames(baseUrl: String): Set<String> {
        val body = fetchBody(URI.create("${baseUrl.trimEnd('/')}/actuator/metrics")) ?: return emptySet()
        return (body["names"] as? List<*>).orEmpty().mapNotNull { it?.toString() }.toSet()
    }

    private fun await(future: Future<ResolvedWidget?>): ResolvedWidget? =
        try {
            future.get(awaitMs, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            future.cancel(true)
            null
        } catch (e: Exception) {
            future.cancel(true)
            log.debug("widget resolution did not complete: {}", e.javaClass.simpleName)
            null
        }

    private fun resolveWidget(baseUrl: String, widget: WidgetSpec): ResolvedWidget {
        val measurements = fetchMeasurements(baseUrl, MetricRef(widget.metric, widget.tags))
        val value = measurements?.let { statisticValue(it, widget.statistic) } ?: return unavailable(widget)
        val max = resolveMax(baseUrl, widget)
        return ResolvedWidget(widget.key, widget.label, widget.render, widget.unit, value, max, widget.cumulative, widget.higherIsBetter, widget.perSecond, widget.ratio, available = true)
    }

    /** A placeholder widget for a metric this target does not expose — rendered dimmed by the UI. */
    private fun unavailable(widget: WidgetSpec): ResolvedWidget =
        ResolvedWidget(widget.key, widget.label, widget.render, widget.unit, null, null, widget.cumulative, widget.higherIsBetter, widget.perSecond, widget.ratio, available = false)

    private fun resolveMax(baseUrl: String, widget: WidgetSpec): Double? = when {
        widget.max != null -> fetchMeasurements(baseUrl, widget.max)?.let { statisticValue(it, "VALUE") }
        widget.render == Render.GAUGE && widget.unit == Unit.PERCENT -> 1.0
        else -> null
    }

    private fun statisticValue(measurements: Map<String, Double>, statistic: String): Double? =
        if (statistic == "MEAN") {
            val total = measurements["TOTAL_TIME"]
            val count = measurements["COUNT"]
            when {
                total == null || count == null -> null
                count > 0 -> total / count
                else -> 0.0
            }
        } else {
            measurements[statistic]
        }

    private fun fetchMeasurements(baseUrl: String, ref: MetricRef): Map<String, Double>? {
        return try {
            @Suppress("UNCHECKED_CAST")
            val body = restClient.get().uri(metricUri(baseUrl, ref)).retrieve().body(Map::class.java) as? Map<String, Any?>
                ?: return null
            val measurements = (body["measurements"] as? List<*>).orEmpty().mapNotNull { m ->
                val mm = m as? Map<*, *> ?: return@mapNotNull null
                val stat = mm["statistic"]?.toString() ?: return@mapNotNull null
                val value = (mm["value"] as? Number)?.toDouble() ?: return@mapNotNull null
                stat to value
            }.toMap()
            measurements.ifEmpty { null }
        } catch (e: Exception) {
            log.debug("Metric {} unavailable on {}: {}", ref.name, baseUrl, e.javaClass.simpleName)
            null
        }
    }

    /** ALWAYS {baseUrl}/actuator/metrics/{name}(+ fixed tags). Tag values are URL-encoded (guardrail #4). */
    private fun metricUri(baseUrl: String, ref: MetricRef): URI {
        val query = ref.tags.entries.joinToString("&") { (key, value) ->
            "tag=$key:" + URLEncoder.encode(value, StandardCharsets.UTF_8)
        }
        val suffix = if (query.isEmpty()) "" else "?$query"
        return URI.create("${baseUrl.trimEnd('/')}/actuator/metrics/${ref.name}$suffix")
    }

    /** Fetches the raw JSON body map from [uri]; returns null on any error. */
    private fun fetchBody(uri: URI): Map<String, Any?>? =
        try {
            @Suppress("UNCHECKED_CAST")
            restClient.get().uri(uri).retrieve().body(Map::class.java) as? Map<String, Any?>
        } catch (e: Exception) {
            log.debug("Fetch failed for {}: {}", uri, e.javaClass.simpleName)
            null
        }

    /**
     * Fetches /actuator/metrics/{ref.name} (no query tags) and returns the string values
     * for [tag] found in the `availableTags` array, or emptyList() if absent/unreachable.
     */
    private fun fetchTagValues(baseUrl: String, ref: MetricRef, tag: String): List<String> {
        val body = fetchBody(metricUri(baseUrl, ref)) ?: return emptyList()
        val availableTags = (body["availableTags"] as? List<*>).orEmpty()
        val tagEntry = availableTags.firstOrNull { entry ->
            (entry as? Map<*, *>)?.get("tag")?.toString() == tag
        } as? Map<*, *> ?: return emptyList()
        return (tagEntry["values"] as? List<*>).orEmpty().mapNotNull { it?.toString() }
    }
}

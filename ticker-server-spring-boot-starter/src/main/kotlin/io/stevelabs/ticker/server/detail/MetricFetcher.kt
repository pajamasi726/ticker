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
     * Default returns emptyList() so existing test stubs compile without changes.
     */
    fun tagBreakdown(target: Target, metricName: String, tag: String): List<TagStat> = emptyList()
}

/**
 * Resolves the curated dashboard against a target's actuator, pulling ONLY whitelisted
 * /actuator/metrics/{name} (guardrail #4). Widget resolutions fan out over virtual threads
 * (the Poller pattern), bounded by the per-call RestClient timeout. A widget whose primary
 * metric is missing/unreachable is dropped; a group with no surviving widgets is omitted.
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

    override fun tagBreakdown(target: Target, metricName: String, tag: String): List<TagStat> {
        if (target.type != ServiceType.SPRING) return emptyList()
        // Guardrail #4: reject any name that doesn't pass MetricRef validation.
        val ref = try { MetricRef(metricName) } catch (_: IllegalArgumentException) { return emptyList() }
        val tagValues = fetchTagValues(target.url, ref, tag)
        if (tagValues.isEmpty()) return emptyList()
        val futures = tagValues.map { value ->
            executor.submit<TagStat?> {
                try {
                    val tagRef = MetricRef(metricName, mapOf(tag to value))
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
        // Submit every widget across every group first, so all GETs run concurrently, then collect.
        val pending = properties.dashboard.map { group ->
            group to group.widgets.map { widget ->
                executor.submit<ResolvedWidget?> { resolveWidget(target.url, widget) }
            }
        }
        return pending.mapNotNull { (group, futures) ->
            val widgets = futures.mapNotNull { await(it) }
            if (widgets.isEmpty()) null else ResolvedGroup(group.title, widgets)
        }
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

    private fun resolveWidget(baseUrl: String, widget: WidgetSpec): ResolvedWidget? {
        val measurements = fetchMeasurements(baseUrl, MetricRef(widget.metric, widget.tags)) ?: return null
        val value = statisticValue(measurements, widget.statistic) ?: return null
        val max = resolveMax(baseUrl, widget)
        return ResolvedWidget(widget.key, widget.label, widget.render, widget.unit, value, max, widget.cumulative, widget.higherIsBetter, widget.perSecond, widget.ratio)
    }

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

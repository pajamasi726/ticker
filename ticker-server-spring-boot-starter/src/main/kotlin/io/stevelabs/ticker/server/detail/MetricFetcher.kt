package io.stevelabs.ticker.server.detail

import io.stevelabs.ticker.core.ServiceType
import io.stevelabs.ticker.server.target.Target
import org.slf4j.LoggerFactory
import org.springframework.web.client.RestClient
import java.net.URI

data class MetricValue(val name: String, val tag: String?, val measurements: Map<String, Double>)

/** Fetches a target's metrics. The seam that lets DetailController be tested without HTTP. */
interface MetricSource {
    fun fetch(target: Target): List<MetricValue>
}

/** Pulls ONLY whitelisted /actuator/metrics/{name} from a target (guardrail #4). Per-metric failures are skipped. */
class MetricFetcher(
    private val restClient: RestClient,
    private val properties: DetailProperties,
) : MetricSource {
    private val log = LoggerFactory.getLogger(MetricFetcher::class.java)

    override fun fetch(target: Target): List<MetricValue> {
        if (target.type != ServiceType.SPRING) return emptyList()
        return properties.metrics.mapNotNull { fetchOne(target.url, it) }
    }

    private fun fetchOne(baseUrl: String, spec: MetricSpec): MetricValue? {
        val uri = buildString {
            append(baseUrl.trimEnd('/')).append("/actuator/metrics/").append(spec.name)
            if (spec.tag != null) append("?tag=").append(spec.tag)
        }
        return try {
            @Suppress("UNCHECKED_CAST")
            val body = restClient.get().uri(URI.create(uri)).retrieve().body(Map::class.java) as? Map<String, Any?>
                ?: return null
            val measurements = (body["measurements"] as? List<*>).orEmpty().mapNotNull { m ->
                val mm = m as? Map<*, *> ?: return@mapNotNull null
                val stat = mm["statistic"]?.toString() ?: return@mapNotNull null
                val value = (mm["value"] as? Number)?.toDouble() ?: return@mapNotNull null
                stat to value
            }.toMap()
            if (measurements.isEmpty()) null else MetricValue(spec.name, spec.tag, measurements)
        } catch (e: Exception) {
            log.debug("Metric {} unavailable on {}: {}", spec.name, baseUrl, e.javaClass.simpleName)
            null
        }
    }
}

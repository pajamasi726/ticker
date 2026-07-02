package io.stevelabs.ticker.server

import io.stevelabs.ticker.core.ServiceType
import io.stevelabs.ticker.server.detail.DetailProperties
import io.stevelabs.ticker.server.detail.MetricSource
import io.stevelabs.ticker.server.detail.ServiceDetail
import io.stevelabs.ticker.server.detail.TagStat
import io.stevelabs.ticker.server.state.HealthStateStore
import io.stevelabs.ticker.server.state.ServiceState
import io.stevelabs.ticker.server.target.TargetRegistry
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@RequestMapping("/api/services")
class DetailController(
    private val registry: TargetRegistry,
    private val store: HealthStateStore,
    private val metricSource: MetricSource,
    private val detailProperties: DetailProperties,
) {
    @GetMapping("/{id}/detail")
    fun detail(@PathVariable id: String): ResponseEntity<Any> {
        val target = registry.all().firstOrNull { it.id == id }
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body<Any>(ApiError("TARGET_NOT_FOUND", "No target with id '$id'"))
        val health = store.snapshot(Instant.now()).firstOrNull { it.target.id == id }
        val groups = if (target.type == ServiceType.SPRING) metricSource.fetch(target) else emptyList()
        return ResponseEntity.ok<Any>(
            ServiceDetail(
                id = target.id,
                name = target.name,
                type = target.type,
                state = health?.state ?: ServiceState.UNKNOWN,
                latencyMs = health?.latencyMs,
                sparkline = health?.sparkline ?: emptyList(),
                groups = groups,
                instance = target.instance,
                ip = target.ip,
                url = target.url,
            ),
        )
    }

    @GetMapping("/{id}/metric-breakdown")
    fun metricBreakdown(
        @PathVariable id: String,
        @RequestParam metric: String,
        @RequestParam tag: String,
        @RequestParam(required = false) filter: String?,
    ): ResponseEntity<Any> {
        val target = registry.all().firstOrNull { it.id == id }
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body<Any>(ApiError("SERVICE_NOT_FOUND", "No target with id '$id'"))

        val allowedMetrics = detailProperties.dashboard
            .flatMap { group -> group.widgets }
            .flatMap { widget -> listOfNotNull(widget.metric, widget.max?.name) }
            .toSet()
        if (metric !in allowedMetrics) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body<Any>(ApiError("METRIC_NOT_ALLOWED", "Metric '$metric' is not in the allowed whitelist"))
        }

        val allowedTags = setOf("uri", "method", "status", "outcome")
        if (tag !in allowedTags) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body<Any>(ApiError("TAG_NOT_ALLOWED", "Tag '$tag' is not allowed; permitted: $allowedTags"))
        }

        val filterMap: Map<String, String> = if (filter != null) {
            val sep = filter.indexOf(':')
            val tagKey = if (sep >= 0) filter.substring(0, sep) else ""
            val tagValue = if (sep >= 0) filter.substring(sep + 1) else ""
            if (tagKey !in allowedTags) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body<Any>(ApiError("FILTER_NOT_ALLOWED", "Filter tag '$tagKey' is not allowed; permitted: $allowedTags"))
            }
            mapOf(tagKey to tagValue)
        } else {
            emptyMap()
        }

        val stats: List<TagStat> = metricSource.tagBreakdown(target, metric, tag, filterMap)
        return ResponseEntity.ok<Any>(stats)
    }
}

package io.stevelabs.ticker.server.history

import io.stevelabs.ticker.server.ApiError
import io.stevelabs.ticker.server.detail.DetailProperties
import io.stevelabs.ticker.server.target.TargetRegistry
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/services")
class MetricHistoryController(
    private val registry: TargetRegistry,
    private val repo: MetricHistoryRepository,
    private val detailProperties: DetailProperties,
    private val props: HistoryProperties,
) {
    private val allowedKeys: Set<String> by lazy {
        detailProperties.dashboard.flatMap { it.widgets }.map { it.key }.toSet()
    }

    private val rangeToMillis = mapOf(
        "5m"  to 5L  * 60_000,
        "15m" to 15L * 60_000,
        "1h"  to 60L * 60_000,
        "6h"  to 6L  * 60 * 60_000,
        "24h" to 24L * 60 * 60_000,
        "7d"  to 7L  * 24 * 60 * 60_000,
    )

    @GetMapping("/{id}/metric-history")
    fun metricHistory(
        @PathVariable id: String,
        @RequestParam key: String,
        @RequestParam range: String,
    ): ResponseEntity<Any> {
        registry.all().firstOrNull { it.id == id }
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body<Any>(ApiError("SERVICE_NOT_FOUND", "No target with id '$id'"))

        if (key !in allowedKeys) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body<Any>(ApiError("METRIC_NOT_ALLOWED", "Key '$key' is not a known dashboard widget key"))
        }

        val rangeMs = rangeToMillis[range]
            ?: return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body<Any>(ApiError("BAD_RANGE", "Range '$range' is not valid; allowed: ${rangeToMillis.keys}"))

        val to = System.currentTimeMillis()
        val from = to - rangeMs
        val bucketMs = maxOf(1_000L, rangeMs / props.maxBuckets)
        val points = repo.query(id, key, from, bucketMs)

        return ResponseEntity.ok<Any>(
            mapOf(
                "range"    to range,
                "from"     to from,
                "to"       to to,
                "bucketMs" to bucketMs,
                "points"   to points,
            ),
        )
    }
}

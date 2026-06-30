package io.stevelabs.ticker.server

import io.stevelabs.ticker.core.ServiceType
import io.stevelabs.ticker.server.detail.MetricSource
import io.stevelabs.ticker.server.detail.ServiceDetail
import io.stevelabs.ticker.server.state.HealthStateStore
import io.stevelabs.ticker.server.state.ServiceState
import io.stevelabs.ticker.server.target.TargetRegistry
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@RequestMapping("/api/services")
class DetailController(
    private val registry: TargetRegistry,
    private val store: HealthStateStore,
    private val metricSource: MetricSource,
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
            ),
        )
    }
}

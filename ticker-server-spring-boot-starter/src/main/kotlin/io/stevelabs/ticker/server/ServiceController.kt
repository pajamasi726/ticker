package io.stevelabs.ticker.server

import io.stevelabs.ticker.server.state.HealthStateStore
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/services")
class ServiceController(private val store: HealthStateStore) {

    @GetMapping
    fun list(): List<ServiceView> = store.snapshot().map { th ->
        ServiceView(
            id = th.target.id,
            name = th.target.name,
            instance = th.target.instance,
            type = th.target.type,
            state = th.state,
            source = th.target.source,
            tags = th.target.tags,
            latencyMs = th.latencyMs,
            sparkline = th.sparkline,
        )
    }
}

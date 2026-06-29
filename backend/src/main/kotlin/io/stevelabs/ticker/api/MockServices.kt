package io.stevelabs.ticker.api

import io.stevelabs.ticker.api.dto.ServiceState
import io.stevelabs.ticker.api.dto.ServiceType
import io.stevelabs.ticker.api.dto.ServiceView
import org.springframework.stereotype.Component

/** Hardcoded fixtures for Phase 0 — replaced by the real poller in Phase 1. */
@Component
class MockServices {
    fun all(): List<ServiceView> = listOf(
        ServiceView("payment-api", "payment-api", ServiceType.SPRING, ServiceState.UP,
            listOf("payments"), 42, listOf(40, 44, 41, 39, 43, 42)),
        ServiceView("ledger", "ledger", ServiceType.SPRING, ServiceState.UP,
            listOf("payments", "core"), 58, listOf(55, 60, 57, 59, 58, 56)),
        ServiceView("auth", "auth", ServiceType.SPRING, ServiceState.UP,
            listOf("core"), 33, listOf(31, 35, 34, 32, 33, 33)),
        ServiceView("notification", "notification", ServiceType.SPRING, ServiceState.DEGRADED,
            listOf("messaging"), 210, listOf(120, 150, 180, 200, 260, 210)),
        ServiceView("edge-nginx", "edge-nginx", ServiceType.HTTP, ServiceState.UP,
            listOf("edge"), 12, listOf(11, 13, 12, 12, 14, 12)),
        ServiceView("batch-worker", "batch-worker", ServiceType.SPRING, ServiceState.DOWN,
            listOf("batch"), null, listOf(90, 95, null, null, null, null)),
        ServiceView("reporting", "reporting", ServiceType.HTTP, ServiceState.UNKNOWN,
            listOf("analytics"), null, listOf(null, null, null, null, null, null)),
    )
}

package io.stevelabs.ticker.server.admin

import io.stevelabs.ticker.core.ServiceType
import io.stevelabs.ticker.server.TickerServerProperties
import io.stevelabs.ticker.server.history.HistoryDb
import io.stevelabs.ticker.server.history.HistoryProperties
import io.stevelabs.ticker.server.poll.PollProperties
import io.stevelabs.ticker.server.state.HealthStateStore
import io.stevelabs.ticker.server.target.TargetRegistry
import io.stevelabs.ticker.server.target.TargetSource
import org.springframework.core.env.Environment
import org.springframework.util.StringUtils
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.lang.management.ManagementFactory

/**
 * Read-only collector self-info + target registry for the admin view. Config values that are
 * startup-bound are DISPLAYED here, never edited — mutations live only where a runtime API exists
 * (backup, silence, rules, target removal). Secrets never leave as strings: webhook/public-url
 * presence is reported as booleans (guardrail #5).
 */
@RestController
@RequestMapping("/api/admin")
class AdminController(
    private val server: TickerServerProperties,
    private val poll: PollProperties,
    private val history: HistoryProperties,
    private val registry: TargetRegistry,
    private val store: HealthStateStore,
    private val environment: Environment,
) {

    data class PollInfo(
        val intervalMillis: Long,
        val timeoutMillis: Long,
        val failureThreshold: Int,
        val degradedLatencyMs: Long,
        val stalenessMultiplier: Int,
    )

    data class ServerInfo(
        val basePath: String?,
        val publicUrlConfigured: Boolean,
        val registrationExpiryMillis: Long,
    )

    data class AlertInfo(
        val enabled: Boolean,
        val webhookConfigured: Boolean,
    )

    data class HistoryInfo(val enabled: Boolean, val db: HistoryDb)

    data class AdminInfo(
        val version: String,
        val uptimeMillis: Long,
        val poll: PollInfo,
        val server: ServerInfo,
        val alert: AlertInfo,
        val history: HistoryInfo,
    )

    data class AdminTarget(
        val id: String,
        val name: String,
        val type: ServiceType,
        val url: String,
        val source: TargetSource,
        val instance: String?,
        val ip: String?,
        val lastSeenMillis: Long?,
        val state: String,
    )

    @GetMapping("/info")
    fun info(): AdminInfo = AdminInfo(
        version = AdminController::class.java.`package`?.implementationVersion ?: "dev",
        uptimeMillis = ManagementFactory.getRuntimeMXBean().uptime,
        poll = PollInfo(
            intervalMillis = poll.interval.toMillis(),
            timeoutMillis = poll.timeout.toMillis(),
            failureThreshold = poll.failureThreshold,
            degradedLatencyMs = poll.degradedLatencyMs,
            stalenessMultiplier = poll.stalenessMultiplier,
        ),
        server = ServerInfo(
            basePath = server.basePath.ifBlank { null },
            publicUrlConfigured = !server.publicUrl.isNullOrBlank(),
            registrationExpiryMillis = server.registrationExpiry.toMillis(),
        ),
        alert = AlertInfo(
            enabled = environment.getProperty("ticker.alert.enabled", Boolean::class.java, false),
            webhookConfigured = StringUtils.hasText(environment.getProperty("ticker.alert.slack-webhook-url")),
        ),
        history = HistoryInfo(enabled = history.enabled, db = history.db),
    )

    @GetMapping("/targets")
    fun targets(): List<AdminTarget> {
        val states = store.snapshot().associate { it.target.id to it.state.name }
        return registry.all().map { t ->
            AdminTarget(
                id = t.id,
                name = t.name,
                type = t.type,
                url = t.url,
                source = t.source,
                instance = t.instance,
                ip = t.ip,
                lastSeenMillis = if (t.source == TargetSource.REGISTERED) registry.lastSeenMillis(t.id) else null,
                state = states[t.id] ?: "UNKNOWN",
            )
        }
    }
}

package io.stevelabs.ticker.server.alert

import io.stevelabs.ticker.server.detail.Unit
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory store for metric-alert rules (seeded from defaults or a custom list) and the recent-fires log.
 * Thread-safe: rules are stored in a ConcurrentHashMap; the fires log is synchronised.
 */
class MetricAlertStore(seed: List<MetricAlertRule> = MetricAlertRule.DEFAULTS) {
    private val order = seed.map { it.key }
    private val rules: ConcurrentHashMap<String, MetricAlertRule> =
        ConcurrentHashMap<String, MetricAlertRule>().also { map ->
            seed.forEach { map[it.key] = it }
        }

    /** Ordered snapshot of all rules (insertion order of seed). */
    fun all(): List<MetricAlertRule> = order.mapNotNull { rules[it] }

    /**
     * Update only the mutable fields (enabled, threshold, cooldownSeconds, forSeconds).
     * Returns the updated rule, or null if the key is unknown.
     * Throws [IllegalArgumentException] if a PERCENT rule is given a threshold outside [0,1],
     * or if forSeconds is negative.
     */
    fun update(
        key: String,
        enabled: Boolean?,
        threshold: Double?,
        cooldownSeconds: Long?,
        forSeconds: Long? = null,
    ): MetricAlertRule? {
        val current = rules[key] ?: return null
        if (threshold != null && current.unit == Unit.PERCENT && (threshold < 0.0 || threshold > 1.0)) {
            throw IllegalArgumentException(
                "Threshold for PERCENT rule '$key' must be in [0,1], got $threshold",
            )
        }
        if (forSeconds != null && forSeconds < 0) {
            throw IllegalArgumentException("forSeconds must be >= 0, got $forSeconds")
        }
        val updated = current.copy(
            enabled = enabled ?: current.enabled,
            threshold = threshold ?: current.threshold,
            cooldownSeconds = cooldownSeconds ?: current.cooldownSeconds,
            forSeconds = forSeconds ?: current.forSeconds,
        )
        rules[key] = updated
        return updated
    }

    // --- Recent-fires log (bounded, newest-first) ------------------------------------

    private val fires: MutableList<AlertFire> = Collections.synchronizedList(ArrayDeque())
    private val maxFires = 50

    fun record(fire: AlertFire) {
        synchronized(fires) {
            fires.add(0, fire)
            while (fires.size > maxFires) fires.removeAt(fires.size - 1)
        }
    }

    /** Newest-first snapshot. */
    fun recent(): List<AlertFire> = synchronized(fires) { fires.toList() }
}

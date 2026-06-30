package io.stevelabs.ticker.server.alert

import io.stevelabs.ticker.server.detail.Unit
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory store for metric-alert rules (seeded from defaults) and the recent-fires log.
 * Thread-safe: rules are stored in a ConcurrentHashMap; the fires log is synchronised.
 */
class MetricAlertStore {
    // LinkedHashMap wrapped for insertion-order iteration, stored as a ConcurrentHashMap value by key
    private val rules: ConcurrentHashMap<String, MetricAlertRule> =
        ConcurrentHashMap<String, MetricAlertRule>().also { map ->
            MetricAlertRule.DEFAULTS.forEach { map[it.key] = it }
        }

    /** Ordered snapshot of all rules (insertion order of DEFAULTS). */
    fun all(): List<MetricAlertRule> = MetricAlertRule.DEFAULTS.mapNotNull { rules[it.key] }

    /**
     * Update only the mutable fields (enabled, threshold, cooldownSeconds).
     * Returns the updated rule, or null if the key is unknown.
     * Throws [IllegalArgumentException] if a PERCENT rule is given a threshold outside [0,1].
     */
    fun update(
        key: String,
        enabled: Boolean?,
        threshold: Double?,
        cooldownSeconds: Long?,
    ): MetricAlertRule? {
        val current = rules[key] ?: return null
        if (threshold != null && current.unit == Unit.PERCENT && (threshold < 0.0 || threshold > 1.0)) {
            throw IllegalArgumentException(
                "Threshold for PERCENT rule '$key' must be in [0,1], got $threshold",
            )
        }
        val updated = current.copy(
            enabled = enabled ?: current.enabled,
            threshold = threshold ?: current.threshold,
            cooldownSeconds = cooldownSeconds ?: current.cooldownSeconds,
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

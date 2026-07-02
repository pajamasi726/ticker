package io.stevelabs.ticker.server.config

import io.stevelabs.ticker.core.ServiceType
import io.stevelabs.ticker.server.alert.MetricAlertRule
import io.stevelabs.ticker.server.target.TargetDefinition
import org.slf4j.LoggerFactory

/** A @Bean that tweaks Ticker's configuration in code after YAML binding. Multiple beans compose in @Order. */
fun interface TickerConfigurer {
    fun configure(ticker: TickerConfig)
}

/** Mutable config seeded from properties/defaults, mutated by TickerConfigurers, then frozen into beans. */
class TickerConfig internal constructor(
    targets: List<TargetDefinition>,
    alertRules: List<MetricAlertRule>,
) {
    private val log = LoggerFactory.getLogger(TickerConfig::class.java)
    private val targetList = targets.toMutableList()
    private val alertMap = LinkedHashMap<String, MetricAlertRule>().apply { alertRules.forEach { put(it.key, it) } }

    /** Add a monitored target (equivalent to a ticker.targets[] entry). Ignored if the name already exists (YAML wins). */
    fun addTarget(name: String, type: ServiceType, url: String, tags: List<String> = emptyList()): TickerConfig {
        if (targetList.none { it.name == name }) targetList += TargetDefinition(name, type, url, tags)
        return this
    }

    /** Add or replace an alert rule by key (full definition). */
    fun putAlertRule(rule: MetricAlertRule): TickerConfig { alertMap[rule.key] = rule; return this }

    /** Tweak an existing alert rule's mutable fields; a typo'd key is a loud no-op (WARN with the valid keys). */
    fun configureAlert(
        key: String,
        threshold: Double? = null,
        enabled: Boolean? = null,
        cooldownSeconds: Long? = null,
        forSeconds: Long? = null,
    ): TickerConfig {
        val r = alertMap[key] ?: run {
            log.warn("configureAlert('{}') matched no alert rule — nothing changed. Valid keys: {}", key, alertMap.keys)
            return this
        }
        alertMap[key] = r.copy(
            threshold = threshold ?: r.threshold,
            enabled = enabled ?: r.enabled,
            cooldownSeconds = cooldownSeconds ?: r.cooldownSeconds,
            forSeconds = forSeconds ?: r.forSeconds,
        )
        return this
    }

    internal fun targets(): List<TargetDefinition> = targetList.toList()
    internal fun alertRules(): List<MetricAlertRule> = alertMap.values.toList()
}

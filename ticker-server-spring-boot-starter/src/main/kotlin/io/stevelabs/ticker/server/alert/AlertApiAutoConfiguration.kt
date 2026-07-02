package io.stevelabs.ticker.server.alert

import io.stevelabs.ticker.server.TickerServerAutoConfiguration
import io.stevelabs.ticker.server.config.TickerConfig
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.context.annotation.Bean

/**
 * The alert rule store + read/edit API, registered whenever the collector itself is on —
 * NOT gated by `ticker.alert.enabled`. Rules are not alert-only data: the dashboard's
 * value-driven severity colouring compares live values against these thresholds, and the UI
 * polls `/api/alerts/rules` + `/api/alerts/recent` unconditionally (an alerts-off collector
 * must answer them, not 404). `ticker.alert.enabled` only gates evaluation + dispatch
 * ([AlertAutoConfiguration]); edits made while alerting is off apply if it is enabled later.
 */
@AutoConfiguration
@AutoConfigureAfter(TickerServerAutoConfiguration::class)
@ConditionalOnBean(TickerConfig::class)
class AlertApiAutoConfiguration {

    @Bean
    fun metricAlertStore(config: TickerConfig): MetricAlertStore = MetricAlertStore(config.alertRules())

    /** Shared deploy/maintenance silence window — gates BOTH incident and metric alert dispatch. */
    @Bean
    fun alertSilence(): AlertSilence = AlertSilence()

    @Bean
    fun metricAlertController(rules: MetricAlertStore, silence: AlertSilence): MetricAlertController =
        MetricAlertController(rules, silence)
}

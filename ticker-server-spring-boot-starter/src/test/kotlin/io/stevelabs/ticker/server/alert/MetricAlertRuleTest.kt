package io.stevelabs.ticker.server.alert

import io.stevelabs.ticker.server.detail.MetricRef
import io.stevelabs.ticker.server.detail.Unit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MetricAlertRuleTest {

    private fun gtRule(threshold: Double) =
        MetricAlertRule("k", "L", MetricRef("a.metric"), comparator = Comparator.GT, threshold = threshold, unit = Unit.PERCENT)

    private fun ltRule(threshold: Double) =
        MetricAlertRule("k", "L", MetricRef("a.metric"), comparator = Comparator.LT, threshold = threshold, unit = Unit.PERCENT)

    // --- breaches ---

    @Test fun `GT breaches when quantity exceeds threshold`() {
        assertThat(gtRule(0.80).breaches(0.81)).isTrue()
    }

    @Test fun `GT does not breach when quantity equals threshold`() {
        assertThat(gtRule(0.80).breaches(0.80)).isFalse()
    }

    @Test fun `GT does not breach when quantity is below threshold`() {
        assertThat(gtRule(0.80).breaches(0.50)).isFalse()
    }

    @Test fun `LT breaches when quantity is below threshold`() {
        assertThat(ltRule(0.10).breaches(0.05)).isTrue()
    }

    @Test fun `LT does not breach when quantity equals threshold`() {
        assertThat(ltRule(0.10).breaches(0.10)).isFalse()
    }

    @Test fun `LT does not breach when quantity is above threshold`() {
        assertThat(ltRule(0.10).breaches(0.50)).isFalse()
    }

    @Test fun `null quantity never breaches`() {
        assertThat(gtRule(0.80).breaches(null)).isFalse()
        assertThat(ltRule(0.10).breaches(null)).isFalse()
    }

    // --- DEFAULTS ---

    @Test fun `DEFAULTS contains exactly the 6 specified keys`() {
        val keys = MetricAlertRule.DEFAULTS.map { it.key }
        assertThat(keys).containsExactly(
            "cpu-process",
            "cpu-system",
            "heap-used",
            "disk-free",
            "gc-overhead",
            "files-open",
        )
    }

    @Test fun `every DEFAULTS metric name is whitelist-safe (guardrail 4)`() {
        val metricNamePattern = Regex("^[a-zA-Z][a-zA-Z0-9._-]*$")
        for (rule in MetricAlertRule.DEFAULTS) {
            assertThat(rule.metric.name).matches(metricNamePattern.toPattern())
            rule.over?.let { assertThat(it.name).matches(metricNamePattern.toPattern()) }
        }
    }

    @Test fun `all DEFAULTS rules are PERCENT unit`() {
        assertThat(MetricAlertRule.DEFAULTS).allSatisfy { assertThat(it.unit).isEqualTo(Unit.PERCENT) }
    }

    // --- forSeconds ---

    @Test fun `forSeconds default is 0`() {
        assertThat(gtRule(0.80).forSeconds).isEqualTo(0L)
    }

    @Test fun `DEFAULTS have expected forSeconds per key`() {
        val byKey = MetricAlertRule.DEFAULTS.associateBy { it.key }
        assertThat(byKey.getValue("cpu-process").forSeconds).isEqualTo(30L)
        assertThat(byKey.getValue("cpu-system").forSeconds).isEqualTo(30L)
        assertThat(byKey.getValue("heap-used").forSeconds).isEqualTo(60L)
        assertThat(byKey.getValue("gc-overhead").forSeconds).isEqualTo(60L)
        assertThat(byKey.getValue("disk-free").forSeconds).isEqualTo(0L)
        assertThat(byKey.getValue("files-open").forSeconds).isEqualTo(0L)
    }
}

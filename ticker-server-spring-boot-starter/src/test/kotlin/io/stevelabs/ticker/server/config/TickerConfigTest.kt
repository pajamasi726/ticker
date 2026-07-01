package io.stevelabs.ticker.server.config

import io.stevelabs.ticker.core.ServiceType
import io.stevelabs.ticker.server.alert.Comparator
import io.stevelabs.ticker.server.alert.MetricAlertRule
import io.stevelabs.ticker.server.detail.MetricRef
import io.stevelabs.ticker.server.detail.Unit
import io.stevelabs.ticker.server.target.TargetDefinition
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TickerConfigTest {

    private fun aTarget(name: String) = TargetDefinition(name, ServiceType.HTTP, "http://example.com/$name")

    private fun aRule(key: String) = MetricAlertRule(
        key = key,
        label = key,
        metric = MetricRef("some.metric"),
        comparator = Comparator.GT,
        threshold = 0.5,
        unit = Unit.PERCENT,
    )

    // --- addTarget ---

    @Test fun `addTarget appends a new target`() {
        val config = TickerConfig(emptyList(), emptyList())
        config.addTarget("x", ServiceType.HTTP, "http://x.example.com")
        assertThat(config.targets().map { it.name }).containsExactly("x")
    }

    @Test fun `addTarget with existing name is ignored (YAML wins)`() {
        val config = TickerConfig(listOf(aTarget("existing")), emptyList())
        config.addTarget("existing", ServiceType.SPRING, "http://other.example.com")
        assertThat(config.targets()).hasSize(1)
        assertThat(config.targets().first().type).isEqualTo(ServiceType.HTTP) // original, not replaced
    }

    @Test fun `addTarget returns TickerConfig for chaining`() {
        val config = TickerConfig(emptyList(), emptyList())
        val returned = config.addTarget("x", ServiceType.HTTP, "http://x.example.com")
        assertThat(returned).isSameAs(config)
    }

    // --- putAlertRule ---

    @Test fun `putAlertRule adds a new rule`() {
        val config = TickerConfig(emptyList(), emptyList())
        config.putAlertRule(aRule("my-rule"))
        assertThat(config.alertRules().map { it.key }).containsExactly("my-rule")
    }

    @Test fun `putAlertRule replaces an existing rule by key`() {
        val config = TickerConfig(emptyList(), listOf(aRule("my-rule")))
        config.putAlertRule(aRule("my-rule").copy(threshold = 0.9))
        assertThat(config.alertRules()).hasSize(1)
        assertThat(config.alertRules().first().threshold).isEqualTo(0.9)
    }

    // --- configureAlert ---

    @Test fun `configureAlert changes threshold and forSeconds of an existing key`() {
        val config = TickerConfig(emptyList(), listOf(aRule("r1")))
        config.configureAlert("r1", threshold = 0.9, forSeconds = 30)
        val r = config.alertRules().first()
        assertThat(r.threshold).isEqualTo(0.9)
        assertThat(r.forSeconds).isEqualTo(30L)
    }

    @Test fun `configureAlert changes enabled of an existing key`() {
        val config = TickerConfig(emptyList(), listOf(aRule("r1")))
        config.configureAlert("r1", enabled = false)
        assertThat(config.alertRules().first().enabled).isFalse()
    }

    @Test fun `configureAlert changes cooldownSeconds of an existing key`() {
        val config = TickerConfig(emptyList(), listOf(aRule("r1")))
        config.configureAlert("r1", cooldownSeconds = 600)
        assertThat(config.alertRules().first().cooldownSeconds).isEqualTo(600L)
    }

    @Test fun `configureAlert is a no-op for an unknown key`() {
        val config = TickerConfig(emptyList(), emptyList())
        // must not throw
        config.configureAlert("no-such-key", threshold = 0.5)
        assertThat(config.alertRules()).isEmpty()
    }

    @Test fun `configureAlert with all-null patch leaves rule unchanged`() {
        val config = TickerConfig(emptyList(), listOf(aRule("r1")))
        val before = config.alertRules().first()
        config.configureAlert("r1")
        assertThat(config.alertRules().first()).isEqualTo(before)
    }

    // --- targets() / alertRules() snapshots ---

    @Test fun `targets() reflects all additions`() {
        val config = TickerConfig(listOf(aTarget("yaml-target")), emptyList())
        config.addTarget("code-target", ServiceType.SPRING, "http://code.example.com")
        assertThat(config.targets().map { it.name }).containsExactly("yaml-target", "code-target")
    }

    @Test fun `alertRules() reflects mutations from configureAlert`() {
        val config = TickerConfig(emptyList(), listOf(aRule("r1"), aRule("r2")))
        config.configureAlert("r1", enabled = false)
        assertThat(config.alertRules().first { it.key == "r1" }.enabled).isFalse()
        assertThat(config.alertRules().first { it.key == "r2" }.enabled).isTrue()
    }

    @Test fun `targets() returns a snapshot (modifications to returned list do not affect config)`() {
        val config = TickerConfig(listOf(aTarget("t1")), emptyList())
        val snapshot = config.targets().toMutableList()
        snapshot.clear()
        assertThat(config.targets()).hasSize(1)
    }
}

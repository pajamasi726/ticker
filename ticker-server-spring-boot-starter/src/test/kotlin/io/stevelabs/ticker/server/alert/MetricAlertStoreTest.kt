package io.stevelabs.ticker.server.alert

import io.stevelabs.ticker.server.detail.Unit
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant

class MetricAlertStoreTest {

    private fun store() = MetricAlertStore()

    // --- seeding ---

    @Test fun `seeded from DEFAULTS in insertion order`() {
        val store = store()
        assertThat(store.all().map { it.key }).containsExactlyElementsOf(MetricAlertRule.DEFAULTS.map { it.key })
    }

    // --- update ---

    @Test fun `update mutates only enabled, threshold, and cooldownSeconds`() {
        val store = store()
        val original = store.all().first { it.key == "cpu-process" }

        val updated = store.update("cpu-process", enabled = false, threshold = 0.70, cooldownSeconds = 600)!!

        assertThat(updated.enabled).isFalse()
        assertThat(updated.threshold).isEqualTo(0.70)
        assertThat(updated.cooldownSeconds).isEqualTo(600)
        // immutable fields unchanged
        assertThat(updated.key).isEqualTo(original.key)
        assertThat(updated.metric).isEqualTo(original.metric)
        assertThat(updated.comparator).isEqualTo(original.comparator)
        assertThat(updated.unit).isEqualTo(original.unit)
    }

    @Test fun `update with all-null patch leaves rule unchanged`() {
        val store = store()
        val original = store.all().first { it.key == "cpu-process" }
        val patched = store.update("cpu-process", null, null, null)!!
        assertThat(patched).isEqualTo(original)
    }

    @Test fun `update returns null for unknown key`() {
        assertThat(store().update("no-such-key", true, null, null)).isNull()
    }

    @Test fun `threshold above 1 on a PERCENT rule throws IllegalArgumentException`() {
        assertThatThrownBy { store().update("cpu-process", null, 1.5, null) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test fun `threshold below 0 on a PERCENT rule throws IllegalArgumentException`() {
        assertThatThrownBy { store().update("cpu-process", null, -0.1, null) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test fun `threshold exactly 0 and 1 are valid for PERCENT rules`() {
        val store = store()
        assertThat(store.update("cpu-process", null, 0.0, null)).isNotNull()
        assertThat(store.update("cpu-process", null, 1.0, null)).isNotNull()
    }

    @Test fun `update sets forSeconds`() {
        val store = store()
        val updated = store.update("cpu-process", null, null, null, forSeconds = 120)!!
        assertThat(updated.forSeconds).isEqualTo(120L)
    }

    @Test fun `negative forSeconds throws IllegalArgumentException`() {
        assertThatThrownBy { store().update("cpu-process", null, null, null, forSeconds = -1) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    // --- recent-fires bounded log ---

    @Test fun `recent fires are returned newest first`() {
        val store = store()
        val t1 = Instant.ofEpochSecond(1000)
        val t2 = Instant.ofEpochSecond(2000)
        store.record(fire("k1", t1))
        store.record(fire("k2", t2))
        val recent = store.recent()
        assertThat(recent[0].at).isEqualTo(t2)
        assertThat(recent[1].at).isEqualTo(t1)
    }

    @Test fun `recent fires are capped at 50`() {
        val store = store()
        repeat(60) { i -> store.record(fire("k$i", Instant.ofEpochSecond(i.toLong()))) }
        assertThat(store.recent()).hasSize(50)
    }

    @Test fun `oldest fires are dropped when cap is exceeded`() {
        val store = store()
        repeat(55) { i -> store.record(fire("k$i", Instant.ofEpochSecond(i.toLong()))) }
        // the 50 newest should be keys k5..k54 (newest first = k54)
        val recent = store.recent()
        assertThat(recent.first().ruleKey).isEqualTo("k54")
        assertThat(recent.last().ruleKey).isEqualTo("k5")
    }

    @Test fun `empty store returns empty recent list`() {
        assertThat(store().recent()).isEmpty()
    }

    private fun fire(key: String, at: Instant) = AlertFire(
        targetId = "t1",
        targetName = "Target One",
        ruleKey = key,
        label = "Some metric",
        value = 0.9,
        threshold = 0.8,
        unit = Unit.PERCENT,
        at = at,
    )
}

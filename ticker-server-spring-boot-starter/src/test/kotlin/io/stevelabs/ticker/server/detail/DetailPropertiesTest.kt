package io.stevelabs.ticker.server.detail

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DetailPropertiesTest {

    @Test fun `default dashboard exposes the expected groups in order`() {
        val titles = DetailProperties().dashboard.map { it.title }
        assertThat(titles).containsExactly(
            "Basic",
            "JVM Memory (heap)",
            "JVM Memory (non-heap)",
            "GC",
            "Threads",
            "Classes & HTTP",
            "Logback",
            "Data Sources",
            "Web",
            "Scheduled Tasks",
        )
    }

    @Test fun `heap-used is a BYTES gauge paired with the heap-max metric`() {
        val widget = DetailProperties().dashboard.flatMap { it.widgets }.first { it.key == "heap-used" }
        assertThat(widget.metric).isEqualTo("jvm.memory.used")
        assertThat(widget.tags).containsEntry("area", "heap")
        assertThat(widget.render).isEqualTo(Render.GAUGE)
        assertThat(widget.unit).isEqualTo(Unit.BYTES)
        assertThat(widget.statistic).isEqualTo("VALUE")
        assertThat(widget.max).isEqualTo(MetricRef("jvm.memory.max", mapOf("area" to "heap")))
    }

    @Test fun `http request count is a cumulative COUNT chart`() {
        val widget = DetailProperties().dashboard.flatMap { it.widgets }.first { it.key == "http-requests" }
        assertThat(widget.statistic).isEqualTo("COUNT")
        assertThat(widget.cumulative).isTrue()
        assertThat(widget.render).isEqualTo(Render.CHART)
    }

    @Test fun `every default widget metric name is whitelist-safe (guardrail 4)`() {
        val names = DetailProperties().dashboard.flatMap { it.widgets }.flatMap { listOf(it.metric) + listOfNotNull(it.max?.name) }
        assertThat(names).allSatisfy { assertThat(it).matches("^[a-zA-Z][a-zA-Z0-9._-]*$") }
    }

    @Test fun `MetricRef rejects path-traversal names (guardrail 4)`() {
        assertThrows<IllegalArgumentException> { MetricRef("../env") }
        assertThrows<IllegalArgumentException> { MetricRef("a/b") }
    }

    @Test fun `WidgetSpec rejects path-traversal metric names (guardrail 4)`() {
        assertThrows<IllegalArgumentException> {
            WidgetSpec("k", "l", "../configprops", render = Render.NUMBER, unit = Unit.COUNT)
        }
    }
}

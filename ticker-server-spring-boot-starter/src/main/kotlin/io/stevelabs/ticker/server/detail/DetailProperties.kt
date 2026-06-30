package io.stevelabs.ticker.server.detail

import org.springframework.boot.context.properties.ConfigurationProperties

enum class Render { GAUGE, CHART, NUMBER }

enum class Unit { BYTES, PERCENT, COUNT, SECONDS, MILLIS, TIMESTAMP }

/** A metric name + fixed tag selectors. Guardrail #4: names must look like Micrometer names. */
data class MetricRef(val name: String, val tags: Map<String, String> = emptyMap()) {
    init {
        require(name.matches(METRIC_NAME)) { "Invalid ticker.detail metric name (must match a Micrometer metric name): '$name'" }
    }
}

/**
 * One dashboard widget. `metric` + `tags` select the primary series; `statistic` picks the
 * measurement to read ("VALUE"/"COUNT"/"MAX"/"ACTIVE_TASKS", or derived "MEAN" = TOTAL_TIME/COUNT).
 * `cumulative` marks monotonic counters (the frontend charts the per-poll delta as a live rate).
 * `max` is an optional gauge denominator (its own metric+tags).
 */
data class WidgetSpec(
    val key: String,
    val label: String,
    val metric: String,
    val tags: Map<String, String> = emptyMap(),
    val statistic: String = "VALUE",
    val render: Render,
    val unit: Unit,
    val cumulative: Boolean = false,
    val max: MetricRef? = null,
) {
    init {
        require(metric.matches(METRIC_NAME)) { "Invalid ticker.detail metric name (must match a Micrometer metric name): '$metric'" }
    }
}

data class GroupSpec(val title: String, val widgets: List<WidgetSpec>)

@ConfigurationProperties(prefix = "ticker.detail")
data class DetailProperties(
    val dashboard: List<GroupSpec> = DEFAULT_DASHBOARD,
)

private val METRIC_NAME = Regex("^[a-zA-Z][a-zA-Z0-9._-]*$")

/**
 * The curated default dashboard — parity with the common pre-built Grafana Spring Boot dashboards.
 * The ONE place to add/remove widgets. Counters that grow forever are `cumulative = true` so the
 * frontend charts their per-poll delta. Groups whose widgets are all absent on a target are omitted.
 */
private val DEFAULT_DASHBOARD: List<GroupSpec> = listOf(
    GroupSpec(
        "Basic",
        listOf(
            WidgetSpec("uptime", "Uptime", "process.uptime", render = Render.NUMBER, unit = Unit.SECONDS),
            WidgetSpec("started", "Started", "process.start.time", render = Render.NUMBER, unit = Unit.TIMESTAMP),
            WidgetSpec("cpu-process", "CPU (process)", "process.cpu.usage", render = Render.GAUGE, unit = Unit.PERCENT),
            WidgetSpec("cpu-system", "CPU (system)", "system.cpu.usage", render = Render.CHART, unit = Unit.PERCENT),
            WidgetSpec("load-1m", "Load avg (1m)", "system.load.average.1m", render = Render.CHART, unit = Unit.COUNT),
            WidgetSpec("cpu-count", "CPUs", "system.cpu.count", render = Render.NUMBER, unit = Unit.COUNT),
            WidgetSpec("files-open", "Open files", "process.files.open", render = Render.GAUGE, unit = Unit.COUNT, max = MetricRef("process.files.max")),
            WidgetSpec("disk-free", "Disk free", "disk.free", render = Render.GAUGE, unit = Unit.BYTES, max = MetricRef("disk.total")),
        ),
    ),
    GroupSpec(
        "JVM Memory (heap)",
        listOf(
            WidgetSpec("heap-used", "Heap used", "jvm.memory.used", tags = mapOf("area" to "heap"), render = Render.GAUGE, unit = Unit.BYTES, max = MetricRef("jvm.memory.max", mapOf("area" to "heap"))),
            WidgetSpec("heap-eden", "G1 Eden", "jvm.memory.used", tags = mapOf("id" to "G1 Eden Space"), render = Render.CHART, unit = Unit.BYTES),
            WidgetSpec("heap-old", "G1 Old Gen", "jvm.memory.used", tags = mapOf("id" to "G1 Old Gen"), render = Render.CHART, unit = Unit.BYTES),
            WidgetSpec("heap-survivor", "G1 Survivor", "jvm.memory.used", tags = mapOf("id" to "G1 Survivor Space"), render = Render.CHART, unit = Unit.BYTES),
        ),
    ),
    GroupSpec(
        "JVM Memory (non-heap)",
        listOf(
            WidgetSpec("nonheap-used", "Non-heap used", "jvm.memory.used", tags = mapOf("area" to "nonheap"), render = Render.GAUGE, unit = Unit.BYTES, max = MetricRef("jvm.memory.committed", mapOf("area" to "nonheap"))),
            WidgetSpec("nonheap-metaspace", "Metaspace", "jvm.memory.used", tags = mapOf("id" to "Metaspace"), render = Render.CHART, unit = Unit.BYTES),
            WidgetSpec("nonheap-compressed-class", "Compressed Class", "jvm.memory.used", tags = mapOf("id" to "Compressed Class Space"), render = Render.CHART, unit = Unit.BYTES),
            WidgetSpec("nonheap-code-cache", "Code cache", "jvm.memory.used", tags = mapOf("id" to "CodeHeap 'profiled nmethods'"), render = Render.CHART, unit = Unit.BYTES),
            WidgetSpec("buffers", "Buffers", "jvm.buffer.memory.used", render = Render.CHART, unit = Unit.BYTES),
        ),
    ),
    GroupSpec(
        "GC",
        listOf(
            WidgetSpec("gc-pause-count", "GC pauses", "jvm.gc.pause", statistic = "COUNT", render = Render.CHART, unit = Unit.COUNT, cumulative = true),
            WidgetSpec("gc-pause-max", "GC pause max", "jvm.gc.pause", statistic = "MAX", render = Render.CHART, unit = Unit.SECONDS),
            WidgetSpec("gc-allocated", "Allocated", "jvm.gc.memory.allocated", statistic = "COUNT", render = Render.CHART, unit = Unit.BYTES, cumulative = true),
            WidgetSpec("gc-promoted", "Promoted", "jvm.gc.memory.promoted", statistic = "COUNT", render = Render.CHART, unit = Unit.BYTES, cumulative = true),
            WidgetSpec("gc-live-data", "Live data", "jvm.gc.live.data.size", render = Render.NUMBER, unit = Unit.BYTES),
            WidgetSpec("gc-max-data", "Max data", "jvm.gc.max.data.size", render = Render.NUMBER, unit = Unit.BYTES),
            WidgetSpec("gc-overhead", "GC overhead", "jvm.gc.overhead", render = Render.GAUGE, unit = Unit.PERCENT),
        ),
    ),
    GroupSpec(
        "Threads",
        listOf(
            WidgetSpec("threads-live", "Live", "jvm.threads.live", render = Render.CHART, unit = Unit.COUNT),
            WidgetSpec("threads-daemon", "Daemon", "jvm.threads.daemon", render = Render.NUMBER, unit = Unit.COUNT),
            WidgetSpec("threads-peak", "Peak", "jvm.threads.peak", render = Render.NUMBER, unit = Unit.COUNT),
            WidgetSpec("threads-runnable", "Runnable", "jvm.threads.states", tags = mapOf("state" to "runnable"), render = Render.CHART, unit = Unit.COUNT),
            WidgetSpec("threads-blocked", "Blocked", "jvm.threads.states", tags = mapOf("state" to "blocked"), render = Render.CHART, unit = Unit.COUNT),
            WidgetSpec("threads-waiting", "Waiting", "jvm.threads.states", tags = mapOf("state" to "waiting"), render = Render.CHART, unit = Unit.COUNT),
            WidgetSpec("threads-timed-waiting", "Timed-waiting", "jvm.threads.states", tags = mapOf("state" to "timed-waiting"), render = Render.CHART, unit = Unit.COUNT),
        ),
    ),
    GroupSpec(
        "Classes & HTTP",
        listOf(
            WidgetSpec("classes-loaded", "Classes loaded", "jvm.classes.loaded", render = Render.NUMBER, unit = Unit.COUNT),
            WidgetSpec("compilation-time", "Compilation time", "jvm.compilation.time", render = Render.NUMBER, unit = Unit.MILLIS),
            WidgetSpec("http-requests", "HTTP requests", "http.server.requests", statistic = "COUNT", render = Render.CHART, unit = Unit.COUNT, cumulative = true),
            WidgetSpec("http-latency-avg", "Avg latency", "http.server.requests", statistic = "MEAN", render = Render.CHART, unit = Unit.SECONDS),
            WidgetSpec("http-latency-max", "Max latency", "http.server.requests", statistic = "MAX", render = Render.CHART, unit = Unit.SECONDS),
            WidgetSpec("http-active", "Active requests", "http.server.requests.active", statistic = "ACTIVE_TASKS", render = Render.NUMBER, unit = Unit.COUNT),
            WidgetSpec("http-success", "Success", "http.server.requests", tags = mapOf("outcome" to "SUCCESS"), statistic = "COUNT", render = Render.CHART, unit = Unit.COUNT, cumulative = true),
            WidgetSpec("http-client-error", "Client error", "http.server.requests", tags = mapOf("outcome" to "CLIENT_ERROR"), statistic = "COUNT", render = Render.CHART, unit = Unit.COUNT, cumulative = true),
            WidgetSpec("http-server-error", "Server error", "http.server.requests", tags = mapOf("outcome" to "SERVER_ERROR"), statistic = "COUNT", render = Render.CHART, unit = Unit.COUNT, cumulative = true),
        ),
    ),
    GroupSpec(
        "Logback",
        listOf(
            WidgetSpec("log-error", "Error", "logback.events", tags = mapOf("level" to "error"), statistic = "COUNT", render = Render.CHART, unit = Unit.COUNT, cumulative = true),
            WidgetSpec("log-warn", "Warn", "logback.events", tags = mapOf("level" to "warn"), statistic = "COUNT", render = Render.CHART, unit = Unit.COUNT, cumulative = true),
            WidgetSpec("log-info", "Info", "logback.events", tags = mapOf("level" to "info"), statistic = "COUNT", render = Render.CHART, unit = Unit.COUNT, cumulative = true),
        ),
    ),
    GroupSpec(
        "Data Sources",
        listOf(
            WidgetSpec("hikari-active", "Active", "hikaricp.connections.active", render = Render.CHART, unit = Unit.COUNT, max = MetricRef("hikaricp.connections.max")),
            WidgetSpec("hikari-idle", "Idle", "hikaricp.connections.idle", render = Render.CHART, unit = Unit.COUNT),
            WidgetSpec("hikari-pending", "Pending", "hikaricp.connections.pending", render = Render.CHART, unit = Unit.COUNT),
        ),
    ),
    GroupSpec(
        "Web",
        listOf(
            WidgetSpec("tomcat-sessions-active", "Sessions active", "tomcat.sessions.active.current", render = Render.CHART, unit = Unit.COUNT),
            WidgetSpec("tomcat-sessions-created", "Sessions created", "tomcat.sessions.created", statistic = "COUNT", render = Render.NUMBER, unit = Unit.COUNT),
            WidgetSpec("tomcat-threads-busy", "Threads busy", "tomcat.threads.busy", render = Render.CHART, unit = Unit.COUNT),
        ),
    ),
)

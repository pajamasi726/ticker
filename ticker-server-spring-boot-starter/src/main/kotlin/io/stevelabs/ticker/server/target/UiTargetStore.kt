package io.stevelabs.ticker.server.target

import org.slf4j.LoggerFactory
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue
import java.nio.file.Files
import java.nio.file.Path

interface UiTargetStore {
    fun load(): List<Target>
    fun save(targets: List<Target>)
}

/** No-op store: UI targets live only in memory (lost on restart). Default when no path is configured. */
class InMemoryUiTargetStore : UiTargetStore {
    override fun load(): List<Target> = emptyList()
    override fun save(targets: List<Target>) { /* no-op */ }
}

/**
 * JSON file-backed store. Degrades gracefully: load() returns empty on missing/corrupt file
 * (logs a warning, never throws); save() logs a warning on IO errors and returns (never throws).
 */
class JsonFileUiTargetStore(
    private val path: Path,
    private val mapper: ObjectMapper,
) : UiTargetStore {
    private val log = LoggerFactory.getLogger(JsonFileUiTargetStore::class.java)

    override fun load(): List<Target> {
        if (!Files.exists(path)) return emptyList()
        return try {
            mapper.readValue<List<Target>>(path.toFile())
        } catch (e: Exception) {
            log.warn("Failed to load UI targets from '{}': {} — starting with empty list.", path, e.message)
            emptyList()
        }
    }

    override fun save(targets: List<Target>) {
        try {
            path.parent?.let { Files.createDirectories(it) }
            mapper.writeValue(path, targets)
        } catch (e: Exception) {
            log.warn("Failed to persist UI targets to '{}': {}", path, e.message)
        }
    }
}

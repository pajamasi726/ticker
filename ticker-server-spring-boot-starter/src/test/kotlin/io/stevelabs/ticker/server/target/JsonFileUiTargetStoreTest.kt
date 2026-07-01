package io.stevelabs.ticker.server.target

import io.stevelabs.ticker.core.ServiceType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.nio.file.Path

class JsonFileUiTargetStoreTest {

    private val mapper = jacksonObjectMapper()

    private fun sampleTarget(id: String = "test-svc") = Target(
        id = id,
        name = id,
        type = ServiceType.HTTP,
        url = "http://$id:8080",
        tags = listOf("ui"),
        source = TargetSource.UI,
    )

    @Test fun `round-trip - save then load returns equal list`(@TempDir dir: Path) {
        val file = dir.resolve("ui-targets.json")
        val store = JsonFileUiTargetStore(file, mapper)
        val targets = listOf(sampleTarget("svc-a"), sampleTarget("svc-b"))

        store.save(targets)
        val loaded = store.load()

        assertThat(loaded).isEqualTo(targets)
    }

    @Test fun `load from non-existent file returns empty list and does not throw`(@TempDir dir: Path) {
        val file = dir.resolve("does-not-exist.json")
        val store = JsonFileUiTargetStore(file, mapper)
        assertThat(store.load()).isEmpty()
    }

    @Test fun `load from corrupt file returns empty list and does not throw`(@TempDir dir: Path) {
        val file = dir.resolve("corrupt.json")
        file.toFile().writeText("{this is not valid json ][")
        val store = JsonFileUiTargetStore(file, mapper)
        assertThat(store.load()).isEmpty()
    }

    @Test fun `save creates parent directories if needed`(@TempDir dir: Path) {
        val nested = dir.resolve("a/b/c/ui-targets.json")
        val store = JsonFileUiTargetStore(nested, mapper)
        store.save(listOf(sampleTarget("nested-svc")))
        assertThat(nested.toFile().exists()).isTrue()
    }

    @Test fun `save then load preserves all fields`(@TempDir dir: Path) {
        val file = dir.resolve("full-fields.json")
        val store = JsonFileUiTargetStore(file, mapper)
        val target = Target(
            id = "full-svc",
            name = "full-svc",
            type = ServiceType.HTTP,
            url = "https://full-svc.example.com/health",
            tags = listOf("ui", "external"),
            source = TargetSource.UI,
        )
        store.save(listOf(target))
        val loaded = store.load().single()
        assertThat(loaded.id).isEqualTo("full-svc")
        assertThat(loaded.url).isEqualTo("https://full-svc.example.com/health")
        assertThat(loaded.tags).containsExactly("ui", "external")
        assertThat(loaded.source).isEqualTo(TargetSource.UI)
        assertThat(loaded.type).isEqualTo(ServiceType.HTTP)
    }
}

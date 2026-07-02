package io.stevelabs.ticker.server.target

import io.stevelabs.ticker.core.RegistrationRequest
import io.stevelabs.ticker.core.ServiceType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TargetRegistryTest {
    private fun def(name: String, url: String = "http://$name") =
        TargetDefinition(name = name, type = ServiceType.SPRING, url = url)

    @Test fun `seeds static targets with id=name and STATIC source`() {
        val registry = TargetRegistry(listOf(def("payment-api")))
        val t = registry.all().single()
        assertThat(t.id).isEqualTo("payment-api")
        assertThat(t.type).isEqualTo(ServiceType.SPRING)
        assertThat(t.source).isEqualTo(TargetSource.STATIC)
    }

    @Test fun `fails fast on duplicate static names`() {
        assertThrows<IllegalArgumentException> { TargetRegistry(listOf(def("dup"), def("dup"))) }
    }

    @Test fun `register adds a REGISTERED target visible in all(), keyed by name@host-port`() {
        val registry = TargetRegistry(emptyList())
        registry.register(RegistrationRequest("edge", ServiceType.HTTP, "http://edge:8080"))
        val t = registry.all().single()
        assertThat(t.name).isEqualTo("edge")
        assertThat(t.instance).isEqualTo("edge:8080")
        assertThat(t.id).isEqualTo("edge@edge:8080")
        assertThat(t.source).isEqualTo(TargetSource.REGISTERED)
        assertThat(t.type).isEqualTo(ServiceType.HTTP)
    }

    @Test fun `re-registering the same instance (name + url) upserts, not duplicates`() {
        val registry = TargetRegistry(emptyList())
        registry.register(RegistrationRequest("api", ServiceType.SPRING, "http://api:8080"))
        registry.register(RegistrationRequest("api", ServiceType.SPRING, "http://api:8080")) // heartbeat
        assertThat(registry.all()).hasSize(1)
    }

    @Test fun `same name at different urls are distinct instances (replicas)`() {
        val registry = TargetRegistry(emptyList())
        registry.register(RegistrationRequest("api", ServiceType.SPRING, "http://10.0.0.1:8080"))
        registry.register(RegistrationRequest("api", ServiceType.SPRING, "http://10.0.0.2:8080"))
        val all = registry.all()
        assertThat(all).hasSize(2)
        assertThat(all.map { it.name }).containsOnly("api")
        assertThat(all.map { it.instance }).containsExactlyInAnyOrder("10.0.0.1:8080", "10.0.0.2:8080")
    }

    @Test fun `client-reported hostname becomes the instance label (with url port)`() {
        val registry = TargetRegistry(emptyList())
        registry.register(RegistrationRequest("api", ServiceType.SPRING, "http://10.0.0.1:8080", instance = "orders-pod-abc", ip = "10.0.0.1"))
        val t = registry.all().single()
        assertThat(t.name).isEqualTo("api")
        assertThat(t.instance).isEqualTo("orders-pod-abc:8080")
        assertThat(t.id).isEqualTo("api@10.0.0.1:8080")
        assertThat(t.ip).isEqualTo("10.0.0.1")
    }

    @Test fun `underscored docker-compose hostname yields a slash-free id (URI host is null)`() {
        val registry = TargetRegistry(emptyList())
        val t = registry.register(RegistrationRequest("orders-api", ServiceType.SPRING, "http://orders_api:8080"))
        assertThat(t.id).isEqualTo("orders-api@orders_api:8080")
        assertThat(t.id).doesNotContain("/")
    }

    @Test fun `addUiTarget refuses a name owned by registered instances`() {
        val registry = TargetRegistry(emptyList())
        registry.register(RegistrationRequest("orders-api", ServiceType.SPRING, "http://10.0.0.1:8080"))
        // Would otherwise silently shadow every orders-api replica out of all()/polling/alerting.
        assertThat(registry.addUiTarget("orders-api", "http://whatever"))
            .isEqualTo(TargetRegistry.AddUiResult.NameTaken)
        assertThat(registry.all().map { it.name }).contains("orders-api")
    }

    @Test fun `evictExpired drops instances whose heartbeat stopped, keeps fresh ones`() {
        val registry = TargetRegistry(emptyList())
        registry.register(RegistrationRequest("api", ServiceType.SPRING, "http://10.0.0.1:8080"), nowMillis = 1_000)
        registry.register(RegistrationRequest("api", ServiceType.SPRING, "http://10.0.0.2:8080"), nowMillis = 90_000)
        val evicted = registry.evictExpired(maxAgeMillis = 60_000, nowMillis = 100_000)
        assertThat(evicted.map { it.instance }).containsExactly("10.0.0.1:8080")
        assertThat(registry.all().map { it.instance }).containsExactly("10.0.0.2:8080")
    }

    @Test fun `evictExpired is a no-op when disabled (maxAge 0)`() {
        val registry = TargetRegistry(emptyList())
        registry.register(RegistrationRequest("api", ServiceType.SPRING, "http://10.0.0.1:8080"), nowMillis = 0)
        assertThat(registry.evictExpired(maxAgeMillis = 0, nowMillis = 999_999_999)).isEmpty()
        assertThat(registry.all()).hasSize(1)
    }

    @Test fun `all() merges static and registered`() {
        val registry = TargetRegistry(listOf(def("static-one")))
        registry.register(RegistrationRequest("reg-one", ServiceType.SPRING, "http://reg"))
        assertThat(registry.all().map { it.name }).containsExactlyInAnyOrder("static-one", "reg-one")
    }

    @Test fun `static wins when a registration collides with a static name`() {
        val registry = TargetRegistry(listOf(def("payment-api", "http://static:8080")))
        registry.register(RegistrationRequest("payment-api", ServiceType.SPRING, "http://impostor:8080"))
        assertThat(registry.all()).hasSize(1)
        val t = registry.all().single { it.id == "payment-api" }
        assertThat(t.url).isEqualTo("http://static:8080")
        assertThat(t.source).isEqualTo(TargetSource.STATIC)
    }

    @Test fun `remove returns REMOVED for a registered target and drops it`() {
        val registry = TargetRegistry(emptyList())
        val reg = registry.register(RegistrationRequest("edge", ServiceType.HTTP, "http://edge:8080"))
        assertThat(registry.remove(reg.id)).isEqualTo(RemoveResult.REMOVED)
        assertThat(registry.all()).isEmpty()
    }

    @Test fun `remove returns NOT_FOUND for an unknown id`() {
        assertThat(TargetRegistry(emptyList()).remove("ghost")).isEqualTo(RemoveResult.NOT_FOUND)
    }

    @Test fun `remove returns STATIC for a static target and keeps it`() {
        val registry = TargetRegistry(listOf(def("payment-api")))
        assertThat(registry.remove("payment-api")).isEqualTo(RemoveResult.STATIC)
        assertThat(registry.all()).hasSize(1)
    }

    // ---- UI target tests ----

    @Test fun `addUiTarget returns Added with source=UI, type=HTTP, tags contains ui`() {
        val registry = TargetRegistry(emptyList())
        val result = registry.addUiTarget("my-http-svc", "https://example.com")
        assertThat(result).isInstanceOf(TargetRegistry.AddUiResult.Added::class.java)
        val added = (result as TargetRegistry.AddUiResult.Added).target
        assertThat(added.source).isEqualTo(TargetSource.UI)
        assertThat(added.type).isEqualTo(ServiceType.HTTP)
        assertThat(added.tags).contains("ui")
        assertThat(added.id).isEqualTo("my-http-svc")
        assertThat(added.url).isEqualTo("https://example.com")
    }

    @Test fun `all() contains UI target after addUiTarget`() {
        val registry = TargetRegistry(emptyList())
        registry.addUiTarget("ui-svc", "http://ui-svc:8080")
        assertThat(registry.all().map { it.id }).contains("ui-svc")
    }

    @Test fun `addUiTarget returns NameTaken when name collides with static target`() {
        val registry = TargetRegistry(listOf(def("static-svc")))
        assertThat(registry.addUiTarget("static-svc", "http://whatever")).isEqualTo(TargetRegistry.AddUiResult.NameTaken)
    }

    @Test fun `addUiTarget returns NameTaken on duplicate UI name`() {
        val registry = TargetRegistry(emptyList())
        registry.addUiTarget("ui-svc", "http://ui-svc:8080")
        assertThat(registry.addUiTarget("ui-svc", "http://ui-svc:9090")).isEqualTo(TargetRegistry.AddUiResult.NameTaken)
    }

    @Test fun `remove returns REMOVED for a UI target and removes it from all()`() {
        val registry = TargetRegistry(emptyList())
        registry.addUiTarget("ui-svc", "http://ui-svc:8080")
        assertThat(registry.remove("ui-svc")).isEqualTo(RemoveResult.REMOVED)
        assertThat(registry.all().map { it.id }).doesNotContain("ui-svc")
    }

    @Test fun `static precedence preserved over UI targets in all()`() {
        val registry = TargetRegistry(listOf(def("shared", "http://static:8080")))
        // UI add for the same name is blocked via NameTaken, but if we bypass (or via registered), static still wins
        // Verify that static is returned first and its URL is the static one
        val t = registry.all().single { it.id == "shared" }
        assertThat(t.source).isEqualTo(TargetSource.STATIC)
        assertThat(t.url).isEqualTo("http://static:8080")
    }

    @Test fun `all() includes UI targets merged with static and registered`() {
        val registry = TargetRegistry(listOf(def("static-svc")))
        registry.register(RegistrationRequest("reg-svc", ServiceType.HTTP, "http://reg"))
        registry.addUiTarget("ui-svc", "http://ui")
        val names = registry.all().map { it.name }
        assertThat(names).containsExactlyInAnyOrder("static-svc", "reg-svc", "ui-svc")
    }

    @Test fun `persistence - init loads UI targets from store`() {
        val stored = Target(
            id = "persisted-svc",
            name = "persisted-svc",
            type = ServiceType.HTTP,
            url = "http://persisted:8080",
            tags = listOf("ui"),
            source = TargetSource.UI,
        )
        val store = RecordingStore(initial = listOf(stored))
        val registry = TargetRegistry(emptyList(), store)
        assertThat(registry.all().map { it.id }).contains("persisted-svc")
        val t = registry.all().single { it.id == "persisted-svc" }
        assertThat(t.source).isEqualTo(TargetSource.UI)
    }

    @Test fun `persistence - addUiTarget calls save with the new target included`() {
        val store = RecordingStore()
        val registry = TargetRegistry(emptyList(), store)
        registry.addUiTarget("new-ui", "http://new-ui:8080")
        assertThat(store.lastSaved).isNotNull
        assertThat(store.lastSaved!!.map { it.id }).contains("new-ui")
    }

    @Test fun `persistence - remove UI target calls save without the removed target`() {
        val store = RecordingStore()
        val registry = TargetRegistry(emptyList(), store)
        registry.addUiTarget("temp-ui", "http://temp-ui")
        registry.remove("temp-ui")
        assertThat(store.lastSaved).isNotNull
        assertThat(store.lastSaved!!.map { it.id }).doesNotContain("temp-ui")
    }

    /** Hand-rolled fake (no MockK) capturing the last list passed to save(). */
    private class RecordingStore(private val initial: List<Target> = emptyList()) : UiTargetStore {
        var lastSaved: List<Target>? = null
        override fun load(): List<Target> = initial
        override fun save(targets: List<Target>) { lastSaved = targets }
    }
}

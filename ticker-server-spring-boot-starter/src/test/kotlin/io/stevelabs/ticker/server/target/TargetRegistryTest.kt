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

    @Test fun `register adds a REGISTERED target visible in all()`() {
        val registry = TargetRegistry(emptyList())
        registry.register(RegistrationRequest("edge", ServiceType.HTTP, "http://edge"))
        val t = registry.all().single()
        assertThat(t.id).isEqualTo("edge")
        assertThat(t.source).isEqualTo(TargetSource.REGISTERED)
        assertThat(t.type).isEqualTo(ServiceType.HTTP)
    }

    @Test fun `register upserts by name (re-register updates url)`() {
        val registry = TargetRegistry(emptyList())
        registry.register(RegistrationRequest("api", ServiceType.SPRING, "http://old:8080"))
        registry.register(RegistrationRequest("api", ServiceType.SPRING, "http://new:8080"))
        assertThat(registry.all()).hasSize(1)
        assertThat(registry.all().single().url).isEqualTo("http://new:8080")
    }

    @Test fun `all() merges static and registered`() {
        val registry = TargetRegistry(listOf(def("static-one")))
        registry.register(RegistrationRequest("reg-one", ServiceType.SPRING, "http://reg"))
        assertThat(registry.all().map { it.id }).containsExactlyInAnyOrder("static-one", "reg-one")
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
        registry.register(RegistrationRequest("edge", ServiceType.HTTP, "http://edge"))
        assertThat(registry.remove("edge")).isEqualTo(RemoveResult.REMOVED)
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
}

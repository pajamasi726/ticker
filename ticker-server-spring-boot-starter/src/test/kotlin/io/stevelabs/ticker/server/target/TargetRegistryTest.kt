package io.stevelabs.ticker.server.target

import io.stevelabs.ticker.core.ServiceType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class TargetRegistryTest {
    private fun def(name: String, type: ServiceType = ServiceType.HTTP, url: String = "http://x") =
        TargetDefinition(name = name, type = type, url = url)

    @Test
    fun `seeds targets from definitions with id=name and STATIC source`() {
        val reg = TargetRegistry(listOf(def("a", ServiceType.SPRING, "http://a"), def("b")))
        assertThat(reg.all()).hasSize(2)
        val a = reg.all().first { it.name == "a" }
        assertThat(a.id).isEqualTo("a")
        assertThat(a.type).isEqualTo(ServiceType.SPRING)
        assertThat(a.source).isEqualTo(TargetSource.STATIC)
    }

    @Test
    fun `fails fast on duplicate target names`() {
        assertThatThrownBy { TargetRegistry(listOf(def("dup"), def("dup"))) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("dup")
    }
}

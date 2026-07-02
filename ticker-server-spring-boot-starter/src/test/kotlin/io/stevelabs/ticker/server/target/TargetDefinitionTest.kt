package io.stevelabs.ticker.server.target

import io.stevelabs.ticker.core.ServiceType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class TargetDefinitionTest {

    @Test fun `missing url fails with a plain-language message (not an NPE)`() {
        assertThatThrownBy { TargetDefinition(name = "edge-nginx") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("'edge-nginx' is missing 'url'")
    }

    @Test fun `missing name fails with a plain-language message`() {
        assertThatThrownBy { TargetDefinition(url = "http://x/health") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("missing 'name'")
    }

    @Test fun `type is optional and defaults to HTTP`() {
        assertThat(TargetDefinition(name = "x", url = "http://x/health").type).isEqualTo(ServiceType.HTTP)
    }
}

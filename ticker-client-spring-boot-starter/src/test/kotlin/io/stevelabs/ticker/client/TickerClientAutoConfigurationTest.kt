package io.stevelabs.ticker.client

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class TickerClientAutoConfigurationTest {
    private val runner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(TickerClientAutoConfiguration::class.java))

    @Test
    fun `registers registrar by default`() {
        runner.run { assertThat(it).hasSingleBean(TickerClientRegistrar::class.java) }
    }

    @Test
    fun `backs off when ticker client disabled`() {
        runner.withPropertyValues("ticker.client.enabled=false")
            .run { assertThat(it).doesNotHaveBean(TickerClientRegistrar::class.java) }
    }
}

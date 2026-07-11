package io.stevelabs.ticker.server.graph

import io.stevelabs.ticker.core.RegistrationRequest
import io.stevelabs.ticker.core.ServiceType
import io.stevelabs.ticker.server.detail.MetricSource
import io.stevelabs.ticker.server.detail.ResolvedGroup
import io.stevelabs.ticker.server.detail.TagStat
import io.stevelabs.ticker.server.poll.PollProperties
import io.stevelabs.ticker.server.state.HealthStateStore
import io.stevelabs.ticker.server.target.Target
import io.stevelabs.ticker.server.target.TargetDefinition
import io.stevelabs.ticker.server.target.TargetRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GraphServiceTest {

    /** orders(2 replicas) -> payments + one external; payments calls nobody. */
    private val source = object : MetricSource {
        override fun fetch(target: Target): List<ResolvedGroup> = emptyList()
        override fun tagBreakdown(target: Target, metricName: String, tag: String, filter: Map<String, String>): List<TagStat> {
            if (target.name != "orders") return emptyList()
            return if (filter["outcome"] == "SERVER_ERROR") {
                listOf(TagStat("payments-host", 1.0, null, null))
            } else {
                // each orders replica reports the same shape — merge must SUM counts and weight means
                listOf(
                    TagStat("payments-host", 100.0, 0.050, 0.400),
                    TagStat("api.external.example", 10.0, 0.200, 0.900),
                )
            }
        }
    }

    private fun registry(): TargetRegistry =
        TargetRegistry(listOf(TargetDefinition("payments", ServiceType.SPRING, "http://payments-host:8080"))).also {
            it.register(RegistrationRequest("orders", ServiceType.SPRING, "http://o1:8081"), nowMillis = 1)
            it.register(RegistrationRequest("orders", ServiceType.SPRING, "http://o2:8081"), nowMillis = 1)
        }

    @Test fun `edges merge replicas by name, resolve callees, and keep externals separate`() {
        val reg = registry()
        val service = GraphService(reg, HealthStateStore(reg, PollProperties()), source)
        val graph = service.graph(nowMillis = 1_000)

        assertThat(graph.nodes.filter { !it.external }.map { it.name }).containsExactlyInAnyOrder("payments", "orders")
        assertThat(graph.nodes.filter { it.external }.map { it.name }).containsExactly("api.external.example")

        val toPayments = graph.edges.single { it.to == "payments" }
        assertThat(toPayments.from).isEqualTo("orders")
        assertThat(toPayments.external).isFalse
        assertThat(toPayments.count).isEqualTo(200.0) // two replicas summed
        assertThat(toPayments.mean).isEqualTo(0.050) // count-weighted mean of equal means
        assertThat(toPayments.error5xx).isEqualTo(2.0)

        val ext = graph.edges.single { it.to == "api.external.example" }
        assertThat(ext.external).isTrue
        assertThat(ext.count).isEqualTo(20.0)
    }

    @Test fun `graph is cached within the TTL and rebuilt after it`() {
        var calls = 0
        val counting = object : MetricSource {
            override fun fetch(target: Target): List<ResolvedGroup> = emptyList()
            override fun tagBreakdown(target: Target, metricName: String, tag: String, filter: Map<String, String>): List<TagStat> {
                calls++
                return emptyList()
            }
        }
        val reg = registry()
        val service = GraphService(reg, HealthStateStore(reg, PollProperties()), counting, cacheTtlMillis = 10_000)
        service.graph(nowMillis = 0)
        val after = calls
        service.graph(nowMillis = 5_000) // within TTL — served from cache
        assertThat(calls).isEqualTo(after)
        service.graph(nowMillis = 20_000) // expired — swept again
        assertThat(calls).isGreaterThan(after)
    }
}

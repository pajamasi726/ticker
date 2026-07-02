package io.stevelabs.ticker.core

import kotlin.test.Test
import kotlin.test.assertEquals

class TargetIdsTest {

    @Test fun `plain host and port`() {
        assertEquals("10.0.0.5:8080", TargetIds.instanceKey("http://10.0.0.5:8080"))
        assertEquals(8080, TargetIds.port("http://10.0.0.5:8080"))
        assertEquals("api@10.0.0.5:8080", TargetIds.registrationId("api", "http://10.0.0.5:8080"))
    }

    @Test fun `no port`() {
        assertEquals("orders.internal", TargetIds.instanceKey("https://orders.internal/health"))
        assertEquals(null, TargetIds.port("https://orders.internal/health"))
    }

    @Test fun `underscored hostname (URI host is null) never yields slashes`() {
        // java.net.URI can't parse underscored hosts (docker-compose service names) — host == null.
        val key = TargetIds.instanceKey("http://orders_api:8080")
        assertEquals("orders_api:8080", key)
        assertEquals(8080, TargetIds.port("http://orders_api:8080"))
        assertEquals(false, TargetIds.registrationId("orders-api", "http://orders_api:8080").contains('/'))
    }

    @Test fun `underscored hostname with path and query`() {
        assertEquals("orders_api:8080", TargetIds.instanceKey("http://orders_api:8080/actuator/health?x=1"))
    }

    @Test fun `ipv6 host keeps brackets and port parses past inner colons`() {
        assertEquals("[2001:db8::1]:8080", TargetIds.instanceKey("http://[2001:db8::1]:8080"))
        assertEquals(8080, TargetIds.port("http://[2001:db8::1]:8080"))
    }

    @Test fun `garbage url degrades to a slash-free key`() {
        val key = TargetIds.instanceKey("not a url at all / with slash")
        assertEquals(false, key.contains('/'))
    }
}

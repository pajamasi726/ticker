package io.stevelabs.ticker.core

/**
 * Shared derivation of a registered instance's identity from its URL — used by the collector to key
 * the registry AND by the client to deregister itself on shutdown, so both sides always agree.
 */
object TargetIds {

    /**
     * `host:port` of a target URL — the stable per-instance key. Never contains `/` (ids travel in
     * URL paths, and Tomcat rejects encoded slashes by default). Handles hosts `java.net.URI` can't
     * (underscored docker-compose names) by extracting the authority manually.
     */
    fun instanceKey(url: String): String {
        val parsed = parse(url)
        val host = parsed?.host
        if (host != null) return if (parsed.port >= 0) "$host:${parsed.port}" else host
        val authority = url.substringAfter("://", url).substringBefore('/').substringBefore('?').substringBefore('#')
        return (authority.ifBlank { url }).replace('/', '_')
    }

    /** The URL's explicit port, or null. From the parsed URI, so IPv6 colons can't confuse it. */
    fun port(url: String): Int? {
        parse(url)?.port?.takeIf { it >= 0 }?.let { return it }
        // Manual fallback for URLs URI can't parse: last ':' of the authority, if numeric.
        val authority = url.substringAfter("://", url).substringBefore('/').substringBefore('?').substringBefore('#')
        return authority.substringAfterLast(':', "").toIntOrNull()
    }

    /** The registry id of a self-registered instance: `name@host:port`. */
    fun registrationId(name: String, url: String): String = "$name@${instanceKey(url)}"

    private fun parse(url: String): java.net.URI? = try {
        java.net.URI(url)
    } catch (e: Exception) {
        null
    }
}

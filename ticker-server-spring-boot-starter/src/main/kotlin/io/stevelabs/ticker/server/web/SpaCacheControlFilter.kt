package io.stevelabs.ticker.server.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.filter.OncePerRequestFilter

// The SPA shell ("/", any .html, and the base-path roots when ticker.server.base-path is set) must
// never be cached: it points at content-hashed asset files, so a stale shell boots an OLD bundle
// after a redeploy. Spring Boot's default static handler sends no Cache-Control, only a constant
// "Last-Modified: 1980" — which makes browsers both heuristically cache the shell for years AND
// falsely revalidate it as 304 Not Modified (1980 is always "old"). So no-cache is not enough; we
// send no-store to force a fresh shell every load. The hashed assets under /assets are untouched
// and stay freely cacheable.
class SpaCacheControlFilter(basePath: String = "") : OncePerRequestFilter() {
    private val base = normalizeBasePath(basePath)

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val path = request.requestURI
        val isShell = path == "/" || path.endsWith(".html") ||
            (base.isNotEmpty() && (path == base || path == "$base/"))
        if (isShell) {
            response.setHeader("Cache-Control", "no-store, must-revalidate")
        }
        chain.doFilter(request, response)
    }
}

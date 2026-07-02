package io.stevelabs.ticker.server.web

import io.stevelabs.ticker.server.TickerServerProperties
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.method.HandlerTypePredicate
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/** Normalize `ticker.server.base-path`: `""` → `""`, and `ticker` / `/ticker/` / `/ticker` → `/ticker`. */
fun normalizeBasePath(raw: String): String {
    val trimmed = raw.trim().trim('/')
    return if (trimmed.isEmpty()) "" else "/$trimmed"
}

/**
 * Relocates the whole Ticker UI + REST API under `ticker.server.base-path` (default off).
 *
 * When a base path is set (e.g. `/ticker`):
 * - every `@RestController` (the API endpoints AND the SPA shell below) is prefixed, so the API moves
 *   under `<base>/api` and the shell to `<base>/`, via [PathMatchConfigurer.addPathPrefix]. The
 *   collector's own `/actuator` is NOT a `@RestController`, so it stays put (guardrail #1).
 * - the bundled SPA assets are served under `<base>/assets`.
 * - the root redirects to `<base>/` so the old entry point still lands you on the board.
 *
 * When empty (default) this configurer is a no-op and Spring Boot's default static serving handles `/`.
 */
class TickerSpaWebConfigurer(basePath: String) : WebMvcConfigurer {
    private val base = normalizeBasePath(basePath)

    override fun configurePathMatch(configurer: PathMatchConfigurer) {
        if (base.isNotEmpty()) {
            // Scope the prefix to Ticker's OWN controllers. forAnnotation(RestController) would relocate
            // every @RestController in the host application — an embedding app's /orders/** must not
            // silently move to /ticker/orders/** because it enabled a Ticker base path.
            configurer.addPathPrefix(base, HandlerTypePredicate.forBasePackage("io.stevelabs.ticker"))
        }
    }

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        if (base.isNotEmpty()) {
            // Serve everything under classpath:/static (assets, favicon, …) beneath the base path.
            // @RestController mappings (the shell + API) take precedence, so only static files fall here.
            registry.addResourceHandler("$base/**").addResourceLocations("classpath:/static/")
        }
    }

    override fun addViewControllers(registry: ViewControllerRegistry) {
        if (base.isNotEmpty()) {
            registry.addRedirectViewController("/", "$base/")
            // Old bookmarks to the un-based shell: redirect rather than serve a raw, uninjected copy.
            registry.addRedirectViewController("/index.html", "$base/")
        }
    }
}

/**
 * Serves the bundled React shell (`index.html`) with the base path injected, so the SPA knows where its
 * API lives. Registered only when `ticker.server.base-path` is set; otherwise Spring Boot serves the raw
 * `index.html` from `classpath:/static/` at `/` unchanged. As a `@RestController` it is picked up by the
 * path-prefix above, so it ends up serving `<base>/`.
 */
@RestController
class SpaShellController(props: TickerServerProperties) {
    private val base = normalizeBasePath(props.basePath)
    private val shell: String by lazy { render() }

    // "<base>", "<base>/" (Spring 6+ no longer trailing-slash-matches), and "<base>/index.html" —
    // the last so the resource handler can never serve a raw, uninjected copy of the shell.
    @GetMapping(value = ["", "/", "/index.html"], produces = [MediaType.TEXT_HTML_VALUE])
    fun index(): String = shell

    private fun render(): String {
        val raw = javaClass.getResource("/static/index.html")?.readText()
            ?: "<!doctype html><html><head></head><body><p>Ticker UI is not bundled in this build.</p></body></html>"
        if (base.isEmpty()) return raw
        // <base> must precede the asset tags so relative (Vite base './') URLs resolve under <base>/;
        // the inline (non-deferred) script runs before the deferred module bundle, so window.__TICKER_BASE__
        // is set before the app boots.
        val inject = """<base href="$base/"><script>window.__TICKER_BASE__="$base";</script>"""
        return if (raw.contains("<head>")) raw.replaceFirst("<head>", "<head>$inject") else "$inject$raw"
    }
}

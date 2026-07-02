package io.stevelabs.ticker.server.target

import io.stevelabs.ticker.core.RegistrationRequest
import io.stevelabs.ticker.core.ServiceType
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

enum class RemoveResult { REMOVED, NOT_FOUND, STATIC }

class TargetRegistry(
    definitions: List<TargetDefinition>,
    private val uiStore: UiTargetStore = InMemoryUiTargetStore(),
) {
    private val log = LoggerFactory.getLogger(TargetRegistry::class.java)

    private val staticTargets: List<Target> = definitions.map {
        Target(id = it.name, name = it.name, type = it.type, url = it.url, tags = it.tags, source = TargetSource.STATIC)
    }
    private val staticIds: Set<String> = staticTargets.map { it.id }.toSet()
    private val registered = ConcurrentHashMap<String, Target>()
    private val ui = ConcurrentHashMap<String, Target>()

    init {
        val dupes = staticTargets.groupBy { it.id }.filterValues { it.size > 1 }.keys
        require(dupes.isEmpty()) { "Duplicate target names in ticker.targets: $dupes" }
        uiStore.load().forEach { if (it.id !in staticIds) ui[it.id] = it.copy(source = TargetSource.UI) }
    }

    /** Everything the poller/UI see: static wins, then UI targets, then registered instances. */
    fun all(): List<Target> =
        staticTargets +
            ui.values.filter { it.id !in staticIds } +
            registered.values.filter { it.name !in staticIds && it.name !in ui.keys }

    /**
     * Upsert a self-registered instance. The id is keyed by `name@host:port` (from the URL — stable and
     * unique per instance) so multiple replicas of the same app each get their own tile instead of
     * overwriting one another. The displayed instance label is the client-reported hostname when present
     * (e.g. the pod name), else that same host:port. A name owned by a static target is ignored (static
     * wins). Heartbeats re-register the same id (same name + URL), so they update in place.
     */
    fun register(request: RegistrationRequest): Target {
        if (request.name in staticIds) {
            log.warn("Registration for '{}' ignored: a static target already owns that name.", request.name)
            return Target(request.name, request.name, request.type, request.url, request.tags, TargetSource.REGISTERED)
        }
        val urlKey = instanceOf(request.url)              // "host:port" — stable, unique per instance → id
        val port = urlKey.substringAfter(':', "")
        val host = request.instance?.takeIf { it.isNotBlank() }
        // Display label: reported hostname (+ port, so replicas on one host still differ); else host:port.
        val instance = when {
            host != null && port.isNotEmpty() -> "$host:$port"
            host != null -> host
            else -> urlKey
        }
        val id = "${request.name}@$urlKey"
        val target = Target(id, request.name, request.type, request.url, request.tags, TargetSource.REGISTERED, instance, request.ip)
        registered[id] = target
        log.info("Registered '{}' instance [{}] ({}) at {}", target.name, instance, target.type, target.url)
        return target
    }

    /** Host:port from a target URL — the stable per-instance key; falls back to the raw URL. */
    private fun instanceOf(url: String): String = try {
        val u = java.net.URI(url)
        val host = u.host ?: return url
        if (u.port >= 0) "$host:${u.port}" else host
    } catch (e: Exception) {
        url
    }

    sealed interface AddUiResult {
        data class Added(val target: Target) : AddUiResult
        data object NameTaken : AddUiResult
    }

    /**
     * Add an HTTP liveness monitor created via the UI (source=UI, type=HTTP).
     * Returns [AddUiResult.NameTaken] if the name is already used by any target (static, UI, or registered).
     */
    fun addUiTarget(name: String, url: String): AddUiResult {
        val id = name
        if (id in staticIds || ui.containsKey(id) || registered.containsKey(id)) return AddUiResult.NameTaken
        val target = Target(
            id = id,
            name = name,
            type = ServiceType.HTTP,
            url = url,
            tags = listOf("ui"),
            source = TargetSource.UI,
        )
        ui[id] = target
        uiStore.save(ui.values.toList())
        log.info("Added UI target '{}' at {}", name, url)
        return AddUiResult.Added(target)
    }

    /** Remove a target by id. Static targets cannot be removed; UI and registered targets can be removed. */
    fun remove(id: String): RemoveResult = when {
        id in staticIds -> RemoveResult.STATIC
        ui.remove(id) != null -> {
            uiStore.save(ui.values.toList())
            RemoveResult.REMOVED
        }
        registered.remove(id) != null -> RemoveResult.REMOVED
        else -> RemoveResult.NOT_FOUND
    }
}

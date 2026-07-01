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

    /** Everything the poller/UI see: static wins, then UI targets, then registered (static wins on id collision). */
    fun all(): List<Target> =
        staticTargets +
            ui.values.filter { it.id !in staticIds } +
            registered.values.filter { it.id !in staticIds && !ui.containsKey(it.id) }

    /** Upsert a self-registered target (id = name). A name owned by a static target is ignored (static wins). */
    fun register(request: RegistrationRequest): Target {
        val target = Target(
            id = request.name, name = request.name, type = request.type,
            url = request.url, tags = request.tags, source = TargetSource.REGISTERED,
        )
        if (target.id in staticIds) {
            log.warn("Registration for '{}' ignored: a static target already owns that name.", target.id)
            return target
        }
        registered[target.id] = target
        log.info("Registered target '{}' ({}) at {}", target.name, target.type, target.url)
        return target
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

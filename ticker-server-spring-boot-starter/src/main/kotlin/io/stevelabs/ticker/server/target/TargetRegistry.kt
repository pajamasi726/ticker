package io.stevelabs.ticker.server.target

import io.stevelabs.ticker.core.RegistrationRequest
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

enum class RemoveResult { REMOVED, NOT_FOUND, STATIC }

class TargetRegistry(definitions: List<TargetDefinition>) {
    private val log = LoggerFactory.getLogger(TargetRegistry::class.java)

    private val staticTargets: List<Target> = definitions.map {
        Target(id = it.name, name = it.name, type = it.type, url = it.url, tags = it.tags, source = TargetSource.STATIC)
    }
    private val staticIds: Set<String> = staticTargets.map { it.id }.toSet()
    private val registered = ConcurrentHashMap<String, Target>()

    init {
        val dupes = staticTargets.groupBy { it.id }.filterValues { it.size > 1 }.keys
        require(dupes.isEmpty()) { "Duplicate target names in ticker.targets: $dupes" }
    }

    /** Everything the poller/UI see: static config + dynamically registered (static wins on id collision). */
    fun all(): List<Target> = staticTargets + registered.values.filter { it.id !in staticIds }

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

    /** Remove a registered target. Static targets cannot be removed. */
    fun remove(id: String): RemoveResult = when {
        id in staticIds -> RemoveResult.STATIC
        registered.remove(id) != null -> RemoveResult.REMOVED
        else -> RemoveResult.NOT_FOUND
    }
}

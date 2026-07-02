package io.stevelabs.ticker.server.target

import io.stevelabs.ticker.core.RegistrationRequest
import io.stevelabs.ticker.core.ServiceType
import io.stevelabs.ticker.core.TargetIds
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
    private val lastSeenMillis = ConcurrentHashMap<String, Long>()
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
     * Upsert a self-registered instance. The id is keyed by `name@host:port` (from the URL — the one
     * thing that must be unique for the poller to check each replica) so multiple replicas of the same
     * app each get their own tile instead of overwriting one another. The displayed instance label is
     * the client-reported hostname when present (e.g. the pod name), else that same host:port. A name
     * owned by a static target is ignored (static wins). Heartbeats re-register the same id (same
     * name + URL), so they update in place and refresh the last-seen clock used by [evictExpired].
     */
    fun register(request: RegistrationRequest, nowMillis: Long = System.currentTimeMillis()): Target {
        if (request.name in staticIds) {
            log.warn("Registration for '{}' ignored: a static target already owns that name.", request.name)
            return Target(request.name, request.name, request.type, request.url, request.tags, TargetSource.REGISTERED)
        }
        val urlKey = TargetIds.instanceKey(request.url)
        val port = TargetIds.port(request.url)
        val host = request.instance?.takeIf { it.isNotBlank() }
        // Display label: reported hostname (+ port, so replicas on one host still differ); else host:port.
        val instance = when {
            host != null && port != null -> "$host:$port"
            host != null -> host
            else -> urlKey
        }
        val id = TargetIds.registrationId(request.name, request.url)
        val target = Target(id, request.name, request.type, request.url, request.tags, TargetSource.REGISTERED, instance, request.ip)
        val previous = registered.put(id, target)
        lastSeenMillis[id] = nowMillis
        if (previous == null) {
            log.info("Registered '{}' instance [{}] ({}) at {}", target.name, instance, target.type, target.url)
            if (request.name in ui.keys) {
                log.warn(
                    "Registered target '{}' is shadowed by a UI monitor with the same name — " +
                        "the instance will not appear on the wall until the UI monitor is removed.",
                    request.name,
                )
            }
        } else if (previous.instance != target.instance) {
            // Two different machines claiming one URL: they upsert the same id, so only one tile exists
            // and its label flip-flops. Almost always a shared, load-balanced ticker.client.url —
            // point each instance's url at its OWN address (or leave it unset: the client defaults
            // to its own IP:port).
            log.warn(
                "Instance '{}' re-registered for URL {} (was '{}'): multiple replicas appear to share " +
                    "one ticker.client.url — give each instance its own address or leave url unset.",
                target.instance, target.url, previous.instance,
            )
        }
        return target
    }

    /**
     * Drop registered instances whose last registration/heartbeat is older than [maxAgeMillis].
     * OPT-IN cleanup for autoscaling churn (replaced pods that never deregistered). Deliberately not
     * on by default: a crashed instance SHOULD stay on the wall as a red tile — that's the board's
     * job — and graceful shutdowns already deregister themselves.
     */
    fun evictExpired(maxAgeMillis: Long, nowMillis: Long = System.currentTimeMillis()): List<Target> {
        if (maxAgeMillis <= 0) return emptyList()
        val expired = registered.values.filter { (nowMillis - (lastSeenMillis[it.id] ?: nowMillis)) > maxAgeMillis }
        return expired.mapNotNull { target ->
            lastSeenMillis.remove(target.id)
            registered.remove(target.id)?.also {
                log.info(
                    "Evicted registered instance '{}' [{}] — no heartbeat for over {} ms " +
                        "(ticker.server.registration-expiry).",
                    it.name, it.instance, maxAgeMillis,
                )
            }
        }
    }

    sealed interface AddUiResult {
        data class Added(val target: Target) : AddUiResult
        data object NameTaken : AddUiResult
    }

    /**
     * Add an HTTP liveness monitor created via the UI (source=UI, type=HTTP).
     * Returns [AddUiResult.NameTaken] if the name is already used by any target — static, UI, or a
     * registered instance's app name (registered keys are `name@host:port`, so this checks names,
     * not keys; otherwise a UI monitor could silently shadow every replica of a live app).
     */
    fun addUiTarget(name: String, url: String): AddUiResult {
        val id = name
        if (id in staticIds || ui.containsKey(id) || registered.values.any { it.name == name }) return AddUiResult.NameTaken
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
        registered.remove(id) != null -> {
            lastSeenMillis.remove(id)
            RemoveResult.REMOVED
        }
        else -> RemoveResult.NOT_FOUND
    }
}

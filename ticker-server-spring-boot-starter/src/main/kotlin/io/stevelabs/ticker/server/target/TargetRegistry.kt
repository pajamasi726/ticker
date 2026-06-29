package io.stevelabs.ticker.server.target

class TargetRegistry(definitions: List<TargetDefinition>) {
    private val targets: List<Target> = definitions.map {
        Target(id = it.name, name = it.name, type = it.type, url = it.url, tags = it.tags, source = TargetSource.STATIC)
    }

    init {
        val dupes = targets.groupBy { it.id }.filterValues { it.size > 1 }.keys
        require(dupes.isEmpty()) { "Duplicate target names in ticker.targets: $dupes" }
    }

    fun all(): List<Target> = targets
}

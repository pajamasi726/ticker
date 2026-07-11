package io.stevelabs.ticker.server.graph

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** The service map's data: wall nodes + aggregated call edges. Cached in [GraphService]. */
@RestController
@RequestMapping("/api/graph")
class GraphController(private val service: GraphService) {

    @GetMapping
    fun graph(): ServiceGraph = service.graph()
}

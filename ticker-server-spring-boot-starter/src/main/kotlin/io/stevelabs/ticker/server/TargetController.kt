package io.stevelabs.ticker.server

import io.stevelabs.ticker.core.RegistrationRequest
import io.stevelabs.ticker.server.state.HealthStateStore
import io.stevelabs.ticker.server.target.RemoveResult
import io.stevelabs.ticker.server.target.Target
import io.stevelabs.ticker.server.target.TargetRegistry
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/targets")
class TargetController(
    private val registry: TargetRegistry,
    private val store: HealthStateStore,
) {
    @GetMapping
    fun list(): List<Target> = registry.all()

    @PostMapping
    fun register(@RequestBody request: RegistrationRequest): Target = registry.register(request)

    @DeleteMapping("/{id}")
    fun remove(@PathVariable id: String): ResponseEntity<Any> = when (registry.remove(id)) {
        RemoveResult.REMOVED -> {
            store.evict(id)
            ResponseEntity.noContent().build<Any>()
        }
        RemoveResult.NOT_FOUND -> ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body<Any>(ApiError("TARGET_NOT_FOUND", "No registered target with id '$id'"))
        RemoveResult.STATIC -> ResponseEntity.status(HttpStatus.CONFLICT)
            .body<Any>(ApiError("TARGET_STATIC", "Target '$id' is statically configured and cannot be removed"))
    }

    /** Bad/empty JSON body → {code, message} 400 (scoped to this controller). */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun onUnreadable(e: HttpMessageNotReadableException): ResponseEntity<ApiError> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiError("INVALID_REQUEST", "Request body is missing or malformed"))
}

package io.stevelabs.ticker.api

import io.stevelabs.ticker.api.dto.ServiceView
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/services")
class ServiceController(private val mockServices: MockServices) {

    @GetMapping
    fun list(): List<ServiceView> = mockServices.all()
}

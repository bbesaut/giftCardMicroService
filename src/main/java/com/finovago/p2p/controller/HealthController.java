package com.finovago.p2p.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "System", description = "System and health endpoints.")
public class HealthController {

    @Operation(
        summary = "Health check",
        description = "Checks the service health status. Used for monitoring and load balancer health probes. "
                    + "Always returns OK if the service is running."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK - Service is healthy and running"),
        @ApiResponse(responseCode = "503", description = "Service Unavailable - Service is down or not responding")
    })
    @GetMapping("/")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }
}


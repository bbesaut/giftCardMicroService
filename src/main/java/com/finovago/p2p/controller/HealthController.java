package com.finovago.p2p.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "System", description = "System and health endpoints.")
public class HealthController {
    
    @GetMapping("/")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }
}


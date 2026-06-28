package com.finovago.p2p.controller;

import com.finovago.p2p.dto.LoginRequest;
import com.finovago.p2p.security.JwtService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final JwtService jwtService;

    public AuthController(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@Valid @RequestBody LoginRequest request) {
        
        // 1. ICI : Tu fais tes vérifications pro en BDD
        // Exemple : User user = userService.findByEmail(request.getEmail());
        // Vérifier si le mot de passe match avec BCryptPasswordEncoder...
        
        // 2. Pour le test, on va simuler que l'utilisateur est valide.
        // On imagine qu'il s'appelle "boss@gmail.com" et qu'il est à la fois CLIENT et ADMIN.
        String username = request.email();
        List<String> roles = List.of("CLIENT","ADMIN"); 

        String token = jwtService.generateToken(username, roles);

        return ResponseEntity.ok(Map.of("token", token));
    }
}

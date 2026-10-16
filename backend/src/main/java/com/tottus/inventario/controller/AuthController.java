package com.tottus.inventario.controller;

import com.tottus.inventario.security.dto.LoginRequest;
import com.tottus.inventario.security.dto.LoginResponse;
import com.tottus.inventario.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        try {
            String ip = servletRequest.getRemoteAddr();
            LoginResponse response = authService.login(request, ip);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> getMe(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(Map.of("error", "No autenticado"));
        }
        return ResponseEntity.ok(Map.of(
                "usuario", authentication.getName(),
                "authorities", authentication.getAuthorities()
        ));
    }
}

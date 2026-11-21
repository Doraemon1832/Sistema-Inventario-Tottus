package com.tottus.inventario.security;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {
    private static final String TEST_SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    @Test
    void tokenConFirmaValidaYNoVencidoSeAcepta() {
        JwtService service = service(60_000);
        var user = User.withUsername("test-user").password("unused").roles("ADMINISTRADOR").build();
        String token = service.generateToken(user, Map.of());

        assertTrue(service.isTokenValid(token, user));
    }

    @Test
    void tokenManipuladoSeRechaza() {
        JwtService service = service(60_000);
        var user = User.withUsername("test-user").password("unused").roles("ADMINISTRADOR").build();
        String token = service.generateToken(user, Map.of());
        String[] parts = token.split("\\.");
        char first = parts[2].charAt(0);
        parts[2] = (first == 'a' ? "b" : "a") + parts[2].substring(1);
        String manipulated = String.join(".", parts);

        assertThrows(JwtException.class, () -> service.isTokenValid(manipulated, user));
    }

    @Test
    void tokenVencidoSeRechaza() throws InterruptedException {
        JwtService service = service(1);
        var user = User.withUsername("test-user").password("unused").roles("ADMINISTRADOR").build();
        String token = service.generateToken(user, Map.of());
        Thread.sleep(20);

        assertThrows(JwtException.class, () -> service.isTokenValid(token, user));
    }

    private JwtService service(long expirationMillis) {
        JwtService service = new JwtService();
        ReflectionTestUtils.setField(service, "secretKey", TEST_SECRET);
        ReflectionTestUtils.setField(service, "jwtExpiration", expirationMillis);
        return service;
    }
}

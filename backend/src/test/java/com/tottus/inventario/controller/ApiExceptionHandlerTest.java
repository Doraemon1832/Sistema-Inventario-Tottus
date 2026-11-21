package com.tottus.inventario.controller;

import com.tottus.inventario.security.LoginRateLimitException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiExceptionHandlerTest {
    @Test
    void loginLimitDevuelve429YRetryAfter() {
        var response = new ApiExceptionHandler().rateLimited(new LoginRateLimitException(317));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertEquals("317", response.getHeaders().getFirst("Retry-After"));
    }
}

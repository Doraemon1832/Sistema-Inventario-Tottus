package com.tottus.inventario.controller;

import com.tottus.inventario.dto.UsuarioAdminResponseDTO;
import com.tottus.inventario.security.dto.GlobalAdminBootstrapRequest;
import com.tottus.inventario.service.GlobalAdminBootstrapService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/bootstrap")
@RequiredArgsConstructor
public class GlobalAdminBootstrapController {
    private final GlobalAdminBootstrapService service;

    @PostMapping("/global")
    public UsuarioAdminResponseDTO bootstrap(@RequestHeader(name = "X-Global-Admin-Bootstrap") String token,
            @Valid @RequestBody GlobalAdminBootstrapRequest request) {
        return service.bootstrap(token, request.getPasswordInicial());
    }
}

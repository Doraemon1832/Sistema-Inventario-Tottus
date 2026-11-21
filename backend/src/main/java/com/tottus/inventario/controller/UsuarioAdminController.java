package com.tottus.inventario.controller;

import com.tottus.inventario.dto.AdminPasswordResetRequestDTO;
import com.tottus.inventario.dto.UsuarioAdminRequestDTO;
import com.tottus.inventario.dto.UsuarioAdminResponseDTO;
import com.tottus.inventario.dto.UsuarioAdminAccessUpdateRequestDTO;
import com.tottus.inventario.service.UsuarioAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/usuarios")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class UsuarioAdminController {
    private final UsuarioAdminService service;

    @GetMapping
    public List<UsuarioAdminResponseDTO> listar(@RequestParam(required = false) Integer sedeId,
            @RequestParam(required = false) String rol) { return service.listar(sedeId, rol); }

    @PostMapping
    public UsuarioAdminResponseDTO crear(@Valid @RequestBody UsuarioAdminRequestDTO request) { return service.crear(request); }

    @PutMapping("/{id}/acceso")
    public UsuarioAdminResponseDTO actualizarAcceso(@PathVariable Integer id,
            @Valid @RequestBody UsuarioAdminAccessUpdateRequestDTO request) { return service.actualizarAcceso(id, request); }

    @PatchMapping("/{id}/estado")
    public UsuarioAdminResponseDTO estado(@PathVariable Integer id, @RequestParam boolean activo) { return service.estado(id, activo); }

    @PutMapping("/{id}/password")
    public Map<String, String> restablecerPassword(@PathVariable Integer id, @Valid @RequestBody AdminPasswordResetRequestDTO request) {
        service.restablecerPassword(id, request.getPassword());
        return Map.of("message", "Contraseña restablecida. El usuario deberá cambiarla antes de operar.");
    }
}

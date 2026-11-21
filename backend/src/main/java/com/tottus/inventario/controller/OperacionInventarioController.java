package com.tottus.inventario.controller;

import com.tottus.inventario.dto.OperacionRequestDTO;
import com.tottus.inventario.dto.OperacionResponseDTO;
import com.tottus.inventario.dto.EgresoRechazoRequestDTO;
import com.tottus.inventario.service.OperacionInventarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

@RestController
@RequestMapping("/api/operaciones")
@RequiredArgsConstructor
public class OperacionInventarioController {
    private final OperacionInventarioService service;

    @PostMapping("/ingresos")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','JEFE_INVENTARIO','OPERADOR_ALMACEN')")
    public ResponseEntity<OperacionResponseDTO> registrarIngreso(@Valid @RequestBody OperacionRequestDTO request, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.registrarIngreso(request, auth.getName()));
    }
    @GetMapping("/ingresos")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','JEFE_INVENTARIO','OPERADOR_ALMACEN','VENDEDOR','ENCARGADO_COMPRAS')")
    public List<OperacionResponseDTO> listarIngresos(@RequestParam(required = false) Integer sedeId, @RequestParam(required = false) String query,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return service.listar("INGRESO", sedeId, query, desde, hasta);
    }
    @GetMapping("/ingresos/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','JEFE_INVENTARIO','OPERADOR_ALMACEN','VENDEDOR','ENCARGADO_COMPRAS')")
    public OperacionResponseDTO ingreso(@PathVariable Integer id, @RequestParam(required = false) Integer sedeId) { return service.obtener("INGRESO", id, sedeId); }
    @PutMapping("/ingresos/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','OPERADOR_ALMACEN')")
    public OperacionResponseDTO editarIngreso(@PathVariable Integer id, @Valid @RequestBody OperacionRequestDTO request, Authentication auth) { return service.editarIngreso(id, request, auth.getName()); }

    @PostMapping("/egresos")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','OPERADOR_ALMACEN','VENDEDOR')")
    public ResponseEntity<OperacionResponseDTO> registrarEgreso(@Valid @RequestBody OperacionRequestDTO request, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.registrarEgreso(request, auth.getName()));
    }
    @GetMapping("/egresos")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','JEFE_INVENTARIO','OPERADOR_ALMACEN','VENDEDOR')")
    public List<OperacionResponseDTO> listarEgresos(@RequestParam(required = false) Integer sedeId, @RequestParam(required = false) String query,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return service.listar("EGRESO", sedeId, query, desde, hasta);
    }
    @GetMapping("/egresos/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','JEFE_INVENTARIO','OPERADOR_ALMACEN','VENDEDOR')")
    public OperacionResponseDTO egreso(@PathVariable Integer id, @RequestParam(required = false) Integer sedeId) { return service.obtener("EGRESO", id, sedeId); }

    @PostMapping("/egresos/{id}/aprobar")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','JEFE_INVENTARIO')")
    public OperacionResponseDTO aprobarEgreso(@PathVariable Integer id, @RequestParam(required = false) Integer sedeId,
                                               Authentication auth) {
        return service.aprobarEgreso(id, sedeId, auth.getName());
    }

    @PostMapping("/egresos/{id}/rechazar")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','JEFE_INVENTARIO')")
    public OperacionResponseDTO rechazarEgreso(@PathVariable Integer id, @RequestParam(required = false) Integer sedeId,
                                                @Valid @RequestBody EgresoRechazoRequestDTO request, Authentication auth) {
        return service.rechazarEgreso(id, sedeId, auth.getName(), request.getMotivo());
    }
}

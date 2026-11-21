package com.tottus.inventario.controller;

import com.tottus.inventario.dto.InventarioResponseDTO;
import com.tottus.inventario.dto.MovimientoRequestDTO;
import com.tottus.inventario.dto.MovimientoResponseDTO;
import com.tottus.inventario.dto.MovimientoPageResponseDTO;
import com.tottus.inventario.dto.ResumenInventarioDTO;
import com.tottus.inventario.service.InventarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/inventario")
@RequiredArgsConstructor
public class InventarioController {

    private final InventarioService inventarioService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','JEFE_INVENTARIO','OPERADOR_ALMACEN','VENDEDOR')")
    public ResponseEntity<List<InventarioResponseDTO>> getInventario(
            @RequestParam(required = false) Integer sedeId,
            @RequestParam(required = false) Integer categoriaId,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String stockStatus
    ) {
        return ResponseEntity.ok(inventarioService.getInventario(sedeId, categoriaId, query, stockStatus));
    }

    @GetMapping("/resumen")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','JEFE_INVENTARIO')")
    public ResponseEntity<ResumenInventarioDTO> getResumen(@RequestParam(required = false) Integer sedeId) {
        return ResponseEntity.ok(inventarioService.getResumen(sedeId));
    }

    @PostMapping("/movimientos")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','OPERADOR_ALMACEN','VENDEDOR')")
    public ResponseEntity<MovimientoResponseDTO> registrarMovimiento(@Valid @RequestBody MovimientoRequestDTO request, Authentication authentication) {
        return ResponseEntity.ok(inventarioService.registrarMovimiento(request, authentication.getName()));
    }

    @GetMapping("/movimientos")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','JEFE_INVENTARIO')")
    public ResponseEntity<MovimientoPageResponseDTO> getMovimientos(
            @RequestParam(required = false) Integer sedeId,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String subtipo,
            @RequestParam(required = false) Integer productoId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size
    ) {
        return ResponseEntity.ok(subtipo == null || subtipo.isBlank() || "TODOS".equalsIgnoreCase(subtipo)
                ? inventarioService.getMovimientosPage(sedeId, tipo, productoId, desde, hasta, page, size)
                : inventarioService.getMovimientosPage(sedeId, tipo, subtipo, productoId, desde, hasta, page, size));
    }
}

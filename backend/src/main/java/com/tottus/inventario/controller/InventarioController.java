package com.tottus.inventario.controller;

import com.tottus.inventario.dto.InventarioResponseDTO;
import com.tottus.inventario.dto.MovimientoRequestDTO;
import com.tottus.inventario.dto.MovimientoResponseDTO;
import com.tottus.inventario.dto.ResumenInventarioDTO;
import com.tottus.inventario.service.InventarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inventario")
@RequiredArgsConstructor
public class InventarioController {

    private final InventarioService inventarioService;

    @GetMapping
    public ResponseEntity<List<InventarioResponseDTO>> getInventario(
            @RequestParam(required = false) Integer sedeId,
            @RequestParam(required = false) Integer categoriaId,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String stockStatus
    ) {
        return ResponseEntity.ok(inventarioService.getInventario(sedeId, categoriaId, query, stockStatus));
    }

    @GetMapping("/resumen")
    public ResponseEntity<ResumenInventarioDTO> getResumen(@RequestParam(required = false) Integer sedeId) {
        return ResponseEntity.ok(inventarioService.getResumen(sedeId));
    }

    @PostMapping("/movimientos")
    public ResponseEntity<?> registrarMovimiento(@RequestBody MovimientoRequestDTO request, Authentication authentication) {
        try {
            String username = authentication != null ? authentication.getName() : "gerencia_admin";
            MovimientoResponseDTO res = inventarioService.registrarMovimiento(request, username);
            return ResponseEntity.ok(res);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Error procesando movimiento: " + e.getMessage()));
        }
    }

    @GetMapping("/movimientos")
    public ResponseEntity<List<MovimientoResponseDTO>> getMovimientos(
            @RequestParam(required = false) Integer sedeId,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) Integer productoId
    ) {
        return ResponseEntity.ok(inventarioService.getMovimientos(sedeId, tipo, productoId));
    }
}

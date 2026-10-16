package com.tottus.inventario.controller;

import com.tottus.inventario.dto.ProductoRequestDTO;
import com.tottus.inventario.model.Categoria;
import com.tottus.inventario.model.Producto;
import com.tottus.inventario.service.ProductoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/productos")
@RequiredArgsConstructor
public class ProductoController {

    private final ProductoService productoService;

    @GetMapping
    public ResponseEntity<List<Producto>> listar(
            @RequestParam(required = false) Integer categoriaId,
            @RequestParam(required = false) String query
    ) {
        return ResponseEntity.ok(productoService.listarProductos(categoriaId, query));
    }

    @GetMapping("/categorias")
    public ResponseEntity<List<Categoria>> categorias() {
        return ResponseEntity.ok(productoService.listarCategorias());
    }

    @PostMapping
    public ResponseEntity<?> crear(@RequestBody ProductoRequestDTO dto, Authentication authentication) {
        try {
            String username = authentication != null ? authentication.getName() : "gerencia_admin";
            Producto saved = productoService.guardarProducto(dto, username);
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(
            @PathVariable Integer id,
            @RequestBody ProductoRequestDTO dto,
            Authentication authentication
    ) {
        try {
            String username = authentication != null ? authentication.getName() : "gerencia_admin";
            Producto updated = productoService.actualizarProducto(id, dto, username);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/{id}/estado")
    public ResponseEntity<?> cambiarEstado(
            @PathVariable Integer id,
            @RequestParam boolean activo,
            Authentication authentication
    ) {
        try {
            String username = authentication != null ? authentication.getName() : "gerencia_admin";
            Producto updated = productoService.cambiarEstadoProducto(id, activo, username);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}

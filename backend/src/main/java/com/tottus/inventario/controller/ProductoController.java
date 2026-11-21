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
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/productos")
@RequiredArgsConstructor
public class ProductoController {

    private final ProductoService productoService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','OPERADOR_ALMACEN','ENCARGADO_COMPRAS')")
    public ResponseEntity<List<Producto>> listar(
            @RequestParam(required = false) Integer categoriaId,
            @RequestParam(required = false) String query
    ) {
        return ResponseEntity.ok(productoService.listarProductos(categoriaId, query));
    }

    @GetMapping("/categorias")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','OPERADOR_ALMACEN')")
    public ResponseEntity<List<Categoria>> categorias() {
        return ResponseEntity.ok(productoService.listarCategorias());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','OPERADOR_ALMACEN')")
    public ResponseEntity<?> crear(@Valid @RequestBody ProductoRequestDTO dto, Authentication authentication) {
        Producto saved = productoService.guardarProducto(dto, authentication.getName());
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','OPERADOR_ALMACEN')")
    public ResponseEntity<?> actualizar(
            @PathVariable Integer id,
            @Valid @RequestBody ProductoRequestDTO dto,
            Authentication authentication
    ) {
        Producto updated = productoService.actualizarProducto(id, dto, authentication.getName());
        return ResponseEntity.ok(updated);
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','OPERADOR_ALMACEN')")
    public ResponseEntity<?> cambiarEstado(
            @PathVariable Integer id,
            @RequestParam boolean activo,
            Authentication authentication
    ) {
        Producto updated = productoService.cambiarEstadoProducto(id, activo, authentication.getName());
        return ResponseEntity.ok(updated);
    }
}

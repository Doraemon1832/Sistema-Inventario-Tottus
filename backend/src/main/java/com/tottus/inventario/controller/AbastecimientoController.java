package com.tottus.inventario.controller;

import com.tottus.inventario.dto.*;
import com.tottus.inventario.model.Proveedor;
import com.tottus.inventario.service.AbastecimientoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController @RequiredArgsConstructor
public class AbastecimientoController {
    private final AbastecimientoService service;
    @GetMapping("/api/proveedores") @PreAuthorize("hasAnyRole('ADMINISTRADOR','JEFE_INVENTARIO','ENCARGADO_COMPRAS','OPERADOR_ALMACEN','VENDEDOR')") public List<Proveedor> proveedores(@RequestParam(required=false) String query,@RequestParam(required=false) Boolean activos){return service.listarProveedores(query,activos);}
    @PostMapping("/api/proveedores") @PreAuthorize("hasAnyRole('ADMINISTRADOR','ENCARGADO_COMPRAS')") public Proveedor crearProveedor(@Valid @RequestBody ProveedorRequestDTO req){return service.guardarProveedor(null,req);}
    @PutMapping("/api/proveedores/{id}") @PreAuthorize("hasAnyRole('ADMINISTRADOR','ENCARGADO_COMPRAS')") public Proveedor editarProveedor(@PathVariable Integer id,@Valid @RequestBody ProveedorRequestDTO req){return service.guardarProveedor(id,req);}
    @PatchMapping("/api/proveedores/{id}/estado") @PreAuthorize("hasAnyRole('ADMINISTRADOR','ENCARGADO_COMPRAS')") public Proveedor estadoProveedor(@PathVariable Integer id,@RequestParam boolean activo){return service.estadoProveedor(id,activo);}

    @GetMapping("/api/compras") @PreAuthorize("hasAnyRole('ADMINISTRADOR','JEFE_INVENTARIO','ENCARGADO_COMPRAS')") public List<Map<String,Object>> compras(@RequestParam(required=false) Integer sedeId,@RequestParam(required=false) String query){return service.listarCompras(sedeId,query);}
    @PostMapping("/api/compras") @PreAuthorize("hasAnyRole('ADMINISTRADOR','ENCARGADO_COMPRAS')") public Map<String,Object> crearCompra(@Valid @RequestBody CompraRequestDTO req,Authentication auth){return service.crearCompra(req,auth.getName());}
    @PutMapping("/api/compras/{id}") @PreAuthorize("hasAnyRole('ADMINISTRADOR','ENCARGADO_COMPRAS')") public Map<String,Object> editarCompra(@PathVariable Integer id,@Valid @RequestBody CompraRequestDTO req){return service.editarCompra(id,req);}
    @PostMapping("/api/compras/{id}/recepcion") @PreAuthorize("hasAnyRole('ADMINISTRADOR','JEFE_INVENTARIO','OPERADOR_ALMACEN')") public Map<String,Object> recibir(@PathVariable Integer id,@RequestParam(required=false) Integer sedeId,Authentication auth){return service.recibirCompra(id,sedeId,auth.getName());}
}

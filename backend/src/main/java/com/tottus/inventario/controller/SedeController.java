package com.tottus.inventario.controller;

import com.tottus.inventario.model.Sede;
import com.tottus.inventario.security.SedeAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/sedes")
@RequiredArgsConstructor
public class SedeController {

    private final SedeAccessService sedeAccessService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','JEFE_INVENTARIO','OPERADOR_ALMACEN','VENDEDOR','ENCARGADO_COMPRAS')")
    public ResponseEntity<List<Sede>> listar() {
        return ResponseEntity.ok(sedeAccessService.authorizedSedes());
    }
}

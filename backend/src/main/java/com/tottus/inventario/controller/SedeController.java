package com.tottus.inventario.controller;

import com.tottus.inventario.model.Sede;
import com.tottus.inventario.service.SedeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/sedes")
@RequiredArgsConstructor
public class SedeController {

    private final SedeService sedeService;

    @GetMapping
    public ResponseEntity<List<Sede>> listar() {
        return ResponseEntity.ok(sedeService.listarSedes());
    }
}

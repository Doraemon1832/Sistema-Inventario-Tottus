package com.tottus.inventario.controller;

import com.tottus.inventario.dto.*;
import com.tottus.inventario.service.TrasladoService;
import com.tottus.inventario.model.Sede;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/traslados") @RequiredArgsConstructor
public class TrasladoController {
    private final TrasladoService service;

    @GetMapping("/sedes-destino") @PreAuthorize("hasAnyRole('ADMINISTRADOR','JEFE_INVENTARIO','OPERADOR_ALMACEN')")
    public List<Sede> sedesDestino() { return service.listarSedesDestino(); }

    @GetMapping @PreAuthorize("hasAnyRole('ADMINISTRADOR','JEFE_INVENTARIO','OPERADOR_ALMACEN')")
    public List<TrasladoResponseDTO> listar(@RequestParam(required = false) String estado) { return service.listar(estado); }

    @GetMapping("/{id}") @PreAuthorize("hasAnyRole('ADMINISTRADOR','JEFE_INVENTARIO','OPERADOR_ALMACEN')")
    public TrasladoResponseDTO obtener(@PathVariable Integer id) { return service.obtener(id); }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','JEFE_INVENTARIO','OPERADOR_ALMACEN')")
    public TrasladoResponseDTO crear(@Valid @RequestBody TrasladoCreateRequestDTO request, Authentication auth) {
        return service.crear(request, auth.getName());
    }

    @PostMapping("/{id}/despacho") @PreAuthorize("hasAnyRole('ADMINISTRADOR','OPERADOR_ALMACEN')")
    public TrasladoResponseDTO despachar(@PathVariable Integer id, @Valid @RequestBody TrasladoActionRequestDTO request, Authentication auth) {
        return service.despachar(id, request, auth.getName());
    }

    @PostMapping("/{id}/recepcion") @PreAuthorize("hasAnyRole('ADMINISTRADOR','JEFE_INVENTARIO','OPERADOR_ALMACEN')")
    public TrasladoResponseDTO recibir(@PathVariable Integer id, @Valid @RequestBody TrasladoActionRequestDTO request, Authentication auth) {
        return service.recibir(id, request, auth.getName());
    }

    @PostMapping("/{id}/cancelacion") @PreAuthorize("hasAnyRole('ADMINISTRADOR','JEFE_INVENTARIO','OPERADOR_ALMACEN')")
    public TrasladoResponseDTO cancelar(@PathVariable Integer id, @Valid @RequestBody TrasladoActionRequestDTO request, Authentication auth) {
        return service.cancelar(id, request, auth.getName());
    }
}

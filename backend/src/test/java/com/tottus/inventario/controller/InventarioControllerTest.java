package com.tottus.inventario.controller;

import com.tottus.inventario.dto.InventarioResponseDTO;
import com.tottus.inventario.dto.MovimientoResponseDTO;
import com.tottus.inventario.dto.MovimientoPageResponseDTO;
import com.tottus.inventario.service.InventarioService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventarioControllerTest {

    @Mock
    private InventarioService inventarioService;

    @InjectMocks
    private InventarioController controller;

    @Test
    void getMovimientosDevuelveKardexDeLaSedeTipoYProductoSolicitados() {
        MovimientoResponseDTO movimiento = MovimientoResponseDTO.builder()
                .idMovimiento(51)
                .idSede(4)
                .idProducto(17)
                .tipo("SALIDA")
                .cantidad(2)
                .referencia("Venta V-51")
                .build();
        var page = MovimientoPageResponseDTO.builder().content(List.of(movimiento)).page(0).size(25).totalElements(1).totalPages(1).build();
        when(inventarioService.getMovimientosPage(4, "SALIDA", 17, null, null, 0, 25)).thenReturn(page);

        var respuesta = controller.getMovimientos(4, "SALIDA", null, 17, null, null, 0, 25);

        assertEquals(HttpStatus.OK, respuesta.getStatusCode());
        var body = Objects.requireNonNull(respuesta.getBody(), "el endpoint debe devolver una página");
        assertEquals(List.of(movimiento), body.getContent());
        assertEquals(1, body.getTotalElements());
        verify(inventarioService).getMovimientosPage(4, "SALIDA", 17, null, null, 0, 25);
    }

    @Test
    void enviaFiltroDeMotivoAlServicioPaginado() {
        var page = MovimientoPageResponseDTO.builder().content(List.of()).page(0).size(25).totalElements(0).totalPages(0).build();
        when(inventarioService.getMovimientosPage(4, "ENTRADA", "COMPRA", 17, null, null, 0, 25)).thenReturn(page);

        var respuesta = controller.getMovimientos(4, "ENTRADA", "COMPRA", 17, null, null, 0, 25);

        assertEquals(HttpStatus.OK, respuesta.getStatusCode());
        verify(inventarioService).getMovimientosPage(4, "ENTRADA", "COMPRA", 17, null, null, 0, 25);
    }

    @Test
    void getInventarioDevuelveStockDeLaSedeConIdentidadDeProducto() {
        InventarioResponseDTO inventario = InventarioResponseDTO.builder()
                .idInventario(90)
                .idSede(6)
                .nombreSede("Sede Centro")
                .idProducto(32)
                .codigoProducto("SKU-32")
                .nombreProducto("Producto óptimo")
                .stockActual(6)
                .stockMinimo(5)
                .estadoStock("OPTIMO")
                .build();
        when(inventarioService.getInventario(6, null, null, null)).thenReturn(List.of(inventario));

        var respuesta = controller.getInventario(6, null, null, null);

        assertEquals(HttpStatus.OK, respuesta.getStatusCode());
        var body = Objects.requireNonNull(respuesta.getBody(), "el endpoint debe devolver el inventario");
        assertEquals(1, body.size());
        assertEquals(6, body.get(0).getIdSede());
        assertEquals("Sede Centro", body.get(0).getNombreSede());
        assertEquals(32, body.get(0).getIdProducto());
        assertEquals("SKU-32", body.get(0).getCodigoProducto());
        assertEquals(6, body.get(0).getStockActual());
        verify(inventarioService).getInventario(6, null, null, null);
    }
}

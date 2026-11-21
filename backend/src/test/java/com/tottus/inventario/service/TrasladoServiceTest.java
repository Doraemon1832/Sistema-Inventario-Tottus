package com.tottus.inventario.service;

import com.tottus.inventario.dto.TrasladoActionRequestDTO;
import com.tottus.inventario.dto.TrasladoCreateRequestDTO;
import com.tottus.inventario.dto.TrasladoItemRequestDTO;
import com.tottus.inventario.model.*;
import com.tottus.inventario.repository.*;
import com.tottus.inventario.security.SedeAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrasladoServiceTest {
    @Mock private TrasladoRepository transferRepo;
    @Mock private DetalleTrasladoRepository detailRepo;
    @Mock private TrasladoEventoRepository eventRepo;
    @Mock private InventarioRepository inventoryRepo;
    @Mock private ProductoRepository productRepo;
    @Mock private SedeRepository siteRepo;
    @Mock private UsuarioRepository userRepo;
    @Mock private MovimientoRepository movementRepo;
    @Mock private SedeAccessService siteAccess;
    private TrasladoService service;
    private Sede source;
    private Sede destination;
    private Usuario user;
    private Producto product;
    private Traslado transfer;
    private DetalleTraslado line;

    @BeforeEach void setUp() {
        service = new TrasladoService(transferRepo, detailRepo, eventRepo, inventoryRepo, productRepo,
                siteRepo, userRepo, movementRepo, siteAccess);
        source = Sede.builder().idSede(1).nombre("Origen").estado(true).build();
        destination = Sede.builder().idSede(2).nombre("Destino").estado(true).build();
        user = Usuario.builder().idUsuario(5).usuario("operador01").estado(true).build();
        product = Producto.builder().idProducto(10).codigo("SKU-10").nombre("Leche").estado(true).unidadMedida("caja").build();
        transfer = Traslado.builder().idTraslado(44).codigo("TR-44").sedeOrigen(source).sedeDestino(destination)
                .usuarioSolicita(user).estado("PENDIENTE").referencia("GUIA-44").fechaSolicitud(java.time.LocalDateTime.now()).build();
        line = DetalleTraslado.builder().idDetalleTraslado(7).traslado(transfer).producto(product)
                .cantidadSolicitada(5).cantidadDespachada(0).cantidadRecibida(0).build();
    }

    @Test void noPermiteTrasladoHaciaLaMismaSede() {
        var request = TrasladoCreateRequestDTO.builder().idSedeOrigen(1).idSedeDestino(1)
                .referencia("GUIA").detalles(List.of(item(10, 1))).build();
        assertThrows(IllegalArgumentException.class, () -> service.crear(request, "operador01"));
        verifyNoInteractions(siteAccess, transferRepo, inventoryRepo, movementRepo);
    }

    @Test void crearSolicitudNoModificaStock() {
        when(siteAccess.requireAuthorizedSede(1)).thenReturn(1);
        when(siteRepo.findById(1)).thenReturn(Optional.of(source));
        when(siteRepo.findById(2)).thenReturn(Optional.of(destination));
        when(productRepo.findAllById(any())).thenReturn(List.of(product));
        when(transferRepo.findBySedeOrigen_IdSedeAndReferencia(1, "GUIA-44")).thenReturn(Optional.empty());
        when(userRepo.findByUsuario("operador01")).thenReturn(Optional.of(user));
        when(transferRepo.save(any())).thenAnswer(invocation -> {
            Traslado saved = invocation.getArgument(0); saved.setIdTraslado(44); return saved;
        });
        when(detailRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(detailRepo.findByTraslado_IdTrasladoOrderByIdDetalleTraslado(44)).thenReturn(List.of(line));
        var request = TrasladoCreateRequestDTO.builder().idSedeOrigen(1).idSedeDestino(2).referencia("GUIA-44")
                .detalles(List.of(item(10, 5))).build();

        var result = service.crear(request, "operador01");

        assertEquals("PENDIENTE", result.getEstado());
        verify(inventoryRepo, never()).save(any());
        verify(movementRepo, never()).save(any());
    }

    @Test void despachoActualizaOrigenYUnaRepeticionIdempotenteNoDuplicaMovimiento() {
        line.setCantidadDespachada(0);
        Inventario stock = Inventario.builder().idInventario(1).sede(source).producto(product).stockActual(12).build();
        when(transferRepo.findForUpdate(44)).thenReturn(Optional.of(transfer));
        when(siteAccess.requireAuthorizedSede(1)).thenReturn(1);
        when(eventRepo.findByClaveIdempotencia("dispatch-44")).thenReturn(Optional.empty());
        when(userRepo.findByUsuario("operador01")).thenReturn(Optional.of(user));
        when(detailRepo.findByTraslado_IdTrasladoOrderByIdDetalleTraslado(44)).thenReturn(List.of(line));
        when(inventoryRepo.findForUpdate(1, 10)).thenReturn(Optional.of(stock));
        when(transferRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(eventRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var request = TrasladoActionRequestDTO.builder().idempotencyKey("dispatch-44").build();

        var first = service.despachar(44, request, "operador01");
        ArgumentCaptor<TrasladoEvento> eventCaptor = ArgumentCaptor.forClass(TrasladoEvento.class);
        verify(eventRepo).save(eventCaptor.capture());
        when(eventRepo.findByClaveIdempotencia("dispatch-44")).thenReturn(Optional.of(eventCaptor.getValue()));
        var replay = service.despachar(44, request, "operador01");

        assertEquals("EN_TRANSITO", first.getEstado());
        assertEquals("EN_TRANSITO", replay.getEstado());
        assertEquals(7, stock.getStockActual());
        assertEquals(5, line.getCantidadDespachada());
        ArgumentCaptor<Movimiento> movementCaptor = ArgumentCaptor.forClass(Movimiento.class);
        verify(movementRepo, times(1)).save(movementCaptor.capture());
        assertEquals(12, movementCaptor.getValue().getStockAnterior());
        assertEquals(7, movementCaptor.getValue().getStockPosterior());
        assertEquals("TRANSFERENCIA_ENVIADA", movementCaptor.getValue().getSubtipo());
    }

    @Test void recepcionParcialSumaDestinoYConservaCantidadEnTransito() {
        transfer.setEstado("EN_TRANSITO");
        line.setCantidadDespachada(5);
        Inventario stock = Inventario.builder().idInventario(2).sede(destination).producto(product).stockActual(3).build();
        when(transferRepo.findForUpdate(44)).thenReturn(Optional.of(transfer));
        when(siteAccess.requireAuthorizedSede(2)).thenReturn(2);
        when(eventRepo.findByClaveIdempotencia("receive-44")).thenReturn(Optional.empty());
        when(userRepo.findByUsuario("operador01")).thenReturn(Optional.of(user));
        when(detailRepo.findByTraslado_IdTrasladoOrderByIdDetalleTraslado(44)).thenReturn(List.of(line));
        when(inventoryRepo.findForUpdate(2, 10)).thenReturn(Optional.of(stock));
        when(transferRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(eventRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var request = TrasladoActionRequestDTO.builder().idempotencyKey("receive-44")
                .detalles(List.of(item(10, 2))).build();

        var result = service.recibir(44, request, "operador01");

        assertEquals("RECIBIDO_PARCIAL", result.getEstado());
        assertEquals(5, stock.getStockActual());
        assertEquals(2, line.getCantidadRecibida());
        assertEquals(3, result.getDetalles().getFirst().getCantidadEnTransito());
        verify(movementRepo).save(argThat(m -> "TRANSFERENCIA_RECIBIDA".equals(m.getSubtipo())
                && Integer.valueOf(3).equals(m.getStockAnterior()) && Integer.valueOf(5).equals(m.getStockPosterior())));
    }

    @Test void rechazaRecepcionMayorALoEnviadoSinModificarDestino() {
        transfer.setEstado("EN_TRANSITO");
        line.setCantidadDespachada(5);
        when(transferRepo.findForUpdate(44)).thenReturn(Optional.of(transfer));
        when(siteAccess.requireAuthorizedSede(2)).thenReturn(2);
        when(eventRepo.findByClaveIdempotencia("receive-over")).thenReturn(Optional.empty());
        when(userRepo.findByUsuario("operador01")).thenReturn(Optional.of(user));
        when(detailRepo.findByTraslado_IdTrasladoOrderByIdDetalleTraslado(44)).thenReturn(List.of(line));
        var request = TrasladoActionRequestDTO.builder().idempotencyKey("receive-over")
                .detalles(List.of(item(10, 6))).build();

        assertThrows(IllegalArgumentException.class, () -> service.recibir(44, request, "operador01"));
        verify(inventoryRepo, never()).save(any());
        verify(movementRepo, never()).save(any());
    }

    private TrasladoItemRequestDTO item(Integer productId, Integer quantity) {
        return TrasladoItemRequestDTO.builder().idProducto(productId).cantidad(quantity).build();
    }
}

package com.tottus.inventario.service;

import com.tottus.inventario.dto.DetalleOperacionRequestDTO;
import com.tottus.inventario.dto.OperacionRequestDTO;
import com.tottus.inventario.model.*;
import com.tottus.inventario.repository.*;
import com.tottus.inventario.security.SedeAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OperacionInventarioServiceTest {
    @Mock private OperacionInventarioRepository operationRepo;
    @Mock private DetalleOperacionInventarioRepository detailRepo;
    @Mock private InventarioRepository inventoryRepo;
    @Mock private MovimientoRepository movementRepo;
    @Mock private ProductoRepository productRepo;
    @Mock private SedeRepository sedeRepo;
    @Mock private UsuarioRepository userRepo;
    @Mock private ProveedorRepository proveedorRepo;
    @Mock private AuditoriaRepository auditRepo;
    @Mock private SedeAccessService sedeAccess;
    private OperacionInventarioService service;

    @BeforeEach void setup() {
        service = new OperacionInventarioService(operationRepo, detailRepo, inventoryRepo, movementRepo,
                productRepo, sedeRepo, userRepo, proveedorRepo, auditRepo, sedeAccess);
        lenient().when(sedeAccess.requireAuthorizedSede(3)).thenReturn(3);
    }

    @Test void rechazaProductoDuplicadoAntesDePersistir() {
        OperacionRequestDTO request = OperacionRequestDTO.builder().idSede(3).subtipo("INGRESO_GENERAL").referencia("R-1")
                .detalles(List.of(line(9, 2), line(9, 4))).build();
        assertThrows(IllegalArgumentException.class, () -> service.registrarIngreso(request, "operador"));
        verifyNoInteractions(operationRepo, detailRepo, inventoryRepo, movementRepo, productRepo, sedeRepo, userRepo, proveedorRepo, auditRepo);
    }

    @Test void rechazaCantidadNegativaAntesDePersistir() {
        OperacionRequestDTO request = OperacionRequestDTO.builder().idSede(3).subtipo("VENTA").referencia("R-2")
                .detalles(List.of(line(9, -1))).build();
        assertThrows(IllegalArgumentException.class, () -> service.registrarEgreso(request, "operador"));
        verifyNoInteractions(operationRepo, detailRepo, inventoryRepo, movementRepo, productRepo, sedeRepo, userRepo, proveedorRepo, auditRepo);
    }

    @Test void impideRegistrarCompraComoIngresoManual() {
        OperacionRequestDTO request = OperacionRequestDTO.builder().idSede(3).subtipo("COMPRA").referencia("R-3")
                .detalles(List.of(line(9, 1))).build();
        assertThrows(IllegalArgumentException.class, () -> service.registrarIngreso(request, "operador"));
        verifyNoInteractions(operationRepo, detailRepo, inventoryRepo, movementRepo, productRepo, sedeRepo, userRepo, proveedorRepo, auditRepo);
    }

    @Test void vendedorNoPuedeRegistrarMermaAunqueLlameAlServicioDirectamente() {
        Usuario seller = Usuario.builder().idUsuario(5).usuario("vendedor")
                .rol(Rol.builder().nombre("VENDEDOR").estado(true).build()).build();
        when(userRepo.findByUsuario("vendedor")).thenReturn(Optional.of(seller));
        OperacionRequestDTO request = OperacionRequestDTO.builder().idSede(3).subtipo("MERMA")
                .motivo("Daño verificado").referencia("SELLER-1").detalles(List.of(line(9, 1))).build();
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.registrarEgreso(request, "vendedor"));
        verifyNoInteractions(operationRepo, detailRepo, inventoryRepo, movementRepo, productRepo, sedeRepo, proveedorRepo, auditRepo);
    }

    @Test void vendedorNoPuedeRegistrarConsumoInterno() {
        Usuario seller = Usuario.builder().idUsuario(5).usuario("vendedor")
                .rol(Rol.builder().nombre("VENDEDOR").estado(true).build()).build();
        when(userRepo.findByUsuario("vendedor")).thenReturn(Optional.of(seller));
        OperacionRequestDTO request = OperacionRequestDTO.builder().idSede(3).subtipo("CONSUMO_INTERNO")
                .motivo("Uso interno").referencia("SELLER-2").detalles(List.of(line(9, 1))).build();
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.registrarEgreso(request, "vendedor"));
        verify(operationRepo, never()).save(any());
        verifyNoInteractions(inventoryRepo, movementRepo, productRepo, sedeRepo, proveedorRepo, auditRepo);
    }

    @Test void vendedorSoloConsultaSusVentasDeLaSede() {
        Usuario seller = Usuario.builder().idUsuario(5).usuario("vendedor")
                .rol(Rol.builder().nombre("VENDEDOR").estado(true).build()).build();
        Usuario anotherSeller = Usuario.builder().idUsuario(6).usuario("otro")
                .rol(Rol.builder().nombre("VENDEDOR").estado(true).build()).build();
        Sede site = Sede.builder().idSede(3).nombre("Centro").build();
        when(sedeAccess.requireAuthorizedSede(3)).thenReturn(3);
        when(sedeAccess.authenticatedUser()).thenReturn(seller);
        when(operationRepo.findByTipoAndSede_IdSedeOrderByFechaDesc("EGRESO", 3)).thenReturn(List.of(
                OperacionInventario.builder().idOperacion(1).tipo("EGRESO").subtipo("VENTA").sede(site).usuario(seller)
                        .referencia("OWN").fecha(java.time.LocalDateTime.now()).build(),
                OperacionInventario.builder().idOperacion(2).tipo("EGRESO").subtipo("VENTA").sede(site).usuario(anotherSeller)
                        .referencia("OTHER").fecha(java.time.LocalDateTime.now()).build(),
                OperacionInventario.builder().idOperacion(3).tipo("EGRESO").subtipo("MERMA").sede(site).usuario(seller)
                        .referencia("WASTE").fecha(java.time.LocalDateTime.now()).build()));
        when(detailRepo.findByOperacion_IdOperacionOrderByIdDetalle(anyInt())).thenReturn(List.of());

        var visible = service.listar("EGRESO", 3, null, null, null);
        assertEquals(List.of(1), visible.stream().map(item -> item.getIdOperacion()).toList());
    }

    @Test void encargadoComprasSoloConsultaRecepcionesDeCompra() {
        Usuario buyer = Usuario.builder().idUsuario(8).usuario("compras")
                .rol(Rol.builder().nombre("ENCARGADO_COMPRAS").estado(true).build()).build();
        Sede site = Sede.builder().idSede(3).nombre("Centro").build();
        when(sedeAccess.requireAuthorizedSede(3)).thenReturn(3);
        when(sedeAccess.authenticatedUser()).thenReturn(buyer);
        when(operationRepo.findByTipoAndSede_IdSedeOrderByFechaDesc("INGRESO", 3)).thenReturn(List.of(
                OperacionInventario.builder().idOperacion(4).tipo("INGRESO").subtipo("COMPRA").sede(site).usuario(buyer)
                        .referencia("PURCHASE").fecha(java.time.LocalDateTime.now()).build(),
                OperacionInventario.builder().idOperacion(5).tipo("INGRESO").subtipo("PRODUCCION").sede(site).usuario(buyer)
                        .referencia("PRODUCTION").fecha(java.time.LocalDateTime.now()).build()));
        when(detailRepo.findByOperacion_IdOperacionOrderByIdDetalle(anyInt())).thenReturn(List.of());

        var visible = service.listar("INGRESO", 3, null, null, null);
        assertEquals(List.of(4), visible.stream().map(item -> item.getIdOperacion()).toList());
    }

    @Test void operadorCreaSolicitudSensibleSinDescontarStockYJefeLaApruebaUnaSolaVez() {
        Sede site = Sede.builder().idSede(3).nombre("Centro").build();
        Producto product = Producto.builder().idProducto(9).codigo("SKU-9").nombre("Producto").estado(true).unidadMedida("unidad").build();
        Usuario operator = Usuario.builder().idUsuario(4).usuario("operador").rol(Rol.builder().nombre("OPERADOR_ALMACEN").build()).build();
        Usuario manager = Usuario.builder().idUsuario(6).usuario("jefe").rol(Rol.builder().nombre("JEFE_INVENTARIO").build()).build();
        OperacionInventario pending = OperacionInventario.builder().idOperacion(55).tipo("EGRESO").subtipo("MERMA")
                .estado("PENDIENTE").sede(site).usuario(operator).referencia("MERMA-55").fecha(java.time.LocalDateTime.now()).build();
        when(userRepo.findByUsuario("operador")).thenReturn(Optional.of(operator));
        when(operationRepo.findByTipoAndReferencia("EGRESO", "MERMA-55")).thenReturn(Optional.empty());
        when(sedeRepo.findById(3)).thenReturn(Optional.of(site));
        when(productRepo.findAllById(any())).thenReturn(List.of(product));
        when(operationRepo.save(any())).thenAnswer(inv -> { OperacionInventario op = inv.getArgument(0); op.setIdOperacion(55); return op; });
        when(detailRepo.findByOperacion_IdOperacionOrderByIdDetalle(55)).thenReturn(List.of());
        var request = OperacionRequestDTO.builder().idSede(3).subtipo("MERMA").motivo("Daño").referencia("MERMA-55").detalles(List.of(line(9, 2))).build();

        var created = service.registrarEgreso(request, "operador");

        assertEquals("PENDIENTE", created.getEstado());
        verify(detailRepo).save(any(DetalleOperacionInventario.class));
        verifyNoInteractions(inventoryRepo, movementRepo);

        Inventario inventory = Inventario.builder().idInventario(1).sede(site).producto(product).stockActual(10).build();
        DetalleOperacionInventario detail = DetalleOperacionInventario.builder().operacion(pending).producto(product).cantidad(2).build();
        when(operationRepo.findForUpdate(55)).thenReturn(Optional.of(pending));
        when(sedeAccess.requireAuthorizedSede(3)).thenReturn(3);
        when(userRepo.findByUsuario("jefe")).thenReturn(Optional.of(manager));
        when(detailRepo.findByOperacion_IdOperacionOrderByIdDetalle(55)).thenReturn(List.of(detail));
        when(inventoryRepo.findForUpdate(3, 9)).thenReturn(Optional.of(inventory));

        var approved = service.aprobarEgreso(55, 3, "jefe");
        assertEquals("CONFIRMADA", approved.getEstado());
        assertEquals(8, inventory.getStockActual());
        verify(movementRepo, times(1)).save(any(Movimiento.class));

        service.aprobarEgreso(55, 3, "jefe");
        verify(movementRepo, times(1)).save(any(Movimiento.class));
    }

    @Test void operadorNoPuedeAprobarSuPropiaSolicitud() {
        Usuario operator = Usuario.builder().idUsuario(4).usuario("operador").rol(Rol.builder().nombre("OPERADOR_ALMACEN").build()).build();
        OperacionInventario pending = OperacionInventario.builder().idOperacion(56).tipo("EGRESO").subtipo("MERMA").estado("PENDIENTE")
                .sede(Sede.builder().idSede(3).build()).usuario(operator).referencia("MERMA-56").build();
        when(operationRepo.findForUpdate(56)).thenReturn(Optional.of(pending));
        when(sedeAccess.requireAuthorizedSede(3)).thenReturn(3);
        when(userRepo.findByUsuario("operador")).thenReturn(Optional.of(operator));
        org.springframework.security.access.AccessDeniedException denied = assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.aprobarEgreso(56, 3, "operador"));
        assertEquals("No puedes aprobar o rechazar tu propia solicitud", denied.getMessage());
        verifyNoInteractions(inventoryRepo, movementRepo);
    }

    @Test void solicitudDeMermaExigeMotivoAntesDeGuardar() {
        Usuario operator = Usuario.builder().idUsuario(4).usuario("operador")
                .rol(Rol.builder().nombre("OPERADOR_ALMACEN").build()).build();
        when(userRepo.findByUsuario("operador")).thenReturn(Optional.of(operator));
        OperacionRequestDTO request = OperacionRequestDTO.builder().idSede(3).subtipo("MERMA")
                .referencia("MERMA-SIN-MOTIVO").detalles(List.of(line(9, 1))).build();

        assertThrows(IllegalArgumentException.class, () -> service.registrarEgreso(request, "operador"));
        verifyNoInteractions(operationRepo, detailRepo, inventoryRepo, movementRepo, productRepo, sedeRepo, proveedorRepo, auditRepo);
    }

    @Test void rechazoConMotivoNoDescuentaStockNiCreaKardexYEsIdempotente() {
        Usuario operator = Usuario.builder().idUsuario(4).usuario("operador").build();
        Usuario manager = Usuario.builder().idUsuario(6).usuario("jefe").build();
        OperacionInventario pending = OperacionInventario.builder().idOperacion(57).tipo("EGRESO").subtipo("MERMA")
                .estado("PENDIENTE").sede(Sede.builder().idSede(3).nombre("Centro").build()).usuario(operator)
                .referencia("MERMA-57").fecha(java.time.LocalDateTime.now()).build();
        when(operationRepo.findForUpdate(57)).thenReturn(Optional.of(pending));
        when(sedeAccess.requireAuthorizedSede(3)).thenReturn(3);
        when(userRepo.findByUsuario("jefe")).thenReturn(Optional.of(manager));
        when(detailRepo.findByOperacion_IdOperacionOrderByIdDetalle(57)).thenReturn(List.of());
        when(operationRepo.save(pending)).thenReturn(pending);

        var rejected = service.rechazarEgreso(57, 3, "jefe", "Falta evidencia de la merma");
        assertEquals("RECHAZADA", rejected.getEstado());
        assertEquals("Falta evidencia de la merma", rejected.getMotivoRevision());
        service.rechazarEgreso(57, 3, "jefe", "Falta evidencia de la merma");

        verify(operationRepo, times(1)).save(pending);
        verifyNoInteractions(inventoryRepo, movementRepo);
        verify(auditRepo, times(1)).save(any(Auditoria.class));
    }

    static Stream<String> manualSubtypes() {
        return Stream.of("DEVOLUCION_CLIENTE", "PRODUCCION", "BONIFICACION_PROVEEDOR", "VENTA",
                "DEVOLUCION_PROVEEDOR", "MERMA", "DESMEDRO", "DONACION", "CONSUMO_INTERNO");
    }

    @ParameterizedTest
    @MethodSource("manualSubtypes")
    void registraCadaTipoDeMovimientoManualEnKardex(String subtype) {
        Sede site = Sede.builder().idSede(3).nombre("Centro").build();
        Usuario user = Usuario.builder().idUsuario(4).usuario("operador").build();
        Producto product = Producto.builder().idProducto(9).codigo("SKU-9").nombre("Producto")
                .estado(true).unidadMedida("unidad").build();
        Inventario stock = Inventario.builder().idInventario(10).sede(site).producto(product).stockActual(10).build();
        when(operationRepo.findByTipoAndReferencia(anyString(), anyString())).thenReturn(Optional.empty());
        when(sedeRepo.findById(3)).thenReturn(Optional.of(site));
        when(userRepo.findByUsuario("operador")).thenReturn(Optional.of(user));
        when(productRepo.findAllById(any())).thenReturn(List.of(product));
        when(inventoryRepo.findForUpdate(3, 9)).thenReturn(Optional.of(stock));
        when(operationRepo.save(any())).thenAnswer(invocation -> {
            OperacionInventario operation = invocation.getArgument(0);
            operation.setIdOperacion(55);
            return operation;
        });
        when(detailRepo.findByOperacion_IdOperacionOrderByIdDetalle(55)).thenReturn(List.of());
        if (List.of("BONIFICACION_PROVEEDOR", "DEVOLUCION_PROVEEDOR").contains(subtype)) {
            when(proveedorRepo.findById(2)).thenReturn(Optional.of(Proveedor.builder().idProveedor(2).nombre("Proveedor")
                    .estado(true).build()));
        }

        OperacionRequestDTO request = OperacionRequestDTO.builder().idSede(3).subtipo(subtype).idProveedor(
                        List.of("BONIFICACION_PROVEEDOR", "DEVOLUCION_PROVEEDOR").contains(subtype) ? 2 : null)
                .beneficiario("DONACION".equals(subtype) ? "Comedor" : null)
                .motivo(List.of("DEVOLUCION_CLIENTE", "PRODUCCION", "BONIFICACION_PROVEEDOR", "DEVOLUCION_PROVEEDOR",
                        "MERMA", "DESMEDRO", "DONACION", "CONSUMO_INTERNO").contains(subtype) ? "Control de prueba" : null)
                .referencia("REF-" + subtype).detalles(List.of(line(9, 1))).build();

        if (List.of("DEVOLUCION_CLIENTE", "PRODUCCION", "BONIFICACION_PROVEEDOR").contains(subtype))
            service.registrarIngreso(request, "operador");
        else service.registrarEgreso(request, "operador");

        var movement = org.mockito.ArgumentCaptor.forClass(Movimiento.class);
        verify(movementRepo).save(movement.capture());
        assertEquals(subtype, movement.getValue().getSubtipo());
        assertEquals("unidad", movement.getValue().getProducto().getUnidadMedida());
    }

    private DetalleOperacionRequestDTO line(Integer product, Integer quantity) {
        return DetalleOperacionRequestDTO.builder().idProducto(product).cantidad(quantity).build();
    }
}

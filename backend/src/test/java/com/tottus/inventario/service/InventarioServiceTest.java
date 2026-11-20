package com.tottus.inventario.service;

import com.tottus.inventario.model.Categoria;
import com.tottus.inventario.model.Inventario;
import com.tottus.inventario.model.Movimiento;
import com.tottus.inventario.model.Producto;
import com.tottus.inventario.model.Sede;
import com.tottus.inventario.model.Usuario;
import com.tottus.inventario.dto.MovimientoRequestDTO;
import com.tottus.inventario.repository.AuditoriaRepository;
import com.tottus.inventario.repository.InventarioRepository;
import com.tottus.inventario.repository.MovimientoRepository;
import com.tottus.inventario.repository.ProductoRepository;
import com.tottus.inventario.repository.SedeRepository;
import com.tottus.inventario.repository.UsuarioRepository;
import com.tottus.inventario.security.SedeAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventarioServiceTest {

    @Mock private InventarioRepository inventarioRepository;
    @Mock private MovimientoRepository movimientoRepository;
    @Mock private ProductoRepository productoRepository;
    @Mock private SedeRepository sedeRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private AuditoriaRepository auditoriaRepository;
    @Mock private SedeAccessService sedeAccessService;

    private InventarioService service;

    @BeforeEach
    void setUp() {
        service = new InventarioService(
                inventarioRepository,
                movimientoRepository,
                productoRepository,
                sedeRepository,
                usuarioRepository,
                auditoriaRepository,
                sedeAccessService
        );
        org.mockito.Mockito.lenient().when(sedeAccessService.requireAuthorizedSede(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void getMovimientosAplicaFiltrosYMapeaDatosDelKardex() {
        LocalDateTime fecha = LocalDateTime.of(2026, 11, 10, 14, 30);
        Sede sede = Sede.builder().idSede(4).nombre("Sede Norte").build();
        Producto producto = Producto.builder()
                .idProducto(17)
                .codigo("SKU-017")
                .nombre("Arroz integral")
                .build();
        Usuario usuario = Usuario.builder().usuario("operador01").build();
        Movimiento movimiento = Movimiento.builder()
                .idMovimiento(81)
                .sede(sede)
                .producto(producto)
                .usuario(usuario)
                .tipo("ENTRADA")
                .cantidad(12)
                .fecha(fecha)
                .referencia("Guía GR-81")
                .build();
        when(movimientoRepository.findWithFilters(4, "ENTRADA", 17)).thenReturn(List.of(movimiento));

        var resultados = service.getMovimientos(4, "ENTRADA", 17);

        assertEquals(1, resultados.size());
        var resultado = resultados.get(0);
        assertEquals(81, resultado.getIdMovimiento());
        assertEquals(4, resultado.getIdSede());
        assertEquals("Sede Norte", resultado.getNombreSede());
        assertEquals(17, resultado.getIdProducto());
        assertEquals("SKU-017", resultado.getCodigoProducto());
        assertEquals("Arroz integral", resultado.getNombreProducto());
        assertEquals("ENTRADA", resultado.getTipo());
        assertEquals(12, resultado.getCantidad());
        assertEquals(fecha, resultado.getFecha());
        assertEquals("Guía GR-81", resultado.getReferencia());
        assertEquals("operador01", resultado.getUsuario());
        verify(movimientoRepository).findWithFilters(4, "ENTRADA", 17);
    }

    @Test
    void getMovimientosTraduceTipoTodosAVacioYCompletaRelacionesNulas() {
        Movimiento movimiento = Movimiento.builder()
                .idMovimiento(82)
                .tipo("AJUSTE")
                .cantidad(3)
                .fecha(LocalDateTime.of(2026, 11, 10, 9, 0))
                .build();
        when(movimientoRepository.findWithFilters(2, null, null)).thenReturn(List.of(movimiento));

        var resultados = service.getMovimientos(2, "TODOS", null);

        assertEquals(1, resultados.size());
        assertEquals(82, resultados.get(0).getIdMovimiento());
        assertNull(resultados.get(0).getIdSede());
        assertEquals("", resultados.get(0).getNombreSede());
        assertNull(resultados.get(0).getIdProducto());
        assertEquals("", resultados.get(0).getCodigoProducto());
        assertEquals("Sistema", resultados.get(0).getUsuario());
        verify(movimientoRepository).findWithFilters(2, null, null);
    }

    @Test
    void getInventarioClasificaLimitesDeStockYRelacionaSedeYProducto() {
        Sede sede = Sede.builder().idSede(6).nombre("Sede Centro").build();
        Categoria categoria = Categoria.builder().idCategoria(3).nombre("Abarrotes").build();
        Producto productoAgotado = producto(30, "SKU-30", "Producto agotado", categoria, 5);
        Producto productoBajo = producto(31, "SKU-31", "Producto bajo", categoria, 5);
        Producto productoOptimo = producto(32, "SKU-32", "Producto óptimo", categoria, 5);
        when(inventarioRepository.searchInventario(6, null, null)).thenReturn(List.of(
                inventario(100, sede, productoAgotado, 0),
                inventario(101, sede, productoBajo, 5),
                inventario(102, sede, productoOptimo, 6)
        ));

        var resultados = service.getInventario(6, null, null, null);

        assertEquals(3, resultados.size());
        assertEquals("AGOTADO", resultados.get(0).getEstadoStock());
        assertEquals("BAJO_STOCK", resultados.get(1).getEstadoStock());
        assertEquals("OPTIMO", resultados.get(2).getEstadoStock());
        assertEquals(6, resultados.get(1).getIdSede());
        assertEquals("Sede Centro", resultados.get(1).getNombreSede());
        assertEquals(31, resultados.get(1).getIdProducto());
        assertEquals("SKU-31", resultados.get(1).getCodigoProducto());
        assertEquals("Producto bajo", resultados.get(1).getNombreProducto());
        assertEquals(5, resultados.get(1).getStockActual());
        assertEquals(5, resultados.get(1).getStockMinimo());
        verify(inventarioRepository).searchInventario(6, null, null);
    }

    @Test
    void getInventarioFiltraEstadoSinPerderElFiltroDeSede() {
        Sede sede = Sede.builder().idSede(6).nombre("Sede Centro").build();
        Categoria categoria = Categoria.builder().idCategoria(3).nombre("Abarrotes").build();
        when(inventarioRepository.searchInventario(6, null, "arroz")).thenReturn(List.of(
                inventario(100, sede, producto(30, "SKU-30", "Agotado", categoria, 5), 0),
                inventario(101, sede, producto(31, "SKU-31", "Bajo", categoria, 5), 5),
                inventario(102, sede, producto(32, "SKU-32", "Óptimo", categoria, 5), 6)
        ));

        var bajoStock = service.getInventario(6, null, "  arroz  ", "BAJO_STOCK");

        assertEquals(1, bajoStock.size());
        assertEquals("Bajo", bajoStock.get(0).getNombreProducto());
        assertEquals("BAJO_STOCK", bajoStock.get(0).getEstadoStock());
        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(inventarioRepository).searchInventario(org.mockito.ArgumentMatchers.eq(6),
                org.mockito.ArgumentMatchers.isNull(), queryCaptor.capture());
        assertEquals("arroz", queryCaptor.getValue());
    }

    @Test
    void getResumenCombinaConteosDeStockDeLaSedeSolicitada() {
        when(inventarioRepository.searchInventario(6, null, null)).thenReturn(List.of(
                inventario(100, Sede.builder().idSede(6).build(), producto(30, "A", "A", null, 4), 3),
                inventario(101, Sede.builder().idSede(6).build(), producto(31, "B", "B", null, 4), 0)
        ));
        when(inventarioRepository.countStockBajo(6)).thenReturn(1L);
        when(inventarioRepository.countAgotados(6)).thenReturn(1L);
        when(inventarioRepository.sumTotalUnidades(6)).thenReturn(3L);
        when(movimientoRepository.countMovimientosDesde(org.mockito.ArgumentMatchers.eq(6),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class))).thenReturn(2L);

        var resumen = service.getResumen(6);

        assertEquals(2, resumen.getTotalProductos());
        assertEquals(1, resumen.getStockBajo());
        assertEquals(1, resumen.getSinStock());
        assertEquals(3, resumen.getTotalUnidades());
        assertEquals(2, resumen.getMovimientosHoy());
        verify(inventarioRepository).countStockBajo(6);
        verify(inventarioRepository).countAgotados(6);
        verify(inventarioRepository).sumTotalUnidades(6);
    }

    @Test
    void rechazaCantidadCeroEnEntradaAntesDeConsultarPersistencia() {
        MovimientoRequestDTO request = MovimientoRequestDTO.builder()
                .idSede(6).idProducto(31).tipo("ENTRADA").cantidad(0).build();

        assertThrows(IllegalArgumentException.class, () -> service.registrarMovimiento(request, "operador01"));
        org.mockito.Mockito.verifyNoInteractions(inventarioRepository, movimientoRepository, productoRepository,
                sedeRepository, usuarioRepository, auditoriaRepository);
    }

    @Test
    void rechazaCantidadNegativaEnSalidaAntesDeConsultarPersistencia() {
        MovimientoRequestDTO request = MovimientoRequestDTO.builder()
                .idSede(6).idProducto(31).tipo("SALIDA").cantidad(-1).build();

        assertThrows(IllegalArgumentException.class, () -> service.registrarMovimiento(request, "operador01"));
        org.mockito.Mockito.verifyNoInteractions(inventarioRepository, movimientoRepository, productoRepository,
                sedeRepository, usuarioRepository, auditoriaRepository);
    }

    @Test
    void rechazaAjusteCuandoElStockFinalYaEsElActual() {
        var authentication = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "admin01", "n/a", List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMINISTRADOR")));
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(authentication);
        Sede sede = Sede.builder().idSede(6).nombre("Sede Centro").build();
        Producto producto = producto(31, "SKU-31", "Producto", null, 3);
        Usuario usuario = Usuario.builder().usuario("admin01").build();
        Inventario inventario = inventario(101, sede, producto, 8);
        when(sedeRepository.findById(6)).thenReturn(Optional.of(sede));
        when(productoRepository.findById(31)).thenReturn(Optional.of(producto));
        when(usuarioRepository.findByUsuario("admin01")).thenReturn(Optional.of(usuario));
        when(inventarioRepository.findForUpdate(6, 31)).thenReturn(Optional.of(inventario));
        try {
            MovimientoRequestDTO request = MovimientoRequestDTO.builder()
                    .idSede(6).idProducto(31).tipo("AJUSTE").cantidad(8).build();
            assertThrows(IllegalArgumentException.class, () -> service.registrarMovimiento(request, "admin01"));
            org.mockito.Mockito.verify(movimientoRepository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    void vendedorNoPuedeRegistrarIngresoAunqueInvoqueElServicio() {
        var authentication = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "vendedor01", "n/a", List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_VENDEDOR")));
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(authentication);
        try {
            MovimientoRequestDTO request = MovimientoRequestDTO.builder()
                    .idSede(6).idProducto(31).tipo("ENTRADA").cantidad(1).build();

            assertThrows(org.springframework.security.access.AccessDeniedException.class,
                    () -> service.registrarMovimiento(request, "vendedor01"));
            org.mockito.Mockito.verifyNoInteractions(inventarioRepository, movimientoRepository, productoRepository,
                    sedeRepository, usuarioRepository, auditoriaRepository, sedeAccessService);
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    private Producto producto(Integer id, String codigo, String nombre, Categoria categoria, int minimo) {
        return Producto.builder()
                .idProducto(id)
                .codigo(codigo)
                .nombre(nombre)
                .categoria(categoria)
                .stockMinimo(minimo)
                .estado(true)
                .build();
    }

    private Inventario inventario(Integer id, Sede sede, Producto producto, int stock) {
        return Inventario.builder()
                .idInventario(id)
                .sede(sede)
                .producto(producto)
                .stockActual(stock)
                .ubicacion("Pasillo A")
                .build();
    }
}

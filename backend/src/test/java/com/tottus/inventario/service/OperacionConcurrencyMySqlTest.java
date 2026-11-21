package com.tottus.inventario.service;

import com.tottus.inventario.dto.DetalleOperacionRequestDTO;
import com.tottus.inventario.dto.OperacionRequestDTO;
import com.tottus.inventario.dto.CompraDetalleRequestDTO;
import com.tottus.inventario.dto.CompraRequestDTO;
import com.tottus.inventario.dto.ProveedorRequestDTO;
import com.tottus.inventario.repository.OperacionInventarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named = "tottus.mysql.integration", matches = "true")
class OperacionConcurrencyMySqlTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private OperacionInventarioService operations;
    @Autowired private AbastecimientoService procurement;
    @Autowired private InventarioService inventoryService;
    @Autowired private ProductoService productService;
    @Autowired private OperacionInventarioRepository operationRepository;
    @Autowired private MockMvc mockMvc;
    @Autowired private com.tottus.inventario.security.JwtService jwtService;

    private Fixture fixture;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> requiredEnvironment("TOTTUS_TEST_JDBC_URL"));
        registry.add("spring.datasource.username", () -> requiredEnvironment("TOTTUS_TEST_DB_USERNAME"));
        registry.add("spring.datasource.password", () -> requiredEnvironment("TOTTUS_TEST_DB_PASSWORD"));
        registry.add("jwt.secret", () -> "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
    }

    @BeforeEach
    void createIsolatedFixture() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        int roleId = insertId("INSERT INTO rol(nombre, estado) VALUES (?, true)", "ADMINISTRADOR-" + suffix);
        int buyerRoleId = insertId("INSERT INTO rol(nombre, estado) VALUES (?, true)", "ENCARGADO_COMPRAS");
        int siteId = insertId("INSERT INTO sede(nombre, direccion, estado) VALUES (?, 'test aislado', true)", "Sede integración " + suffix);
        String username = "mysql-test-" + suffix;
        insertId("INSERT INTO usuario(id_rol,id_sede,usuario,password_hash,estado) VALUES (?,?,?,'unused',true)", roleId, siteId, username);
        String buyerUsername = "buyer-test-" + suffix;
        insertId("INSERT INTO usuario(id_rol,id_sede,usuario,password_hash,estado) VALUES (?,?,?,'unused',true)", buyerRoleId, siteId, buyerUsername);
        int categoryId = insertId("INSERT INTO categoria(nombre, estado) VALUES (?, true)", "Categoría test " + suffix);
        int productA = createProduct(categoryId, "A-" + suffix);
        int productB = createProduct(categoryId, "B-" + suffix);
        int productC = createProduct(categoryId, "C-" + suffix);
        jdbc.update("INSERT INTO inventario(id_sede,id_producto,stock_actual,ubicacion) VALUES (?,?,0,'test'),(?,?,0,'test'),(?,?,0,'test')",
                siteId, productA, siteId, productB, siteId, productC);
        int supplierId = insertId("INSERT INTO proveedor(documento,nombre,estado) VALUES (?, ?, true)", "DOC-" + suffix, "Proveedor " + suffix);
        fixture = new Fixture(siteId, username, buyerUsername, categoryId, productA, productB, productC, supplierId, suffix);
        authenticate();
    }

    @Test
    void edicionAumentaReduceAgregaRetiraEsIdempotenteYRevierteTodoAnteError() {
        String ref = "IN-EDIT-" + fixture.suffix;
        var original = operations.registrarIngreso(request(ref, line(fixture.productA, 10), line(fixture.productB, 5)), fixture.username);
        assertEquals(10, stock(fixture.productA));
        assertEquals(5, stock(fixture.productB));

        operations.editarIngreso(original.getIdOperacion(), request(ref, line(fixture.productA, 12), line(fixture.productB, 5), line(fixture.productC, 3)), fixture.username);
        assertEquals(12, stock(fixture.productA));
        assertEquals(5, stock(fixture.productB));
        assertEquals(3, stock(fixture.productC));

        operations.editarIngreso(original.getIdOperacion(), request(ref, line(fixture.productA, 6), line(fixture.productC, 3)), fixture.username);
        assertEquals(6, stock(fixture.productA));
        assertEquals(0, stock(fixture.productB));
        assertEquals(3, stock(fixture.productC));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM detalle_operacion_inventario WHERE id_operacion=?", Integer.class, original.getIdOperacion()));
        assertEquals(6, jdbc.queryForObject("SELECT COUNT(*) FROM movimiento WHERE id_operacion=?", Integer.class, original.getIdOperacion()),
                "debe conservar los dos movimientos iniciales y agregar cuatro compensaciones");

        var retry = operations.registrarIngreso(request(ref, line(fixture.productA, 6), line(fixture.productC, 3)), fixture.username);
        assertEquals(original.getIdOperacion(), retry.getIdOperacion());
        assertThrows(IllegalArgumentException.class, () -> operations.registrarIngreso(request(ref, line(fixture.productA, 7)), fixture.username));

        String rollbackRef = "OUT-ROLLBACK-" + fixture.suffix;
        assertThrows(IllegalArgumentException.class, () -> operations.registrarEgreso(
                request(rollbackRef, line(fixture.productC, 2), line(fixture.productB, 1)), fixture.username));
        assertEquals(3, stock(fixture.productC), "el primer detalle debe revertirse si falla el segundo");
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM operacion_inventario WHERE referencia=?", Integer.class, rollbackRef));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM movimiento WHERE referencia=?", Integer.class, rollbackRef));
    }

    @Test
    void dosEgresosConcurrentesConStockInsuficienteSoloPermitenUno() throws Exception {
        jdbc.update("UPDATE inventario SET stock_actual=5 WHERE id_sede=? AND id_producto=?", fixture.siteId, fixture.productA);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<CallResult> first = pool.submit(() -> concurrent(ready, start, () -> operations.registrarEgreso(request("OUT-A-" + fixture.suffix, line(fixture.productA, 4)), fixture.username)));
            Future<CallResult> second = pool.submit(() -> concurrent(ready, start, () -> operations.registrarEgreso(request("OUT-B-" + fixture.suffix, line(fixture.productA, 4)), fixture.username)));
            releaseTogether(ready, start);
            List<CallResult> results = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertEquals(1, (int) results.stream().filter(CallResult::succeeded).count());
            assertTrue(results.stream().filter(result -> !result.succeeded()).allMatch(result -> result.failure() instanceof IllegalArgumentException
                    && result.failure().getMessage().contains("Stock insuficiente")));
        }
        assertEquals(1, stock(fixture.productA));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM operacion_inventario WHERE tipo='EGRESO' AND referencia LIKE ?", Integer.class, "OUT-%-" + fixture.suffix));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM movimiento WHERE tipo='SALIDA' AND id_producto=?", Integer.class, fixture.productA));
    }

    @Test
    void dosEdicionesConcurrentesDelMismoIngresoConservanStockDetalleYKardex() throws Exception {
        var inbound = operations.registrarIngreso(request("IN-RACE-" + fixture.suffix, line(fixture.productA, 10)), fixture.username);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<CallResult> first = pool.submit(() -> concurrent(ready, start, () -> operations.editarIngreso(inbound.getIdOperacion(), request("IN-RACE-" + fixture.suffix, line(fixture.productA, 4)), fixture.username)));
            Future<CallResult> second = pool.submit(() -> concurrent(ready, start, () -> operations.editarIngreso(inbound.getIdOperacion(), request("IN-RACE-" + fixture.suffix, line(fixture.productA, 7)), fixture.username)));
            releaseTogether(ready, start);
            CallResult firstResult = first.get(10, TimeUnit.SECONDS);
            CallResult secondResult = second.get(10, TimeUnit.SECONDS);
            assertTrue(firstResult.succeeded(), String.valueOf(firstResult.failure()));
            assertTrue(secondResult.succeeded(), String.valueOf(secondResult.failure()));
        }
        int detailQty = jdbc.queryForObject("SELECT cantidad FROM detalle_operacion_inventario WHERE id_operacion=? AND id_producto=?", Integer.class, inbound.getIdOperacion(), fixture.productA);
        assertTrue(detailQty == 4 || detailQty == 7);
        assertEquals(detailQty, stock(fixture.productA));
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM movimiento WHERE id_operacion=?", Integer.class, inbound.getIdOperacion()));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM inventario WHERE id_sede=? AND stock_actual<0", Integer.class, fixture.siteId));
    }

    @Test
    void recepcionConcurrenteDeLaMismaCompraContabilizaUnaVez() throws Exception {
        String purchaseRef = "PO-RACE-" + fixture.suffix;
        int purchaseId = insertId("INSERT INTO compra(id_proveedor,id_sede,id_usuario,referencia,estado,fecha) " +
                        "SELECT ?,?,?,?,'PENDIENTE',NOW()", fixture.supplierId, fixture.siteId,
                jdbc.queryForObject("SELECT id_usuario FROM usuario WHERE usuario=?", Integer.class, fixture.username), purchaseRef);
        jdbc.update("INSERT INTO detalle_compra(id_compra,id_producto,cantidad,costo_unitario) VALUES (?,?,8,1.00)", purchaseId, fixture.productA);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<CallResult> first = pool.submit(() -> concurrent(ready, start, () -> procurement.recibirCompra(purchaseId, fixture.siteId, fixture.username)));
            Future<CallResult> second = pool.submit(() -> concurrent(ready, start, () -> procurement.recibirCompra(purchaseId, fixture.siteId, fixture.username)));
            releaseTogether(ready, start);
            CallResult firstResult = first.get(10, TimeUnit.SECONDS);
            CallResult secondResult = second.get(10, TimeUnit.SECONDS);
            assertTrue(firstResult.succeeded(), String.valueOf(firstResult.failure()));
            assertTrue(secondResult.succeeded(), String.valueOf(secondResult.failure()));
        }
        assertEquals(8, stock(fixture.productA));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM operacion_inventario WHERE referencia=?", Integer.class, "RECEPCION-" + purchaseRef));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM movimiento WHERE referencia=?", Integer.class, "RECEPCION-" + purchaseRef));
        assertEquals("RECIBIDA", jdbc.queryForObject("SELECT estado FROM compra WHERE id_compra=?", String.class, purchaseId));
        assertNotNull(operationRepository.findByTipoAndReferencia("INGRESO", "RECEPCION-" + purchaseRef).orElse(null));
    }

    @Test
    void comprasPendientesProveedoresDuplicadosEdicionEHistorial() {
        ProveedorRequestDTO supplierEdit = ProveedorRequestDTO.builder().documento("DOC-" + fixture.suffix)
                .nombre("Proveedor actualizado").contacto("Compras").telefono("999000111").correo("compras@example.test").build();
        assertThrows(IllegalArgumentException.class, () -> procurement.guardarProveedor(null,
                ProveedorRequestDTO.builder().documento("DOC-" + fixture.suffix).nombre("Duplicado").build()));
        assertEquals("Proveedor actualizado", procurement.guardarProveedor(fixture.supplierId, supplierEdit).getNombre());

        String ref = "PO-EDIT-" + fixture.suffix;
        CompraRequestDTO pending = purchaseRequest(ref, line(fixture.productA, 4), line(fixture.productB, 2));
        @SuppressWarnings("unchecked")
        Map<String, Object> created = procurement.crearCompra(pending, fixture.username);
        int purchaseId = (Integer) created.get("idCompra");
        assertEquals(0, stock(fixture.productA), "una compra pendiente no cambia el stock");

        CompraRequestDTO edited = purchaseRequest(ref, line(fixture.productA, 6), line(fixture.productC, 3));
        @SuppressWarnings("unchecked")
        Map<String, Object> updated = procurement.editarCompra(purchaseId, edited);
        assertEquals(2, ((List<?>) updated.get("detalles")).size());
        assertEquals(0, stock(fixture.productA));

        procurement.estadoProveedor(fixture.supplierId, false);
        assertThrows(IllegalArgumentException.class, () -> procurement.crearCompra(
                purchaseRequest("PO-INACTIVE-" + fixture.suffix, line(fixture.productA, 1)), fixture.username));
        assertEquals(1, procurement.listarCompras(fixture.siteId, ref).size(), "la compra histórica debe conservarse");

        procurement.estadoProveedor(fixture.supplierId, true);
        procurement.recibirCompra(purchaseId, fixture.siteId, fixture.username);
        assertEquals(6, stock(fixture.productA));
        assertEquals(3, stock(fixture.productC));
        assertThrows(IllegalArgumentException.class, () -> procurement.editarCompra(purchaseId,
                purchaseRequest(ref, line(fixture.productA, 7))));
        assertEquals(1, procurement.listarCompras(fixture.siteId, ref).size());
    }

    @Test
    void kardexPaginaEnServidorConservaConteoYOrdenEstable() {
        for (int index = 0; index < 5; index++) {
            operations.registrarIngreso(request("IN-PAGE-" + fixture.suffix + "-" + index, line(fixture.productA, 1)), fixture.username);
        }
        var firstPage = inventoryService.getMovimientosPage(fixture.siteId, null, fixture.productA, null, null, 0, 2);
        var secondPage = inventoryService.getMovimientosPage(fixture.siteId, null, fixture.productA, null, null, 1, 2);

        assertEquals(5, firstPage.getTotalElements());
        assertEquals(3, firstPage.getTotalPages());
        assertEquals(2, firstPage.getContent().size());
        assertEquals(2, secondPage.getContent().size());
        assertTrue(firstPage.getContent().get(0).getIdMovimiento() > firstPage.getContent().get(1).getIdMovimiento());
        assertTrue(firstPage.getContent().get(1).getIdMovimiento() > secondPage.getContent().get(0).getIdMovimiento());
    }

    @Test
    void estadosYResumenDeStockCoincidenEnLimitesNulosYCeroYSedeAjenaSeRechaza() {
        jdbc.update("UPDATE producto SET stock_minimo=5 WHERE id_producto=?", fixture.productA);
        jdbc.update("UPDATE inventario SET stock_actual=0 WHERE id_sede=? AND id_producto=?", fixture.siteId, fixture.productA);
        jdbc.update("UPDATE producto SET stock_minimo=5 WHERE id_producto=?", fixture.productB);
        jdbc.update("UPDATE inventario SET stock_actual=5 WHERE id_sede=? AND id_producto=?", fixture.siteId, fixture.productB);
        jdbc.update("UPDATE producto SET stock_minimo=0 WHERE id_producto=?", fixture.productC);
        jdbc.update("UPDATE inventario SET stock_actual=1 WHERE id_sede=? AND id_producto=?", fixture.siteId, fixture.productC);

        var states = inventoryService.getInventario(fixture.siteId, null, null, null).stream()
                .collect(java.util.stream.Collectors.toMap(com.tottus.inventario.dto.InventarioResponseDTO::getIdProducto,
                        com.tottus.inventario.dto.InventarioResponseDTO::getEstadoStock));
        assertEquals("AGOTADO", states.get(fixture.productA));
        assertEquals("BAJO_STOCK", states.get(fixture.productB), "la igualdad positiva se clasifica como BAJO_STOCK");
        assertEquals("OPTIMO", states.get(fixture.productC), "con mínimo cero, el stock positivo es óptimo");

        var summary = inventoryService.getResumen(fixture.siteId);
        assertEquals(1, summary.getStockBajo());
        assertEquals(1, summary.getSinStock());
        assertEquals(6, summary.getTotalUnidades());

        jdbc.update("UPDATE inventario SET stock_actual=NULL WHERE id_sede=? AND id_producto=?", fixture.siteId, fixture.productA);
        jdbc.update("UPDATE producto SET stock_minimo=NULL WHERE id_producto=?", fixture.productB);
        states = inventoryService.getInventario(fixture.siteId, null, null, null).stream()
                .collect(java.util.stream.Collectors.toMap(com.tottus.inventario.dto.InventarioResponseDTO::getIdProducto,
                        com.tottus.inventario.dto.InventarioResponseDTO::getEstadoStock));
        assertEquals("AGOTADO", states.get(fixture.productA), "stock actual nulo se presenta como cero");
        assertEquals("OPTIMO", states.get(fixture.productB), "mínimo nulo se presenta como cero");
        summary = inventoryService.getResumen(fixture.siteId);
        assertEquals(0, summary.getStockBajo());
        assertEquals(1, summary.getSinStock(), "el conteo SQL debe coincidir con el mapper para stock nulo");
        assertEquals(6, summary.getTotalUnidades());

        int otherSite = insertId("INSERT INTO sede(nombre, direccion, estado) VALUES (?, 'otra sede aislada', true)",
                "Otra sede " + fixture.suffix);
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> inventoryService.getInventario(otherSite, null, null, null));
    }

    @Test
    void crudDeProductoValidaDuplicadosConservaHistorialYRevierteDatosInvalidos() {
        String code = "E2E-" + fixture.suffix;
        String invalidCode = "BAD-" + fixture.suffix;
        assertThrows(IllegalArgumentException.class, () -> productService.guardarProducto(
                com.tottus.inventario.dto.ProductoRequestDTO.builder().codigo(invalidCode).nombre("Producto invÃ¡lido")
                        .idCategoria(fixture.categoryId).precio(BigDecimal.ZERO).stockMinimo(0)
                        .idSede(fixture.siteId).stockInicial(2).build(), fixture.username));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM producto WHERE codigo=?", Integer.class, invalidCode));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM inventario i JOIN producto p ON p.id_producto=i.id_producto WHERE p.codigo=?",
                Integer.class, invalidCode));

        var request = com.tottus.inventario.dto.ProductoRequestDTO.builder()
                .codigo(" " + code + " ").nombre("Producto integración " + fixture.suffix)
                .idCategoria(fixture.categoryId).precio(new BigDecimal("4.50")).stockMinimo(0)
                .idSede(fixture.siteId).stockInicial(3).ubicacion("Pasillo prueba").build();

        var created = productService.guardarProducto(request, fixture.username);
        assertEquals(code, created.getCodigo(), "el SKU se normaliza sin espacios externos");
        assertEquals(3, stock(created.getIdProducto()));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM movimiento WHERE id_producto=? AND id_sede=?",
                Integer.class, created.getIdProducto(), fixture.siteId));

        assertThrows(IllegalArgumentException.class, () -> productService.guardarProducto(
                com.tottus.inventario.dto.ProductoRequestDTO.builder().codigo(code).nombre("Duplicado")
                        .precio(new BigDecimal("2.00")).idSede(fixture.siteId).stockInicial(0).build(), fixture.username));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM producto WHERE codigo=?", Integer.class, code));

        var edited = productService.actualizarProducto(created.getIdProducto(),
                com.tottus.inventario.dto.ProductoRequestDTO.builder().codigo(code).nombre("Producto editado")
                        .idCategoria(fixture.categoryId).precio(new BigDecimal("5.00")).stockMinimo(0)
                        .idSede(fixture.siteId).ubicacion("Pasillo actualizado").build(), fixture.username);
        assertEquals("Producto editado", edited.getNombre());
        assertThrows(IllegalArgumentException.class, () -> productService.actualizarProducto(created.getIdProducto(),
                com.tottus.inventario.dto.ProductoRequestDTO.builder()
                        .codigo(jdbc.queryForObject("SELECT codigo FROM producto WHERE id_producto=?", String.class, fixture.productA))
                        .nombre("SKU duplicado").idCategoria(fixture.categoryId).precio(new BigDecimal("5.00"))
                        .stockMinimo(0).idSede(fixture.siteId).build(), fixture.username));

        int movementsBeforeDeactivation = jdbc.queryForObject("SELECT COUNT(*) FROM movimiento WHERE id_producto=?",
                Integer.class, created.getIdProducto());
        productService.cambiarEstadoProducto(created.getIdProducto(), false, fixture.username);
        assertEquals(3, stock(created.getIdProducto()), "la inactivación lógica conserva existencia");
        assertEquals(movementsBeforeDeactivation, jdbc.queryForObject("SELECT COUNT(*) FROM movimiento WHERE id_producto=?",
                Integer.class, created.getIdProducto()), "la inactivación no borra movimientos históricos");
        assertFalse(jdbc.queryForObject("SELECT estado FROM producto WHERE id_producto=?", Boolean.class, created.getIdProducto()));
    }

    @Test
    void compradorLeeCatalogoSinPoderConsultarExistenciasDeSede() {
        authenticateAs(fixture.buyerUsername, "ENCARGADO_COMPRAS");

        assertTrue(operations.listar("INGRESO", fixture.siteId, null, null, null).isEmpty(),
                "la lectura de operaciones permitida por controller también debe estar permitida en servicio");
        assertTrue(productService.listarProductos(null, "A-" + fixture.suffix).stream()
                .anyMatch(product -> product.getIdProducto().equals(fixture.productA)));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> inventoryService.getInventario(fixture.siteId, null, null, null));
        assertEquals(0, productService.listarProductos(null, "B-" + fixture.suffix).stream()
                .filter(product -> product.getIdProducto().equals(fixture.productA)).count());
    }

    @Test
    void jwtHttpAplicaUsuarioActivoRolActivoYSedeAsignada() throws Exception {
        int operatorRole = insertId("INSERT INTO rol(nombre, estado) VALUES ('OPERADOR_ALMACEN', true)");
        int inactiveRole = insertId("INSERT INTO rol(nombre, estado) VALUES ('VENDEDOR', false)");
        String activeUsername = "http-active-" + fixture.suffix;
        String inactiveUsername = "http-inactive-" + fixture.suffix;
        String inactiveRoleUsername = "http-role-inactive-" + fixture.suffix;
        String noSiteUsername = "http-no-site-" + fixture.suffix;
        insertId("INSERT INTO usuario(id_rol,id_sede,usuario,password_hash,estado) VALUES (?,?,?,'unused',true)",
                operatorRole, fixture.siteId, activeUsername);
        insertId("INSERT INTO usuario(id_rol,id_sede,usuario,password_hash,estado) VALUES (?,?,?,'unused',false)",
                operatorRole, fixture.siteId, inactiveUsername);
        insertId("INSERT INTO usuario(id_rol,id_sede,usuario,password_hash,estado) VALUES (?,?,?,'unused',true)",
                inactiveRole, fixture.siteId, inactiveRoleUsername);
        insertId("INSERT INTO usuario(id_rol,id_sede,usuario,password_hash,estado) VALUES (?,NULL,?,'unused',true)",
                operatorRole, noSiteUsername);

        // The shared fixture authenticates a service principal for direct-service tests;
        // clear it so these HTTP requests exercise the Authorization bearer filter.
        SecurityContextHolder.clearContext();
        mockMvc.perform(get("/api/inventario")
                        .header("Authorization", bearer(activeUsername, "OPERADOR_ALMACEN")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/inventario")
                        .header("Authorization", bearer(inactiveUsername, "OPERADOR_ALMACEN")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/inventario")
                        .header("Authorization", bearer(inactiveRoleUsername, "VENDEDOR")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/inventario")
                        .header("Authorization", bearer(noSiteUsername, "OPERADOR_ALMACEN")))
                .andExpect(status().isForbidden());

        int foreignSite = insertId("INSERT INTO sede(nombre,direccion,estado) VALUES (?, 'otra sede', true)",
                "HTTP ajena " + fixture.suffix);
        mockMvc.perform(get("/api/inventario").param("sedeId", String.valueOf(foreignSite))
                        .header("Authorization", bearer(activeUsername, "OPERADOR_ALMACEN")))
                .andExpect(status().isForbidden());
    }

    private String bearer(String username, String role) {
        var principal = User.withUsername(username).password("unused").roles(role).build();
        return "Bearer " + jwtService.generateToken(principal, Map.of());
    }

    private int createProduct(int categoryId, String code) {
        return insertId("INSERT INTO producto(id_categoria,codigo,nombre,precio,stock_minimo,estado) VALUES (?,?,?,1.00,2,true)",
                categoryId, code, "Producto " + code);
    }

    private int insertId(String sql, Object... args) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int index = 0; index < args.length; index++) statement.setObject(index + 1, args[index]);
            return statement;
        }, keys);
        Number key = Objects.requireNonNull(keys.getKey(), "la inserción debe devolver una clave generada");
        return key.intValue();
    }

    private int stock(int productId) {
        return jdbc.queryForObject("SELECT stock_actual FROM inventario WHERE id_sede=? AND id_producto=?", Integer.class, fixture.siteId, productId);
    }

    private OperacionRequestDTO request(String reference, DetalleOperacionRequestDTO... lines) {
        boolean expense = reference.startsWith("OUT-");
        return OperacionRequestDTO.builder().idSede(fixture.siteId)
                .subtipo(expense ? "VENTA" : "DEVOLUCION_CLIENTE")
                .motivo(expense ? null : "Prueba automatizada aislada")
                .referencia(reference).detalles(List.of(lines)).build();
    }

    private CompraRequestDTO purchaseRequest(String reference, DetalleOperacionRequestDTO... lines) {
        List<CompraDetalleRequestDTO> details = java.util.Arrays.stream(lines)
                .map(line -> CompraDetalleRequestDTO.builder().idProducto(line.getIdProducto()).cantidad(line.getCantidad())
                        .costoUnitario(new BigDecimal("1.25")).build()).toList();
        return CompraRequestDTO.builder().idSede(fixture.siteId).idProveedor(fixture.supplierId)
                .referencia(reference).detalles(details).build();
    }

    private DetalleOperacionRequestDTO line(int productId, int quantity) {
        return DetalleOperacionRequestDTO.builder().idProducto(productId).cantidad(quantity).build();
    }

    private void authenticate() {
        authenticateAs(fixture.username, "ADMINISTRADOR");
    }

    private void authenticateAs(String username, String role) {
        var auth = new UsernamePasswordAuthenticationToken(username, "",
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private CallResult concurrent(CountDownLatch ready, CountDownLatch start, ThrowingRunnable operation) {
        authenticate();
        ready.countDown();
        try {
            if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("No se liberó la barrera de inicio");
            operation.run();
            return new CallResult(true, null);
        } catch (RuntimeException exception) {
            return new CallResult(false, exception);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void releaseTogether(CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        assertTrue(ready.await(5, TimeUnit.SECONDS), "los dos hilos deben estar listos");
        start.countDown();
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Falta propiedad de prueba " + name);
        return value;
    }

    private record Fixture(int siteId, String username, String buyerUsername, int categoryId, int productA, int productB, int productC,
                           int supplierId, String suffix) { }
    private record CallResult(boolean succeeded, RuntimeException failure) { }

    @FunctionalInterface
    private interface ThrowingRunnable { void run() throws Exception; }
}

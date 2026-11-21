package com.tottus.inventario.security;

import com.tottus.inventario.config.CorsConfig;
import com.tottus.inventario.controller.AbastecimientoController;
import com.tottus.inventario.controller.InventarioController;
import com.tottus.inventario.controller.OperacionInventarioController;
import com.tottus.inventario.controller.ProductoController;
import com.tottus.inventario.controller.SedeController;
import com.tottus.inventario.controller.UsuarioAdminController;
import com.tottus.inventario.model.Sede;
import com.tottus.inventario.service.AbastecimientoService;
import com.tottus.inventario.service.InventarioService;
import com.tottus.inventario.service.OperacionInventarioService;
import com.tottus.inventario.service.ProductoService;
import com.tottus.inventario.service.UsuarioAdminService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {InventarioController.class, ProductoController.class, AbastecimientoController.class,
        OperacionInventarioController.class, SedeController.class, UsuarioAdminController.class})
@Import({SecurityConfig.class, CorsConfig.class, JwtService.class})
@TestPropertySource(properties = {
        "jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "jwt.expiration=60000"
})
class RoleAuthorizationHttpTest {
    @Autowired private MockMvc mockMvc;
    @MockBean private InventarioService inventario;
    @MockBean private ProductoService productos;
    @MockBean private AbastecimientoService abastecimiento;
    @MockBean private OperacionInventarioService operaciones;
    @MockBean private SedeAccessService sedeAccess;
    @MockBean private CustomUserDetailsService userDetailsService;
    @MockBean private UsuarioAdminService usuariosAdmin;

    @Test
    void inventarioDeLecturaRespetaLosRolesDeLaReglaActual() throws Exception {
        for (String role : List.of("ADMINISTRADOR", "JEFE_INVENTARIO", "OPERADOR_ALMACEN", "VENDEDOR")) {
            mockMvc.perform(get("/api/inventario").with(user("tester").roles(role)))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/inventario").with(user("buyer").roles("ENCARGADO_COMPRAS")))
                .andExpect(status().isForbidden());
    }

    @Test
    void resumenYKardexSonSoloParaAdminYJefe() throws Exception {
        for (String role : List.of("ADMINISTRADOR", "JEFE_INVENTARIO")) {
            mockMvc.perform(get("/api/inventario/resumen").with(user("tester").roles(role)))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/inventario/movimientos").with(user("tester").roles(role)))
                    .andExpect(status().isOk());
        }
        for (String role : List.of("OPERADOR_ALMACEN", "VENDEDOR", "ENCARGADO_COMPRAS")) {
            mockMvc.perform(get("/api/inventario/resumen").with(user("tester").roles(role)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/inventario/movimientos").with(user("tester").roles(role)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void encargadoComprasConsultaCatalogoPeroNoStockNiGestionDeProductos() throws Exception {
        mockMvc.perform(get("/api/productos").with(user("buyer").roles("ENCARGADO_COMPRAS")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/inventario").with(user("buyer").roles("ENCARGADO_COMPRAS")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/compras").with(user("buyer").roles("ENCARGADO_COMPRAS")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/proveedores").with(user("buyer").roles("ENCARGADO_COMPRAS")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/productos").with(user("buyer").roles("ENCARGADO_COMPRAS"))
                        .contentType("application/json")
                        .content("{\"codigo\":\"SKU-1\",\"nombre\":\"Producto\",\"precio\":1,\"idCategoria\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void encargadoComprasPuedeLeerPeroNoRegistrarIngresoManualNiEditarNiLeerEgreso() throws Exception {
        String body = "{\"idSede\":7,\"referencia\":\"IN-TEST\",\"detalles\":[{\"idProducto\":1,\"cantidad\":1}]}";
        mockMvc.perform(get("/api/operaciones/ingresos").with(user("buyer").roles("ENCARGADO_COMPRAS")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/operaciones/ingresos").with(user("buyer").roles("ENCARGADO_COMPRAS"))
                        .contentType("application/json").content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/operaciones/ingresos/1").with(user("buyer").roles("ENCARGADO_COMPRAS"))
                        .contentType("application/json").content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/operaciones/egresos").with(user("buyer").roles("ENCARGADO_COMPRAS")))
                .andExpect(status().isForbidden());
    }

    @Test
    void ventaPuedeRegistrarEgresoPeroNoIngreso() throws Exception {
        String body = "{\"idSede\":7,\"referencia\":\"TEST\",\"detalles\":[{\"idProducto\":1,\"cantidad\":1}]}";
        mockMvc.perform(post("/api/operaciones/egresos").with(user("seller").roles("VENDEDOR"))
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/operaciones/ingresos").with(user("seller").roles("VENDEDOR"))
                        .contentType("application/json").content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void recepcionFisicaEsParaAlmacenYSupervisionNoParaCompras() throws Exception {
        for (String role : List.of("ADMINISTRADOR", "JEFE_INVENTARIO", "OPERADOR_ALMACEN")) {
            mockMvc.perform(post("/api/compras/1/recepcion").with(user("receiver").roles(role)))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/compras/1/recepcion").with(user("buyer").roles("ENCARGADO_COMPRAS")))
                .andExpect(status().isForbidden());
    }

    @Test
    void aprobacionDeEgresoSoloParaJefeOAdministrador() throws Exception {
        for (String role : List.of("ADMINISTRADOR", "JEFE_INVENTARIO")) {
            mockMvc.perform(post("/api/operaciones/egresos/14/aprobar?sedeId=7").with(user("reviewer").roles(role)))
                    .andExpect(status().isOk());
        }
        for (String role : List.of("OPERADOR_ALMACEN", "VENDEDOR", "ENCARGADO_COMPRAS")) {
            mockMvc.perform(post("/api/operaciones/egresos/14/aprobar?sedeId=7").with(user("other").roles(role)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void operadorPuedeSolicitarEgresoPeroNoAprobarlo() throws Exception {
        String request = """
                {"idSede":7,"subtipo":"MERMA","motivo":"Empaque dañado","referencia":"REQ-14",
                 "detalles":[{"idProducto":3,"cantidad":1}]}
                """;
        mockMvc.perform(post("/api/operaciones/egresos").with(user("operator").roles("OPERADOR_ALMACEN"))
                        .contentType("application/json").content(request))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/operaciones/egresos/14/aprobar?sedeId=7").with(user("operator").roles("OPERADOR_ALMACEN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void gestionDeCuentasSoloEsAccesiblePorAdministradores() throws Exception {
        when(usuariosAdmin.listar(null, null)).thenReturn(List.of());
        mockMvc.perform(get("/api/admin/usuarios").with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk());
        for (String role : List.of("JEFE_INVENTARIO", "OPERADOR_ALMACEN", "VENDEDOR", "ENCARGADO_COMPRAS")) {
            mockMvc.perform(get("/api/admin/usuarios").with(user("other").roles(role)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void cambiarRolYSedesDeUsuarioEsAccionSoloAdministrativa() throws Exception {
        String request = """
                {"rol":"VENDEDOR","idSedes":[7],"accesoGlobal":false}
                """;
        mockMvc.perform(put("/api/admin/usuarios/12/acceso").with(user("admin").roles("ADMINISTRADOR"))
                        .contentType("application/json").content(request))
                .andExpect(status().isOk());
        for (String role : List.of("JEFE_INVENTARIO", "OPERADOR_ALMACEN", "VENDEDOR", "ENCARGADO_COMPRAS")) {
            mockMvc.perform(put("/api/admin/usuarios/12/acceso").with(user("other").roles(role))
                            .contentType("application/json").content(request))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void administradorSoloVeLaSedeAsignadaYNoObtieneAccesoGlobal() throws Exception {
        when(sedeAccess.authorizedSedes()).thenReturn(List.of(
                Sede.builder().idSede(7).nombre("Sede asignada").build()));

        mockMvc.perform(get("/api/sedes").with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].idSede").value(7));
    }
}

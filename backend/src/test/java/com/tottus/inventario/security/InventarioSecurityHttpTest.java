package com.tottus.inventario.security;

import com.tottus.inventario.controller.InventarioController;
import com.tottus.inventario.dto.MovimientoResponseDTO;
import com.tottus.inventario.dto.MovimientoPageResponseDTO;
import com.tottus.inventario.service.InventarioService;
import com.tottus.inventario.config.CorsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.util.Date;
import java.util.Map;

import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InventarioController.class)
@Import({SecurityConfig.class, CorsConfig.class, JwtService.class})
@TestPropertySource(properties = {
        "jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "jwt.expiration=60000"
})
class InventarioSecurityHttpTest {
    @Autowired private MockMvc mockMvc;
    @MockBean private InventarioService inventarioService;
    @Autowired private JwtService jwtService;
    @MockBean private CustomUserDetailsService userDetailsService;

    @Test
    void peticionSinTokenRecibe401() throws Exception {
        mockMvc.perform(get("/api/inventario/movimientos"))
                .andExpect(status().isUnauthorized())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Content-Security-Policy", "default-src 'none'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("X-Frame-Options", "DENY"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Permissions-Policy", "camera=(), microphone=(), geolocation=()"));
        verifyNoInteractions(inventarioService);
    }

    @Test
    void rolOperadorNoPuedeConsultarKardexYRecibe403() throws Exception {
        mockMvc.perform(get("/api/inventario/movimientos").with(user("operator").roles("OPERADOR_ALMACEN")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(inventarioService);
    }

    @Test
    void rolJefePuedeConsultarKardex() throws Exception {
        when(inventarioService.getMovimientosPage(7, null, null, null, null, 0, 25)).thenReturn(
                MovimientoPageResponseDTO.builder().content(List.of(MovimientoResponseDTO.builder().idMovimiento(1).idSede(7).tipo("ENTRADA").cantidad(2).build()))
                        .page(0).size(25).totalElements(1).totalPages(1).build());

        mockMvc.perform(get("/api/inventario/movimientos?sedeId=7")
                        .with(user("supervisor").roles("JEFE_INVENTARIO")))
                .andExpect(status().isOk());
    }

    @Test
    void bearerFirmadoAtraviesaElFiltroYAutorizaElEndpoint() throws Exception {
        UserDetails jefe = User.withUsername("jefe-http").password("unused").roles("JEFE_INVENTARIO").build();
        when(userDetailsService.loadUserByUsername("jefe-http")).thenReturn(jefe);
        when(inventarioService.getMovimientosPage(7, null, null, null, null, 0, 25)).thenReturn(
                MovimientoPageResponseDTO.builder().content(List.of()).page(0).size(25).totalElements(0).totalPages(0).build());
        String token = jwtService.generateToken(jefe, Map.of());

        mockMvc.perform(get("/api/inventario/movimientos?sedeId=7")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void bearerManipuladoYVencidoNoAutorizanElEndpoint() throws Exception {
        UserDetails jefe = User.withUsername("jefe-http").password("unused").roles("JEFE_INVENTARIO").build();
        when(userDetailsService.loadUserByUsername("jefe-http")).thenReturn(jefe);
        String valid = jwtService.generateToken(jefe, Map.of());
        String[] parts = valid.split("\\.");
        char first = parts[2].charAt(0);
        parts[2] = (first == 'a' ? "b" : "a") + parts[2].substring(1);
        String tampered = String.join(".", parts);
        String expired = Jwts.builder().setSubject("jefe-http")
                .setExpiration(new Date(System.currentTimeMillis() - 1000))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")), SignatureAlgorithm.HS256)
                .compact();

        mockMvc.perform(get("/api/inventario/movimientos").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/inventario/movimientos").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(inventarioService);
    }
}

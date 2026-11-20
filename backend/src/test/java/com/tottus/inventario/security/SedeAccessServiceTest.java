package com.tottus.inventario.security;

import com.tottus.inventario.model.Sede;
import com.tottus.inventario.model.Rol;
import com.tottus.inventario.model.Usuario;
import com.tottus.inventario.repository.UsuarioRepository;
import com.tottus.inventario.repository.UsuarioSedeRepository;
import com.tottus.inventario.repository.SedeRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SedeAccessServiceTest {
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private UsuarioSedeRepository usuarioSedeRepository;
    @Mock private SedeRepository sedeRepository;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void usaLaSedeAsignadaCuandoLaSolicitudNoIndicaSede() {
        authenticateAs("operador01");
        when(usuarioRepository.findByUsuario("operador01")).thenReturn(Optional.of(user(7, true)));

        assertEquals(7, service().requireAuthorizedSede(null));
    }

    @Test
    void rechazaSedeDistintaDeLaAsignada() {
        authenticateAs("operador01");
        when(usuarioRepository.findByUsuario("operador01")).thenReturn(Optional.of(user(7, true)));

        assertThrows(AccessDeniedException.class,
                () -> service().requireAuthorizedSede(8));
    }

    @Test
    void rechazaUsuarioSinSedeAsignada() {
        authenticateAs("operador01");
        when(usuarioRepository.findByUsuario("operador01")).thenReturn(Optional.of(user(null, true)));

        assertThrows(AccessDeniedException.class,
                () -> service().requireAuthorizedSede(null));
    }

    @Test
    void permiteUnaSedeAdicionalAsignadaEnBaseDeDatos() {
        authenticateAs("operador01");
        when(usuarioRepository.findByUsuario("operador01")).thenReturn(Optional.of(user(7, true)));
        when(usuarioSedeRepository.existsByUsuario_IdUsuarioAndSede_IdSede(99, 8)).thenReturn(true);

        assertEquals(8, service().requireAuthorizedSede(8));
    }

    @Test
    void administradorGlobalSoloPuedeElegirSedesActivasExistentes() {
        authenticateAs("global-admin");
        Usuario global = user(7, true);
        global.setAccesoGlobal(true);
        when(usuarioRepository.findByUsuario("global-admin")).thenReturn(Optional.of(global));
        when(sedeRepository.findById(8)).thenReturn(Optional.of(Sede.builder().idSede(8).estado(true).build()));

        assertEquals(8, service().requireAuthorizedSede(8));
    }

    private void authenticateAs(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "n/a", java.util.List.of()));
    }

    private Usuario user(Integer sedeId, boolean active) {
        Sede sede = sedeId == null ? null : Sede.builder().idSede(sedeId).build();
        return Usuario.builder().idUsuario(99).usuario("operador01").sede(sede).estado(active)
                .rol(Rol.builder().nombre("OPERADOR_ALMACEN").estado(true).build()).build();
    }

    private SedeAccessService service() {
        return new SedeAccessService(usuarioRepository, usuarioSedeRepository, sedeRepository);
    }
}

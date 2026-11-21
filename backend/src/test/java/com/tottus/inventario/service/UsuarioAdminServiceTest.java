package com.tottus.inventario.service;

import com.tottus.inventario.dto.UsuarioAdminAccessUpdateRequestDTO;
import com.tottus.inventario.model.*;
import com.tottus.inventario.repository.*;
import com.tottus.inventario.security.SedeAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioAdminServiceTest {
    @Mock private UsuarioRepository usuarios;
    @Mock private UsuarioSedeRepository usuarioSedes;
    @Mock private RolRepository roles;
    @Mock private SedeRepository sedes;
    @Mock private SedeAccessService sedeAccess;
    @Mock private org.springframework.security.crypto.password.PasswordEncoder encoder;
    @Mock private AuditoriaRepository auditoria;
    private UsuarioAdminService service;

    @BeforeEach void setUp() {
        service = new UsuarioAdminService(usuarios, usuarioSedes, roles, sedes, sedeAccess, encoder, auditoria);
    }

    @Test void administradorGlobalPuedeCambiarRolYSedeYSeAudita() {
        Sede primary = Sede.builder().idSede(2).nombre("Jockey Plaza").estado(true).build();
        Rol newRole = Rol.builder().idRol(4).nombre("VENDEDOR").estado(true).build();
        Usuario actor = Usuario.builder().idUsuario(1).usuario("global").accesoGlobal(true)
                .rol(Rol.builder().nombre("ADMINISTRADOR").estado(true).build()).build();
        Usuario target = Usuario.builder().idUsuario(7).usuario("cuenta").accesoGlobal(false)
                .rol(Rol.builder().nombre("JEFE_INVENTARIO").estado(true).build())
                .sede(Sede.builder().idSede(1).nombre("MegaPlaza").estado(true).build()).build();
        when(sedeAccess.authenticatedUser()).thenReturn(actor);
        when(sedeAccess.authorizedSiteIds(actor)).thenReturn(List.of(1, 2, 3, 4));
        when(usuarios.findById(7)).thenReturn(Optional.of(target));
        when(sedes.findById(2)).thenReturn(Optional.of(primary));
        when(roles.findByNombre("VENDEDOR")).thenReturn(Optional.of(newRole));
        when(usuarios.save(target)).thenReturn(target);
        when(usuarioSedes.findByUsuario_IdUsuario(7)).thenReturn(List.of());

        UsuarioAdminAccessUpdateRequestDTO request = new UsuarioAdminAccessUpdateRequestDTO();
        request.setRol("VENDEDOR"); request.setIdSedes(List.of(2)); request.setAccesoGlobal(false);
        var updated = service.actualizarAcceso(7, request);

        assertEquals("VENDEDOR", updated.getRol());
        assertEquals(List.of(2), updated.getIdSedesAutorizadas());
        assertFalse(updated.isAccesoGlobal());
        verify(usuarioSedes).deleteByUsuario_IdUsuario(7);
        verify(usuarioSedes).flush();
        verify(usuarioSedes).save(any(UsuarioSede.class));
        verify(auditoria).save(argThat(event -> "USUARIO_ACCESO_MODIFICADO".equals(event.getAccion())));
    }

    @Test void administradorDeSedeNoPuedeElevarCuentaNiConcederAccesoGlobal() {
        Sede site = Sede.builder().idSede(1).nombre("MegaPlaza").estado(true).build();
        Usuario actor = Usuario.builder().idUsuario(1).usuario("admin.sede").accesoGlobal(false).sede(site)
                .rol(Rol.builder().nombre("ADMINISTRADOR").estado(true).build()).build();
        Usuario target = Usuario.builder().idUsuario(7).usuario("cuenta").accesoGlobal(false).sede(site)
                .rol(Rol.builder().nombre("VENDEDOR").estado(true).build()).build();
        when(sedeAccess.authenticatedUser()).thenReturn(actor);
        when(sedeAccess.authorizedSiteIds(actor)).thenReturn(List.of(1));
        when(usuarios.findById(7)).thenReturn(Optional.of(target));
        when(usuarioSedes.findByUsuario_IdUsuario(7)).thenReturn(List.of());
        UsuarioAdminAccessUpdateRequestDTO request = new UsuarioAdminAccessUpdateRequestDTO();
        request.setRol("ADMINISTRADOR"); request.setIdSedes(List.of(1)); request.setAccesoGlobal(true);

        assertThrows(AccessDeniedException.class, () -> service.actualizarAcceso(7, request));
        verify(usuarios, never()).save(any());
        verifyNoInteractions(roles, sedes, auditoria);
    }
}

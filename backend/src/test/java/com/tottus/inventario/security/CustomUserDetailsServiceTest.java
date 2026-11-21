package com.tottus.inventario.security;

import com.tottus.inventario.model.Rol;
import com.tottus.inventario.model.Usuario;
import com.tottus.inventario.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {
    @Mock private UsuarioRepository usuarioRepository;

    @Test
    void rechazaUsuarioInactivo() {
        when(usuarioRepository.findByUsuario("inactive"))
                .thenReturn(Optional.of(Usuario.builder().usuario("inactive").estado(false).build()));

        assertThrows(UsernameNotFoundException.class,
                () -> new CustomUserDetailsService(usuarioRepository).loadUserByUsername("inactive"));
    }

    @Test
    void rechazaRolInactivo() {
        Rol role = Rol.builder().nombre("OPERADOR_ALMACEN").estado(false).build();
        Usuario user = Usuario.builder().usuario("inactive-role").estado(true).rol(role).build();
        when(usuarioRepository.findByUsuario("inactive-role")).thenReturn(Optional.of(user));

        assertThrows(UsernameNotFoundException.class,
                () -> new CustomUserDetailsService(usuarioRepository).loadUserByUsername("inactive-role"));
    }
}

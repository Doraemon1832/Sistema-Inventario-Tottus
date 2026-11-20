package com.tottus.inventario.security;

import com.tottus.inventario.model.Usuario;
import com.tottus.inventario.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Usuario usuario = usuarioRepository.findByUsuario(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + username));

        if (!Boolean.TRUE.equals(usuario.getEstado())) {
            throw new UsernameNotFoundException("Usuario inactivo: " + username);
        }

        if (usuario.getRol() == null || !Boolean.TRUE.equals(usuario.getRol().getEstado())) {
            throw new UsernameNotFoundException("Rol de usuario inactivo o no asignado");
        }

        String rolNombre = usuario.getRol().getNombre();

        var authorities = new ArrayList<SimpleGrantedAuthority>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + rolNombre));
        if (Boolean.TRUE.equals(usuario.getCambioPasswordRequerido()))
            authorities.add(new SimpleGrantedAuthority("ROLE_PASSWORD_CHANGE_REQUIRED"));

        return new User(
                usuario.getUsuario(),
                usuario.getPasswordHash(),
                authorities
        );
    }
}

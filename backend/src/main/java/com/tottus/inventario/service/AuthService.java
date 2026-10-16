package com.tottus.inventario.service;

import com.tottus.inventario.model.Auditoria;
import com.tottus.inventario.model.Usuario;
import com.tottus.inventario.repository.AuditoriaRepository;
import com.tottus.inventario.repository.UsuarioRepository;
import com.tottus.inventario.security.CustomUserDetailsService;
import com.tottus.inventario.security.JwtService;
import com.tottus.inventario.security.dto.LoginRequest;
import com.tottus.inventario.security.dto.LoginResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final AuthenticationManager authenticationManager;
    private final CustomUserDetailsService userDetailsService;
    private final JwtService jwtService;
    private final AuditoriaRepository auditoriaRepository;

    public LoginResponse login(LoginRequest request, String ipAddress) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsuario(), request.getPassword())
            );
        } catch (Exception e) {
            throw new BadCredentialsException("Credenciales inválidas");
        }

        Usuario usuario = usuarioRepository.findByUsuario(request.getUsuario())
                .orElseThrow(() -> new BadCredentialsException("Usuario no encontrado"));

        UserDetails userDetails = userDetailsService.loadUserByUsername(usuario.getUsuario());

        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("idUsuario", usuario.getIdUsuario());
        extraClaims.put("rol", usuario.getRol() != null ? usuario.getRol().getNombre() : "USER");
        if (usuario.getSede() != null) {
            extraClaims.put("idSede", usuario.getSede().getIdSede());
            extraClaims.put("nombreSede", usuario.getSede().getNombre());
        }

        String token = jwtService.generateToken(userDetails, extraClaims);

        // Registrar auditoria
        Auditoria audit = Auditoria.builder()
                .usuario(usuario)
                .accion("LOGIN_EXITOSO")
                .fecha(LocalDateTime.now())
                .ip(ipAddress != null ? ipAddress : "127.0.0.1")
                .detalle("Inicio de sesión en el sistema de inventario Tottus")
                .build();
        auditoriaRepository.save(audit);

        return LoginResponse.builder()
                .token(token)
                .idUsuario(usuario.getIdUsuario())
                .usuario(usuario.getUsuario())
                .rol(usuario.getRol() != null ? usuario.getRol().getNombre() : "USER")
                .idSede(usuario.getSede() != null ? usuario.getSede().getIdSede() : null)
                .nombreSede(usuario.getSede() != null ? usuario.getSede().getNombre() : null)
                .build();
    }
}

package com.tottus.inventario.service;

import com.tottus.inventario.model.Auditoria;
import com.tottus.inventario.model.Usuario;
import com.tottus.inventario.repository.AuditoriaRepository;
import com.tottus.inventario.repository.UsuarioRepository;
import com.tottus.inventario.security.CustomUserDetailsService;
import com.tottus.inventario.security.JwtService;
import com.tottus.inventario.security.LoginAttemptLimiter;
import com.tottus.inventario.security.SedeAccessService;
import com.tottus.inventario.security.dto.LoginRequest;
import com.tottus.inventario.security.dto.LoginResponse;
import com.tottus.inventario.security.dto.PasswordChangeRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final AuthenticationManager authenticationManager;
    private final CustomUserDetailsService userDetailsService;
    private final JwtService jwtService;
    private final AuditoriaRepository auditoriaRepository;
    private final LoginAttemptLimiter loginAttemptLimiter;
    private final SedeAccessService sedeAccessService;
    private final PasswordEncoder passwordEncoder;

    public LoginResponse login(LoginRequest request, String ipAddress) {
        loginAttemptLimiter.checkAndRecordAttempt(ipAddress);
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsuario(), request.getPassword())
            );
        } catch (AuthenticationException e) {
            log.warn("Autenticación rechazada desde {}", ipAddress == null ? "No disponible" : ipAddress);
            throw new BadCredentialsException("Credenciales inválidas");
        }

        loginAttemptLimiter.recordSuccess(ipAddress);

        Usuario usuario = usuarioRepository.findByUsuario(request.getUsuario())
                .orElseThrow(() -> new BadCredentialsException("Usuario no encontrado"));

        UserDetails userDetails = userDetailsService.loadUserByUsername(usuario.getUsuario());

        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("idUsuario", usuario.getIdUsuario());
        extraClaims.put("rol", usuario.getRol() != null ? usuario.getRol().getNombre() : "USER");
        extraClaims.put("accesoGlobal", Boolean.TRUE.equals(usuario.getAccesoGlobal()));
        if (usuario.getSede() != null) {
            extraClaims.put("idSede", usuario.getSede().getIdSede());
            extraClaims.put("nombreSede", usuario.getSede().getNombre());
        }

        String token = jwtService.generateToken(userDetails, extraClaims);

        // Registrar auditoria
        Auditoria audit = Auditoria.builder()
                .usuario(usuario)
                .accion("LOGIN_EXITOSO")
                .fecha(LocalDateTime.now(ZoneId.of("America/Lima")))
                .ip(ipAddress != null ? ipAddress : "No disponible")
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
                .idSedesAutorizadas(sedeAccessService.authorizedSiteIds(usuario))
                .accesoGlobal(Boolean.TRUE.equals(usuario.getAccesoGlobal()))
                .cambioPasswordRequerido(Boolean.TRUE.equals(usuario.getCambioPasswordRequerido()))
                .build();
    }

    @Transactional
    public void changePassword(String username, PasswordChangeRequest request) {
        Usuario user = usuarioRepository.findByUsuario(username)
                .orElseThrow(() -> new BadCredentialsException("Usuario no encontrado"));
        if (!Boolean.TRUE.equals(user.getEstado()) || !passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash()))
            throw new BadCredentialsException("La contraseña actual no es válida");
        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash()))
            throw new IllegalArgumentException("La nueva contraseña debe ser diferente de la actual");
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setCambioPasswordRequerido(false);
        usuarioRepository.save(user);
        auditoriaRepository.save(Auditoria.builder().usuario(user).accion("PASSWORD_ACTUALIZADO")
                .fecha(LocalDateTime.now(ZoneId.of("America/Lima")))
                .ip("Autenticado").detalle("El usuario actualizó su contraseña").build());
    }
}

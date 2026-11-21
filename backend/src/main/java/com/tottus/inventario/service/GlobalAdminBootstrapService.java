package com.tottus.inventario.service;

import com.tottus.inventario.dto.UsuarioAdminResponseDTO;
import com.tottus.inventario.model.Auditoria;
import com.tottus.inventario.model.Rol;
import com.tottus.inventario.model.Sede;
import com.tottus.inventario.model.Usuario;
import com.tottus.inventario.model.UsuarioSede;
import com.tottus.inventario.repository.AuditoriaRepository;
import com.tottus.inventario.repository.RolRepository;
import com.tottus.inventario.repository.SedeRepository;
import com.tottus.inventario.repository.UsuarioRepository;
import com.tottus.inventario.repository.UsuarioSedeRepository;
import com.tottus.inventario.security.ClientAddress;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GlobalAdminBootstrapService {
    private static final String USERNAME = "gerencia_admin_global";
    private final UsuarioRepository usuarioRepository;
    private final UsuarioSedeRepository usuarioSedeRepository;
    private final RolRepository rolRepository;
    private final SedeRepository sedeRepository;
    private final AuditoriaRepository auditoriaRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.security.global-admin-bootstrap-token:}")
    private String configuredToken;

    @Transactional
    public UsuarioAdminResponseDTO bootstrap(String suppliedToken, String initialPassword) {
        if (configuredToken == null || configuredToken.length() < 32 || suppliedToken == null
                || !MessageDigest.isEqual(configuredToken.getBytes(StandardCharsets.UTF_8), suppliedToken.getBytes(StandardCharsets.UTF_8)))
            throw new AccessDeniedException("Bootstrap de Administrador General no autorizado");
        if (usuarioRepository.existsByAccesoGlobalTrue() || usuarioRepository.existsByUsuario(USERNAME))
            throw new IllegalArgumentException("El Administrador General ya fue inicializado");
        if (initialPassword == null || initialPassword.length() < 16 || initialPassword.length() > 72)
            throw new IllegalArgumentException("La contraseña inicial debe tener entre 16 y 72 caracteres");
        Rol adminRole = rolRepository.findByNombre("ADMINISTRADOR").filter(role -> Boolean.TRUE.equals(role.getEstado()))
                .orElseThrow(() -> new EntityNotFoundException("Rol Administrador no encontrado"));
        List<Sede> sites = sedeRepository.findByEstadoTrue();
        if (sites.isEmpty()) throw new IllegalStateException("No hay sedes activas para inicializar el administrador global");
        Usuario created = usuarioRepository.save(Usuario.builder().usuario(USERNAME).rol(adminRole).sede(sites.getFirst())
                .passwordHash(passwordEncoder.encode(initialPassword)).estado(true).accesoGlobal(true)
                .cambioPasswordRequerido(true).build());
        sites.forEach(site -> usuarioSedeRepository.save(UsuarioSede.builder().usuario(created).sede(site).build()));
        auditoriaRepository.save(Auditoria.builder().usuario(created).accion("ADMIN_GLOBAL_INICIALIZADO")
                .detalle("Cuenta gerencia_admin_global inicializada con alcance global y cambio de contraseña obligatorio")
                .fecha(LocalDateTime.now(ZoneId.of("America/Lima"))).ip(ClientAddress.currentRemoteAddress()).build());
        return UsuarioAdminResponseDTO.builder().idUsuario(created.getIdUsuario()).usuario(created.getUsuario())
                .rol(adminRole.getNombre()).idSedePrincipal(sites.getFirst().getIdSede()).sedePrincipal(sites.getFirst().getNombre())
                .idSedesAutorizadas(sites.stream().map(Sede::getIdSede).toList()).accesoGlobal(true).estado(true)
                .cambioPasswordRequerido(true).build();
    }
}

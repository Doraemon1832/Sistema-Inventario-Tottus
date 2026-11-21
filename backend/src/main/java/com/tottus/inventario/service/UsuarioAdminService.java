package com.tottus.inventario.service;

import com.tottus.inventario.dto.UsuarioAdminRequestDTO;
import com.tottus.inventario.dto.UsuarioAdminResponseDTO;
import com.tottus.inventario.dto.UsuarioAdminAccessUpdateRequestDTO;
import com.tottus.inventario.model.*;
import com.tottus.inventario.repository.*;
import com.tottus.inventario.security.ClientAddress;
import com.tottus.inventario.security.SedeAccessService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class UsuarioAdminService {
    private static final Set<String> ROLES = Set.of("ADMINISTRADOR", "JEFE_INVENTARIO", "OPERADOR_ALMACEN", "VENDEDOR", "ENCARGADO_COMPRAS");
    private final UsuarioRepository usuarioRepository;
    private final UsuarioSedeRepository usuarioSedeRepository;
    private final RolRepository rolRepository;
    private final SedeRepository sedeRepository;
    private final SedeAccessService sedeAccessService;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaRepository auditoriaRepository;

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public List<UsuarioAdminResponseDTO> listar(Integer requestedSiteId, String requestedRole) {
        Usuario actor = sedeAccessService.authenticatedUser();
        Set<Integer> allowed = new HashSet<>(sedeAccessService.authorizedSiteIds(actor));
        if (requestedSiteId != null && !allowed.contains(requestedSiteId))
            throw new AccessDeniedException("No puedes consultar usuarios de una sede no autorizada");
        String roleFilter = requestedRole == null || requestedRole.isBlank() ? null : requestedRole.trim().toUpperCase(Locale.ROOT);
        if (roleFilter != null && !ROLES.contains(roleFilter)) throw new IllegalArgumentException("El filtro de rol no es vÃ¡lido");
        return usuarioRepository.findAll().stream()
                .filter(user -> Boolean.TRUE.equals(actor.getAccesoGlobal()) || assignedSiteIds(user).stream().anyMatch(allowed::contains))
                .filter(user -> requestedSiteId == null || assignedSiteIds(user).contains(requestedSiteId))
                .filter(user -> roleFilter == null || roleFilter.equals(user.getRol().getNombre()))
                .sorted(Comparator.comparing(Usuario::getUsuario))
                .map(this::map).toList();
    }

    @Transactional
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public UsuarioAdminResponseDTO crear(UsuarioAdminRequestDTO request) {
        Usuario actor = sedeAccessService.authenticatedUser();
        String username = request.getUsuario().trim().toLowerCase(Locale.ROOT);
        if (usuarioRepository.existsByUsuario(username)) throw new IllegalArgumentException("El nombre de usuario ya está en uso");
        String roleName = request.getRol().trim().toUpperCase(Locale.ROOT);
        if (!ROLES.contains(roleName)) throw new IllegalArgumentException("El rol solicitado no es válido");
        if (!Boolean.TRUE.equals(actor.getAccesoGlobal()) && (request.isAccesoGlobal() || "ADMINISTRADOR".equals(roleName)))
            throw new AccessDeniedException("Un administrador de sede no puede crear administradores ni asignar ámbito global");
        if (request.getPasswordInicial() == null || request.getPasswordInicial().length() < 12)
            throw new IllegalArgumentException("La contraseña inicial debe tener al menos 12 caracteres");
        if (request.getIdSedes().stream().distinct().count() != request.getIdSedes().size())
            throw new IllegalArgumentException("No repitas sedes en la asignación");
        Set<Integer> allowed = new HashSet<>(sedeAccessService.authorizedSiteIds(actor));
        List<Sede> sites = request.getIdSedes().stream().map(id -> {
            Sede site = sedeRepository.findById(id).filter(s -> Boolean.TRUE.equals(s.getEstado()))
                    .orElseThrow(() -> new EntityNotFoundException("Sede no encontrada o inactiva: " + id));
            if (!Boolean.TRUE.equals(actor.getAccesoGlobal()) && !allowed.contains(id))
                throw new AccessDeniedException("No puedes asignar usuarios a una sede no autorizada");
            return site;
        }).toList();
        Rol role = rolRepository.findByNombre(roleName).filter(r -> Boolean.TRUE.equals(r.getEstado()))
                .orElseThrow(() -> new EntityNotFoundException("Rol no encontrado o inactivo"));
        Sede primary = sites.getFirst();
        Usuario created = usuarioRepository.save(Usuario.builder().usuario(username).rol(role).sede(primary)
                .passwordHash(passwordEncoder.encode(request.getPasswordInicial())).estado(true)
                .accesoGlobal(request.isAccesoGlobal()).cambioPasswordRequerido(true).build());
        sites.forEach(site -> usuarioSedeRepository.save(UsuarioSede.builder().usuario(created).sede(site).build()));
        audit(actor, "USUARIO_CREADO", "Cuenta " + username + ", rol " + roleName + ", sedes " + assignedSiteIds(created));
        return map(created);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public UsuarioAdminResponseDTO actualizarAcceso(Integer id, UsuarioAdminAccessUpdateRequestDTO request) {
        Usuario actor = sedeAccessService.authenticatedUser();
        Usuario target = accessibleTarget(id, actor);
        if (Objects.equals(actor.getIdUsuario(), target.getIdUsuario()))
            throw new IllegalArgumentException("No puedes modificar tu propio rol o alcance de sedes");
        String roleName = request.getRol().trim().toUpperCase(Locale.ROOT);
        if (!ROLES.contains(roleName)) throw new IllegalArgumentException("El rol solicitado no es vÃ¡lido");
        boolean global = request.isAccesoGlobal();
        if (global && !"ADMINISTRADOR".equals(roleName))
            throw new IllegalArgumentException("El acceso global solo puede pertenecer al rol Administrador");
        if (!Boolean.TRUE.equals(actor.getAccesoGlobal()) && (global || "ADMINISTRADOR".equals(roleName)))
            throw new AccessDeniedException("Un administrador de sede no puede asignar alcance global ni elevar a administrador");
        if (request.getIdSedes().stream().distinct().count() != request.getIdSedes().size())
            throw new IllegalArgumentException("No repitas sedes en la asignaciÃ³n");
        Set<Integer> allowed = new HashSet<>(sedeAccessService.authorizedSiteIds(actor));
        List<Sede> sites = request.getIdSedes().stream().map(siteId -> {
            Sede site = sedeRepository.findById(siteId).filter(s -> Boolean.TRUE.equals(s.getEstado()))
                    .orElseThrow(() -> new EntityNotFoundException("Sede no encontrada o inactiva: " + siteId));
            if (!Boolean.TRUE.equals(actor.getAccesoGlobal()) && !allowed.contains(siteId))
                throw new AccessDeniedException("No puedes asignar una sede no autorizada");
            return site;
        }).toList();
        Rol role = rolRepository.findByNombre(roleName).filter(r -> Boolean.TRUE.equals(r.getEstado()))
                .orElseThrow(() -> new EntityNotFoundException("Rol no encontrado o inactivo"));
        String before = "rol=" + target.getRol().getNombre() + ", sedes=" + assignedSiteIds(target)
                + ", global=" + Boolean.TRUE.equals(target.getAccesoGlobal());
        usuarioSedeRepository.deleteByUsuario_IdUsuario(target.getIdUsuario());
        usuarioSedeRepository.flush();
        target.setRol(role);
        target.setSede(sites.getFirst());
        target.setAccesoGlobal(global);
        Usuario saved = usuarioRepository.save(target);
        sites.forEach(site -> usuarioSedeRepository.save(UsuarioSede.builder().usuario(saved).sede(site).build()));
        audit(actor, "USUARIO_ACCESO_MODIFICADO", "Cuenta " + target.getUsuario() + "; antes: " + before
                + "; ahora: rol=" + roleName + ", sedes=" + sites.stream().map(Sede::getIdSede).toList() + ", global=" + global);
        return map(saved);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public UsuarioAdminResponseDTO estado(Integer id, boolean active) {
        Usuario actor = sedeAccessService.authenticatedUser();
        Usuario target = accessibleTarget(id, actor);
        if (Objects.equals(actor.getIdUsuario(), target.getIdUsuario()))
            throw new IllegalArgumentException("No puedes desactivar tu propia cuenta");
        target.setEstado(active);
        audit(actor, active ? "USUARIO_ACTIVADO" : "USUARIO_DESACTIVADO", "Cuenta " + target.getUsuario());
        return map(usuarioRepository.save(target));
    }

    @Transactional
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public UsuarioAdminResponseDTO restablecerPassword(Integer id, String newPassword) {
        Usuario actor = sedeAccessService.authenticatedUser();
        Usuario target = accessibleTarget(id, actor);
        if (Objects.equals(actor.getIdUsuario(), target.getIdUsuario()))
            throw new IllegalArgumentException("Usa el flujo de cambio de contraseña para tu propia cuenta");
        if (newPassword == null || newPassword.length() < 12 || newPassword.length() > 72)
            throw new IllegalArgumentException("La contraseña debe tener entre 12 y 72 caracteres");
        target.setPasswordHash(passwordEncoder.encode(newPassword));
        target.setCambioPasswordRequerido(true);
        audit(actor, "USUARIO_PASSWORD_RESTABLECIDO", "Se restableciÃ³ la clave de " + target.getUsuario());
        return map(usuarioRepository.save(target));
    }

    private Usuario accessibleTarget(Integer id, Usuario actor) {
        Usuario target = usuarioRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));
        if (Boolean.TRUE.equals(actor.getAccesoGlobal())) return target;
        Set<Integer> allowed = new HashSet<>(sedeAccessService.authorizedSiteIds(actor));
        if (!assignedSiteIds(target).stream().anyMatch(allowed::contains) || "ADMINISTRADOR".equals(target.getRol().getNombre()))
            throw new AccessDeniedException("No puedes administrar esta cuenta");
        return target;
    }

    private List<Integer> assignedSiteIds(Usuario user) {
        LinkedHashSet<Integer> ids = new LinkedHashSet<>();
        if (user.getSede() != null) ids.add(user.getSede().getIdSede());
        usuarioSedeRepository.findByUsuario_IdUsuario(user.getIdUsuario()).stream()
                .map(grant -> grant.getSede().getIdSede()).forEach(ids::add);
        return List.copyOf(ids);
    }

    private UsuarioAdminResponseDTO map(Usuario user) {
        return UsuarioAdminResponseDTO.builder().idUsuario(user.getIdUsuario()).usuario(user.getUsuario())
                .rol(user.getRol().getNombre()).idSedePrincipal(user.getSede() == null ? null : user.getSede().getIdSede())
                .sedePrincipal(user.getSede() == null ? null : user.getSede().getNombre())
                .idSedesAutorizadas(assignedSiteIds(user)).accesoGlobal(Boolean.TRUE.equals(user.getAccesoGlobal()))
                .estado(Boolean.TRUE.equals(user.getEstado())).cambioPasswordRequerido(Boolean.TRUE.equals(user.getCambioPasswordRequerido())).build();
    }

    private void audit(Usuario actor, String action, String details) {
        auditoriaRepository.save(Auditoria.builder().usuario(actor).accion(action).detalle(details)
                .fecha(java.time.LocalDateTime.now(java.time.ZoneId.of("America/Lima")))
                .ip(ClientAddress.currentRemoteAddress()).build());
    }
}

package com.tottus.inventario.security;

import com.tottus.inventario.model.Sede;
import com.tottus.inventario.model.Usuario;
import com.tottus.inventario.repository.SedeRepository;
import com.tottus.inventario.repository.UsuarioRepository;
import com.tottus.inventario.repository.UsuarioSedeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SedeAccessService {
    private final UsuarioRepository usuarioRepository;
    private final UsuarioSedeRepository usuarioSedeRepository;
    private final SedeRepository sedeRepository;

    /** Resolves an omitted sede to the primary site; explicit IDs are checked against current DB grants. */
    public Integer requireAuthorizedSede(Integer requestedSedeId) {
        Usuario user = authenticatedUser();
        if (requestedSedeId == null) {
            if (user.getSede() == null || user.getSede().getIdSede() == null)
                throw new AccessDeniedException("Usuario sin sede principal asignada");
            requestedSedeId = user.getSede().getIdSede();
        }
        if (Boolean.TRUE.equals(user.getAccesoGlobal())) {
            if (sedeRepository.findById(requestedSedeId).filter(s -> Boolean.TRUE.equals(s.getEstado())).isPresent())
                return requestedSedeId;
            throw new AccessDeniedException("La sede solicitada no existe o está inactiva");
        }
        Integer userId = user.getIdUsuario();
        boolean primarySite = user.getSede() != null && requestedSedeId.equals(user.getSede().getIdSede());
        boolean explicitGrant = userId != null && usuarioSedeRepository.existsByUsuario_IdUsuarioAndSede_IdSede(userId, requestedSedeId);
        if (!primarySite && !explicitGrant)
            throw new AccessDeniedException("Acceso denegado a la sede solicitada");
        return requestedSedeId;
    }

    @Transactional(readOnly = true)
    public List<Sede> authorizedSedes() {
        Usuario user = authenticatedUser();
        if (Boolean.TRUE.equals(user.getAccesoGlobal())) return sedeRepository.findByEstadoTrue();
        List<Sede> sites = user.getIdUsuario() == null ? List.of()
                : usuarioSedeRepository.findByUsuario_IdUsuario(user.getIdUsuario()).stream()
                .map(grant -> grant.getSede()).filter(s -> Boolean.TRUE.equals(s.getEstado())).toList();
        if (user.getSede() != null && Boolean.TRUE.equals(user.getSede().getEstado())
                && sites.stream().noneMatch(s -> s.getIdSede().equals(user.getSede().getIdSede()))) {
            return java.util.stream.Stream.concat(sites.stream(), java.util.stream.Stream.of(user.getSede())).toList();
        }
        return sites;
    }

    @Transactional(readOnly = true)
    public List<Integer> authorizedSiteIds(Usuario user) {
        if (Boolean.TRUE.equals(user.getAccesoGlobal()))
            return sedeRepository.findByEstadoTrue().stream().map(Sede::getIdSede).toList();
        java.util.LinkedHashSet<Integer> ids = new java.util.LinkedHashSet<>();
        if (user.getSede() != null && Boolean.TRUE.equals(user.getSede().getEstado())) ids.add(user.getSede().getIdSede());
        if (user.getIdUsuario() != null) usuarioSedeRepository.findByUsuario_IdUsuario(user.getIdUsuario()).stream()
                .map(grant -> grant.getSede()).filter(s -> Boolean.TRUE.equals(s.getEstado()))
                .map(Sede::getIdSede).forEach(ids::add);
        return List.copyOf(ids);
    }

    public Usuario authenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal()))
            throw new AccessDeniedException("Autenticación requerida");
        Usuario user = usuarioRepository.findByUsuario(authentication.getName())
                .orElseThrow(() -> new AccessDeniedException("Usuario sin perfil autorizado"));
        if (!Boolean.TRUE.equals(user.getEstado()) || user.getRol() == null
                || !Boolean.TRUE.equals(user.getRol().getEstado()))
            throw new AccessDeniedException("Usuario o rol inactivo");
        return user;
    }
}

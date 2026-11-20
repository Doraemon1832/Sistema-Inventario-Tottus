package com.tottus.inventario.service;

import com.tottus.inventario.dto.InventarioResponseDTO;
import com.tottus.inventario.dto.MovimientoRequestDTO;
import com.tottus.inventario.dto.MovimientoResponseDTO;
import com.tottus.inventario.dto.MovimientoPageResponseDTO;
import com.tottus.inventario.dto.ResumenInventarioDTO;
import com.tottus.inventario.model.*;
import com.tottus.inventario.repository.*;
import com.tottus.inventario.security.SedeAccessService;
import com.tottus.inventario.security.ClientAddress;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventarioService {

    private final InventarioRepository inventarioRepository;
    private final MovimientoRepository movimientoRepository;
    private final ProductoRepository productoRepository;
    private final SedeRepository sedeRepository;
    private final UsuarioRepository usuarioRepository;
    private final AuditoriaRepository auditoriaRepository;
    private final SedeAccessService sedeAccessService;

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','JEFE_INVENTARIO','OPERADOR_ALMACEN','VENDEDOR')")
    public List<InventarioResponseDTO> getInventario(Integer sedeId, Integer categoriaId, String query, String stockStatus) {
        sedeId = sedeAccessService.requireAuthorizedSede(sedeId);
        List<Inventario> list = inventarioRepository.searchInventario(sedeId, categoriaId, (query != null && !query.trim().isEmpty()) ? query.trim() : null);

        return list.stream()
                .map(this::mapToDTO)
                .filter(dto -> {
                    if (stockStatus == null || stockStatus.trim().isEmpty() || "TODOS".equalsIgnoreCase(stockStatus)) {
                        return true;
                    }
                    return stockStatus.equalsIgnoreCase(dto.getEstadoStock());
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','JEFE_INVENTARIO')")
    public ResumenInventarioDTO getResumen(Integer sedeId) {
        sedeId = sedeAccessService.requireAuthorizedSede(sedeId);
        long totalProductos = inventarioRepository.searchInventario(sedeId, null, null).size();
        long stockBajo = inventarioRepository.countStockBajo(sedeId);
        long sinStock = inventarioRepository.countAgotados(sedeId);
        long totalUnidades = inventarioRepository.sumTotalUnidades(sedeId);
        long movimientosHoy = movimientoRepository.countMovimientosDesde(sedeId, LocalDate.now(ZoneId.of("America/Lima")).atStartOfDay());

        return ResumenInventarioDTO.builder()
                .totalProductos(totalProductos)
                .stockBajo(stockBajo)
                .sinStock(sinStock)
                .totalUnidades(totalUnidades)
                .movimientosHoy(movimientosHoy)
                .build();
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','OPERADOR_ALMACEN','VENDEDOR')")
    public MovimientoResponseDTO registrarMovimiento(MovimientoRequestDTO request, String username) {
        if (request == null || request.getIdSede() == null || request.getIdProducto() == null
                || request.getCantidad() == null || request.getTipo() == null) {
            throw new IllegalArgumentException("Sede, producto, tipo y cantidad son obligatorios");
        }
        String tipo = request.getTipo().trim().toUpperCase();
        int cantidad = request.getCantidad();
        if (("ENTRADA".equals(tipo) || "SALIDA".equals(tipo)) && cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor que cero");
        }
        if ("AJUSTE".equals(tipo) && cantidad < 0) {
            throw new IllegalArgumentException("El stock final del ajuste no puede ser negativo");
        }
        if (!List.of("ENTRADA", "SALIDA", "AJUSTE").contains(tipo)) {
            throw new IllegalArgumentException("Tipo de movimiento inválido");
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean admin = hasRole(authentication, "ROLE_ADMINISTRADOR");
        boolean operador = hasRole(authentication, "ROLE_OPERADOR_ALMACEN");
        boolean vendedor = hasRole(authentication, "ROLE_VENDEDOR");
        if (("ENTRADA".equals(tipo) && !(admin || operador))
                || ("SALIDA".equals(tipo) && !(admin || operador || vendedor))
                || ("AJUSTE".equals(tipo) && !admin)) {
            throw new org.springframework.security.access.AccessDeniedException("Rol no autorizado para este movimiento");
        }
        Integer authorizedSedeId = sedeAccessService.requireAuthorizedSede(request.getIdSede());
        Sede sede = sedeRepository.findById(request.getIdSede())
                .orElseThrow(() -> new EntityNotFoundException("Sede no encontrada"));

        Producto producto = productoRepository.findById(request.getIdProducto())
                .orElseThrow(() -> new EntityNotFoundException("Producto no encontrado"));

        if (Boolean.FALSE.equals(producto.getEstado())) {
            throw new IllegalArgumentException("No se pueden registrar operaciones: El producto '" + producto.getNombre() + "' (" + producto.getCodigo() + ") se encuentra INACTIVO.");
        }

        Usuario usuario = usuarioRepository.findByUsuario(username)
                .orElse(null);

        Inventario inventario = inventarioRepository.findForUpdate(authorizedSedeId, request.getIdProducto())
                .orElseThrow(() -> new EntityNotFoundException("No existe inventario del producto en la sede asignada"));

        int stockActual = inventario.getStockActual() != null ? inventario.getStockActual() : 0;
        int stockPosterior;

        switch (tipo) {
            case "ENTRADA":
                stockPosterior = Math.addExact(stockActual, cantidad);
                inventario.setStockActual(stockPosterior);
                break;
            case "SALIDA":
                if (stockActual < cantidad) {
                    throw new IllegalArgumentException("Stock insuficiente. Stock actual: " + stockActual + ", Cantidad requerida: " + cantidad);
                }
                stockPosterior = stockActual - cantidad;
                inventario.setStockActual(stockPosterior);
                break;
            case "AJUSTE":
                if (cantidad == stockActual) throw new IllegalArgumentException("El stock ya tiene ese valor; no se registró ningún ajuste");
                stockPosterior = cantidad;
                inventario.setStockActual(stockPosterior);
                break;
            default:
                throw new IllegalArgumentException("Tipo de movimiento inválido: " + tipo);
        }

        inventarioRepository.save(inventario);

        Movimiento mov = Movimiento.builder()
                .sede(sede)
                .producto(producto)
                .usuario(usuario)
                .tipo(tipo)
                .cantidad("AJUSTE".equals(tipo) ? Math.abs(cantidad - stockActual) : cantidad)
                .stockAnterior(stockActual)
                .stockPosterior(inventario.getStockActual())
                .fecha(LocalDateTime.now(ZoneId.of("America/Lima")))
                .referencia(request.getReferencia())
                .build();

        Movimiento savedMov = movimientoRepository.save(mov);

        // Guardar auditoria
        Auditoria audit = Auditoria.builder()
                .usuario(usuario)
                .accion("MOVIMIENTO_INVENTARIO_" + tipo)
                .fecha(LocalDateTime.now(ZoneId.of("America/Lima")))
                .ip(ClientAddress.currentRemoteAddress())
                .detalle("Movimiento de " + tipo + " (" + cantidad + " unids) para '" + producto.getNombre() + "' en " + sede.getNombre())
                .build();
        auditoriaRepository.save(audit);

        return MovimientoResponseDTO.builder()
                .idMovimiento(savedMov.getIdMovimiento())
                .idSede(sede.getIdSede())
                .nombreSede(sede.getNombre())
                .idProducto(producto.getIdProducto())
                .codigoProducto(producto.getCodigo())
                .nombreProducto(producto.getNombre())
                .tipo(savedMov.getTipo())
                .subtipo(savedMov.getSubtipo())
                .cantidad(savedMov.getCantidad())
                .fecha(savedMov.getFecha())
                .referencia(savedMov.getReferencia())
                .usuario(usuario != null ? usuario.getUsuario() : "Sistema")
                .stockAnterior(savedMov.getStockAnterior()).stockPosterior(savedMov.getStockPosterior())
                .unidadMedida(producto.getUnidadMedida())
                .build();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','JEFE_INVENTARIO')")
    public List<MovimientoResponseDTO> getMovimientos(Integer sedeId, String tipo, Integer productoId) {
        return getMovimientos(sedeId, tipo, productoId, null, null);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','JEFE_INVENTARIO')")
    public List<MovimientoResponseDTO> getMovimientos(Integer sedeId, String tipo, Integer productoId, LocalDate desde, LocalDate hasta) {
        sedeId = sedeAccessService.requireAuthorizedSede(sedeId);
        if (desde != null && hasta != null && hasta.isBefore(desde)) throw new IllegalArgumentException("La fecha final no puede ser anterior a la fecha inicial");
        String filterTipo = (tipo != null && !tipo.trim().isEmpty() && !"TODOS".equalsIgnoreCase(tipo)) ? tipo.trim() : null;
        List<Movimiento> list = movimientoRepository.findWithFilters(sedeId, filterTipo, productoId);

        return list.stream()
                .filter(m -> desde == null || (m.getFecha() != null && !m.getFecha().toLocalDate().isBefore(desde)))
                .filter(m -> hasta == null || (m.getFecha() != null && !m.getFecha().toLocalDate().isAfter(hasta)))
                .map(m -> MovimientoResponseDTO.builder()
                .idMovimiento(m.getIdMovimiento())
                .idSede(m.getSede() != null ? m.getSede().getIdSede() : null)
                .nombreSede(m.getSede() != null ? m.getSede().getNombre() : "")
                .idProducto(m.getProducto() != null ? m.getProducto().getIdProducto() : null)
                .codigoProducto(m.getProducto() != null ? m.getProducto().getCodigo() : "")
                .nombreProducto(m.getProducto() != null ? m.getProducto().getNombre() : "")
                .tipo(m.getTipo())
                .subtipo(m.getSubtipo())
                .cantidad(m.getCantidad())
                .fecha(m.getFecha())
                .referencia(m.getReferencia())
                .usuario(m.getUsuario() != null ? m.getUsuario().getUsuario() : "Sistema")
                .stockAnterior(m.getStockAnterior()).stockPosterior(m.getStockPosterior())
                .idTraslado(m.getTraslado() != null ? m.getTraslado().getIdTraslado() : null)
                .codigoTraslado(m.getTraslado() != null ? m.getTraslado().getCodigo() : null)
                .unidadMedida(m.getProducto() != null ? m.getProducto().getUnidadMedida() : null)
                .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','JEFE_INVENTARIO')")
    public MovimientoPageResponseDTO getMovimientosPage(Integer requestedSedeId, String tipo, Integer productoId,
                                                         LocalDate desde, LocalDate hasta, int page, int size) {
        return getMovimientosPage(requestedSedeId, tipo, null, productoId, desde, hasta, page, size);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','JEFE_INVENTARIO')")
    public MovimientoPageResponseDTO getMovimientosPage(Integer requestedSedeId, String tipo, String subtipo, Integer productoId,
                                                         LocalDate desde, LocalDate hasta, int page, int size) {
        if (page < 0) throw new IllegalArgumentException("La página debe ser cero o mayor");
        if (size < 1 || size > 100) throw new IllegalArgumentException("El tamaño de página debe estar entre 1 y 100");
        if (desde != null && hasta != null && hasta.isBefore(desde)) {
            throw new IllegalArgumentException("La fecha final no puede ser anterior a la fecha inicial");
        }
        Integer authorizedSedeId = sedeAccessService.requireAuthorizedSede(requestedSedeId);
        String filterTipo = (tipo == null || tipo.isBlank() || "TODOS".equalsIgnoreCase(tipo)) ? null : tipo.trim().toUpperCase();
        String filterSubtipo = (subtipo == null || subtipo.isBlank() || "TODOS".equalsIgnoreCase(subtipo)) ? null : subtipo.trim().toUpperCase();
        LocalDateTime from = desde == null ? null : desde.atStartOfDay();
        LocalDateTime toExclusive = hasta == null ? null : hasta.plusDays(1).atStartOfDay();
        Page<Movimiento> result = movimientoRepository.findPageWithFilters(authorizedSedeId, filterTipo, filterSubtipo, productoId,
                from, toExclusive, PageRequest.of(page, size, Sort.by(Sort.Order.desc("fecha"), Sort.Order.desc("idMovimiento"))));
        return MovimientoPageResponseDTO.builder()
                .content(result.getContent().stream().map(this::mapMovimiento).toList())
                .page(result.getNumber()).size(result.getSize()).totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages()).build();
    }

    private MovimientoResponseDTO mapMovimiento(Movimiento movement) {
        return MovimientoResponseDTO.builder().idMovimiento(movement.getIdMovimiento())
                .idSede(movement.getSede() != null ? movement.getSede().getIdSede() : null)
                .nombreSede(movement.getSede() != null ? movement.getSede().getNombre() : "")
                .idProducto(movement.getProducto() != null ? movement.getProducto().getIdProducto() : null)
                .codigoProducto(movement.getProducto() != null ? movement.getProducto().getCodigo() : "")
                .nombreProducto(movement.getProducto() != null ? movement.getProducto().getNombre() : "")
                .tipo(movement.getTipo()).subtipo(movement.getSubtipo()).cantidad(movement.getCantidad()).fecha(movement.getFecha())
                .referencia(movement.getReferencia())
                .usuario(movement.getUsuario() != null ? movement.getUsuario().getUsuario() : "Sistema")
                .stockAnterior(movement.getStockAnterior()).stockPosterior(movement.getStockPosterior())
                .idTraslado(movement.getTraslado() != null ? movement.getTraslado().getIdTraslado() : null)
                .codigoTraslado(movement.getTraslado() != null ? movement.getTraslado().getCodigo() : null)
                .unidadMedida(movement.getProducto() != null ? movement.getProducto().getUnidadMedida() : null).build();
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> role.equals(authority.getAuthority()));
    }

    private InventarioResponseDTO mapToDTO(Inventario inv) {
        Producto p = inv.getProducto();
        Sede s = inv.getSede();

        int stockActual = inv.getStockActual() != null ? inv.getStockActual() : 0;
        int stockMinimo = (p != null && p.getStockMinimo() != null) ? p.getStockMinimo() : 0;

        String estado;
        if (stockActual <= 0) {
            estado = "AGOTADO";
        } else if (stockActual <= stockMinimo) {
            estado = "BAJO_STOCK";
        } else {
            estado = "OPTIMO";
        }

        return InventarioResponseDTO.builder()
                .idInventario(inv.getIdInventario())
                .idSede(s != null ? s.getIdSede() : null)
                .nombreSede(s != null ? s.getNombre() : "")
                .idProducto(p != null ? p.getIdProducto() : null)
                .codigoProducto(p != null ? p.getCodigo() : "")
                .nombreProducto(p != null ? p.getNombre() : "")
                .unidadMedida(p != null ? p.getUnidadMedida() : null)
                .categoria(p != null && p.getCategoria() != null ? p.getCategoria().getNombre() : "Sin Categoría")
                .idCategoria(p != null && p.getCategoria() != null ? p.getCategoria().getIdCategoria() : null)
                .precio(p != null ? p.getPrecio() : null)
                .stockActual(stockActual)
                .stockMinimo(stockMinimo)
                .ubicacion(inv.getUbicacion() != null ? inv.getUbicacion() : "Sin Asignar")
                .estadoStock(estado)
                .estado(p != null && p.getEstado() != null ? p.getEstado() : true)
                .build();
    }
}

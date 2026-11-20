package com.tottus.inventario.service;

import com.tottus.inventario.dto.*;
import com.tottus.inventario.model.*;
import com.tottus.inventario.repository.*;
import com.tottus.inventario.security.SedeAccessService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class TrasladoService {
    private final TrasladoRepository trasladoRepository;
    private final DetalleTrasladoRepository detalleRepository;
    private final TrasladoEventoRepository eventoRepository;
    private final InventarioRepository inventarioRepository;
    private final ProductoRepository productoRepository;
    private final SedeRepository sedeRepository;
    private final UsuarioRepository usuarioRepository;
    private final MovimientoRepository movimientoRepository;
    private final SedeAccessService sedeAccessService;

    @Transactional
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','JEFE_INVENTARIO','OPERADOR_ALMACEN')")
    public TrasladoResponseDTO crear(TrasladoCreateRequestDTO request, String username) {
        if (request.getIdSedeOrigen().equals(request.getIdSedeDestino()))
            throw new IllegalArgumentException("La sede de origen y destino deben ser diferentes");
        Integer sourceId = sedeAccessService.requireAuthorizedSede(request.getIdSedeOrigen());
        Sede source = activeSite(sourceId);
        Sede destination = activeSite(request.getIdSedeDestino());
        Map<Integer, Integer> requested = quantities(request.getDetalles());
        Map<Integer, Producto> products = activeProducts(requested.keySet());
        String reference = request.getReferencia().trim();
        if (trasladoRepository.findBySedeOrigen_IdSedeAndReferencia(sourceId, reference).isPresent())
            throw new IllegalArgumentException("La referencia ya existe para un traslado de esta sede");
        Usuario user = user(username);
        LocalDateTime now = now();
        Traslado transfer = trasladoRepository.save(Traslado.builder()
                .codigo("TR-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(Locale.ROOT))
                .sedeOrigen(source).sedeDestino(destination).usuarioSolicita(user)
                .estado("PENDIENTE").referencia(reference).observaciones(trimToNull(request.getObservaciones()))
                .fechaSolicitud(now).build());
        requested.forEach((productId, quantity) -> detalleRepository.save(DetalleTraslado.builder()
                .traslado(transfer).producto(products.get(productId)).cantidadSolicitada(quantity)
                .cantidadDespachada(0).cantidadRecibida(0).build()));
        return map(transfer);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','JEFE_INVENTARIO','OPERADOR_ALMACEN')")
    public List<TrasladoResponseDTO> listar(String estado) {
        Integer sedeId = sedeAccessService.requireAuthorizedSede(null);
        return trasladoRepository.findBySedeOrigen_IdSedeOrSedeDestino_IdSedeOrderByFechaSolicitudDesc(sedeId, sedeId).stream()
                .filter(t -> estado == null || estado.isBlank() || "TODOS".equalsIgnoreCase(estado)
                        || t.getEstado().equalsIgnoreCase(estado.trim()))
                .map(this::map).toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','JEFE_INVENTARIO','OPERADOR_ALMACEN')")
    public List<Sede> listarSedesDestino() {
        sedeAccessService.requireAuthorizedSede(null);
        return sedeRepository.findByEstadoTrue();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','JEFE_INVENTARIO','OPERADOR_ALMACEN')")
    public TrasladoResponseDTO obtener(Integer id) {
        Integer sedeId = sedeAccessService.requireAuthorizedSede(null);
        Traslado transfer = trasladoRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Traslado no encontrado"));
        if (!sedeId.equals(transfer.getSedeOrigen().getIdSede()) && !sedeId.equals(transfer.getSedeDestino().getIdSede()))
            throw new EntityNotFoundException("Traslado no encontrado");
        return map(transfer);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','OPERADOR_ALMACEN')")
    public TrasladoResponseDTO despachar(Integer id, TrasladoActionRequestDTO request, String username) {
        Traslado transfer = locked(id);
        sedeAccessService.requireAuthorizedSede(transfer.getSedeOrigen().getIdSede());
        String payloadHash = hash("DESPACHO|" + transfer.getIdTraslado());
        TrasladoResponseDTO replay = replay(transfer, request.getIdempotencyKey(), "DESPACHO", payloadHash);
        if (replay != null) return replay;
        if (!"PENDIENTE".equals(transfer.getEstado())) throw new IllegalArgumentException("Solo se despachan traslados pendientes");
        Usuario user = user(username);
        LocalDateTime now = now();
        List<DetalleTraslado> lines = detalleRepository.findByTraslado_IdTrasladoOrderByIdDetalleTraslado(id);
        for (DetalleTraslado line : lines) {
            Integer productId = line.getProducto().getIdProducto();
            Inventario inventory = inventarioRepository.findForUpdate(transfer.getSedeOrigen().getIdSede(), productId)
                    .orElseThrow(() -> new EntityNotFoundException("No hay inventario del producto en la sede de origen"));
            int current = Objects.requireNonNullElse(inventory.getStockActual(), 0);
            int quantity = line.getCantidadSolicitada();
            if (current < quantity) throw new IllegalArgumentException("Stock insuficiente para despachar " + line.getProducto().getNombre());
            int after = current - quantity;
            inventory.setStockActual(after);
            inventarioRepository.save(inventory);
            line.setCantidadDespachada(quantity);
            saveMovement(transfer, transfer.getSedeOrigen(), user, line.getProducto(), "SALIDA", "TRANSFERENCIA_ENVIADA", quantity, current, after, now);
        }
        transfer.setUsuarioDespacha(user); transfer.setFechaDespacho(now); transfer.setEstado("EN_TRANSITO");
        trasladoRepository.save(transfer);
        saveEvent(transfer, request, "DESPACHO", payloadHash, now);
        return map(transfer);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','JEFE_INVENTARIO','OPERADOR_ALMACEN')")
    public TrasladoResponseDTO recibir(Integer id, TrasladoActionRequestDTO request, String username) {
        Traslado transfer = locked(id);
        sedeAccessService.requireAuthorizedSede(transfer.getSedeDestino().getIdSede());
        Map<Integer, Integer> received = quantities(request.getDetalles());
        String payloadHash = hash(received.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + ":" + e.getValue()).collect(Collectors.joining("|")));
        TrasladoResponseDTO replay = replay(transfer, request.getIdempotencyKey(), "RECEPCION", payloadHash);
        if (replay != null) return replay;
        if (!List.of("EN_TRANSITO", "RECIBIDO_PARCIAL").contains(transfer.getEstado()))
            throw new IllegalArgumentException("El traslado no está disponible para recepción");
        Usuario user = user(username);
        LocalDateTime now = now();
        Map<Integer, DetalleTraslado> lines = detalleRepository.findByTraslado_IdTrasladoOrderByIdDetalleTraslado(id).stream()
                .collect(Collectors.toMap(d -> d.getProducto().getIdProducto(), d -> d));
        for (var entry : received.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            DetalleTraslado line = lines.get(entry.getKey());
            if (line == null) throw new IllegalArgumentException("El producto no pertenece al traslado");
            int quantity = entry.getValue();
            int inTransit = line.getCantidadDespachada() - line.getCantidadRecibida();
            if (quantity > inTransit) throw new IllegalArgumentException("La recepción supera la cantidad pendiente en tránsito");
            Integer productId = line.getProducto().getIdProducto();
            Inventario inventory = inventarioRepository.findForUpdate(transfer.getSedeDestino().getIdSede(), productId)
                    .orElseGet(() -> Inventario.builder().sede(transfer.getSedeDestino()).producto(line.getProducto()).stockActual(0).build());
            int current = Objects.requireNonNullElse(inventory.getStockActual(), 0);
            int after = Math.addExact(current, quantity);
            inventory.setStockActual(after); inventarioRepository.save(inventory);
            line.setCantidadRecibida(Math.addExact(line.getCantidadRecibida(), quantity));
            saveMovement(transfer, transfer.getSedeDestino(), user, line.getProducto(), "ENTRADA", "TRANSFERENCIA_RECIBIDA", quantity, current, after, now);
        }
        boolean complete = lines.values().stream().allMatch(d -> d.getCantidadRecibida().equals(d.getCantidadDespachada()));
        transfer.setUsuarioRecibe(user);
        transfer.setEstado(complete ? "RECIBIDO" : "RECIBIDO_PARCIAL");
        if (complete) transfer.setFechaFinalizacion(now);
        trasladoRepository.save(transfer);
        saveEvent(transfer, request, "RECEPCION", payloadHash, now);
        return map(transfer);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','JEFE_INVENTARIO','OPERADOR_ALMACEN')")
    public TrasladoResponseDTO cancelar(Integer id, TrasladoActionRequestDTO request, String username) {
        Traslado transfer = locked(id);
        sedeAccessService.requireAuthorizedSede(transfer.getSedeOrigen().getIdSede());
        String returnRef = trimToNull(request.getReferenciaDevolucion());
        String payloadHash = hash("CANCELACION|" + Objects.toString(returnRef, ""));
        TrasladoResponseDTO replay = replay(transfer, request.getIdempotencyKey(), "CANCELACION", payloadHash);
        if (replay != null) return replay;
        if ("PENDIENTE".equals(transfer.getEstado())) {
            transfer.setEstado("CANCELADO");
        } else if ("EN_TRANSITO".equals(transfer.getEstado())
                && detalleRepository.findByTraslado_IdTrasladoOrderByIdDetalleTraslado(id).stream().allMatch(d -> d.getCantidadRecibida() == 0)) {
            if (returnRef == null) throw new IllegalArgumentException("Documenta la devolución física antes de cancelar un traslado despachado");
            Usuario user = user(username); LocalDateTime now = now();
            for (DetalleTraslado line : detalleRepository.findByTraslado_IdTrasladoOrderByIdDetalleTraslado(id)) {
                int quantity = line.getCantidadDespachada();
                Inventario inventory = inventarioRepository.findForUpdate(transfer.getSedeOrigen().getIdSede(), line.getProducto().getIdProducto())
                        .orElseThrow(() -> new EntityNotFoundException("No existe inventario en sede origen para la devolución"));
                int current = Objects.requireNonNullElse(inventory.getStockActual(), 0);
                int after = Math.addExact(current, quantity);
                inventory.setStockActual(after); inventarioRepository.save(inventory);
                saveMovement(transfer, transfer.getSedeOrigen(), user, line.getProducto(), "ENTRADA", "DEVOLUCION_TRASLADO_ORIGEN", quantity, current, after, now);
                line.setCantidadRecibida(quantity);
            }
            transfer.setReferenciaDevolucion(returnRef); transfer.setUsuarioRecibe(user); transfer.setFechaFinalizacion(now);
            transfer.setEstado("CANCELADO");
        } else {
            throw new IllegalArgumentException("No se puede cancelar este traslado en su estado actual; si hubo recepción parcial, resuelve la diferencia con un traslado documentado");
        }
        trasladoRepository.save(transfer);
        saveEvent(transfer, request, "CANCELACION", payloadHash, now());
        return map(transfer);
    }

    private Traslado locked(Integer id) { return trasladoRepository.findForUpdate(id).orElseThrow(() -> new EntityNotFoundException("Traslado no encontrado")); }
    private Sede activeSite(Integer id) {
        Sede site = sedeRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Sede no encontrada"));
        if (!Boolean.TRUE.equals(site.getEstado())) throw new IllegalArgumentException("La sede seleccionada está inactiva");
        return site;
    }
    private Usuario user(String username) { return usuarioRepository.findByUsuario(username).filter(u -> Boolean.TRUE.equals(u.getEstado()))
            .orElseThrow(() -> new AccessDeniedException("Usuario sin perfil activo")); }
    private LocalDateTime now() { return LocalDateTime.now(ZoneId.of("America/Lima")); }
    private String trimToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private Map<Integer, Integer> quantities(List<TrasladoItemRequestDTO> details) {
        if (details == null || details.isEmpty()) throw new IllegalArgumentException("Incluye al menos un producto");
        Map<Integer, Integer> result = new LinkedHashMap<>();
        for (TrasladoItemRequestDTO item : details) {
            if (item == null || item.getIdProducto() == null || item.getIdProducto() <= 0 || item.getCantidad() == null || item.getCantidad() <= 0)
                throw new IllegalArgumentException("Cada producto debe tener una cantidad positiva");
            if (result.putIfAbsent(item.getIdProducto(), item.getCantidad()) != null)
                throw new IllegalArgumentException("No se puede repetir un producto en la misma solicitud");
        }
        return result;
    }
    private Map<Integer, Producto> activeProducts(Collection<Integer> ids) {
        Map<Integer, Producto> products = productoRepository.findAllById(ids).stream().collect(Collectors.toMap(Producto::getIdProducto, p -> p));
        for (Integer id : ids) {
            Producto product = products.get(id);
            if (product == null) throw new EntityNotFoundException("Producto no encontrado: " + id);
            if (!Boolean.TRUE.equals(product.getEstado())) throw new IllegalArgumentException("No se puede trasladar un producto inactivo");
        }
        return products;
    }
    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException("No se pudo asegurar la idempotencia", e); }
    }
    private TrasladoResponseDTO replay(Traslado transfer, String key, String action, String hash) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("La clave de idempotencia es obligatoria");
        return eventoRepository.findByClaveIdempotencia(key).map(event -> {
            if (!event.getTraslado().getIdTraslado().equals(transfer.getIdTraslado())
                    || !event.getAccion().equals(action) || !event.getHashSolicitud().equals(hash))
                throw new IllegalArgumentException("La clave de idempotencia ya se usó con otra solicitud");
            return map(transfer);
        }).orElse(null);
    }
    private void saveEvent(Traslado transfer, TrasladoActionRequestDTO request, String action, String hash, LocalDateTime now) {
        eventoRepository.save(TrasladoEvento.builder().traslado(transfer).claveIdempotencia(request.getIdempotencyKey().trim())
                .accion(action).hashSolicitud(hash).fecha(now).build());
    }
    private void saveMovement(Traslado transfer, Sede site, Usuario user, Producto product, String type, String subtype,
                              int quantity, int before, int after, LocalDateTime now) {
        movimientoRepository.save(Movimiento.builder().sede(site)
                .producto(product).usuario(user).traslado(transfer).tipo(type).subtipo(subtype).cantidad(quantity)
                .stockAnterior(before).stockPosterior(after).fecha(now).referencia(transfer.getCodigo() + " · " + transfer.getReferencia()).build());
    }
    private TrasladoResponseDTO map(Traslado t) {
        return TrasladoResponseDTO.builder().idTraslado(t.getIdTraslado()).codigo(t.getCodigo())
                .idSedeOrigen(t.getSedeOrigen().getIdSede()).sedeOrigen(t.getSedeOrigen().getNombre())
                .idSedeDestino(t.getSedeDestino().getIdSede()).sedeDestino(t.getSedeDestino().getNombre())
                .estado(t.getEstado()).referencia(t.getReferencia()).observaciones(t.getObservaciones())
                .responsable(t.getUsuarioSolicita().getUsuario())
                .responsableDespacho(t.getUsuarioDespacha() == null ? null : t.getUsuarioDespacha().getUsuario())
                .responsableRecepcion(t.getUsuarioRecibe() == null ? null : t.getUsuarioRecibe().getUsuario())
                .fechaSolicitud(t.getFechaSolicitud()).fechaDespacho(t.getFechaDespacho()).fechaFinalizacion(t.getFechaFinalizacion())
                .detalles(detalleRepository.findByTraslado_IdTrasladoOrderByIdDetalleTraslado(t.getIdTraslado()).stream()
                        .map(d -> DetalleTrasladoResponseDTO.builder().idProducto(d.getProducto().getIdProducto())
                                .codigoProducto(d.getProducto().getCodigo()).nombreProducto(d.getProducto().getNombre())
                                .unidadMedida(d.getProducto().getUnidadMedida()).cantidadSolicitada(d.getCantidadSolicitada())
                                .cantidadDespachada(d.getCantidadDespachada()).cantidadRecibida(d.getCantidadRecibida())
                                .cantidadEnTransito(d.getCantidadDespachada() - d.getCantidadRecibida()).build()).toList()).build();
    }
}

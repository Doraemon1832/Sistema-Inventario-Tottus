package com.tottus.inventario.service;

import com.tottus.inventario.dto.*;
import com.tottus.inventario.model.*;
import com.tottus.inventario.repository.*;
import com.tottus.inventario.security.ClientAddress;
import com.tottus.inventario.security.SedeAccessService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OperacionInventarioService {
    private final OperacionInventarioRepository operacionRepository;
    private final DetalleOperacionInventarioRepository detalleRepository;
    private final InventarioRepository inventarioRepository;
    private final MovimientoRepository movimientoRepository;
    private final ProductoRepository productoRepository;
    private final SedeRepository sedeRepository;
    private final UsuarioRepository usuarioRepository;
    private final ProveedorRepository proveedorRepository;
    private final AuditoriaRepository auditoriaRepository;
    private final SedeAccessService sedeAccessService;

    @Transactional
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','JEFE_INVENTARIO','OPERADOR_ALMACEN')")
    public OperacionResponseDTO registrarIngreso(OperacionRequestDTO request, String username) {
        return crear("INGRESO", request, username, false);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','JEFE_INVENTARIO','OPERADOR_ALMACEN')")
    public OperacionResponseDTO registrarIngresoCompra(OperacionRequestDTO request, String username) {
        if (request != null) request.setSubtipo("COMPRA");
        return crear("INGRESO", request, username, true);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','OPERADOR_ALMACEN','VENDEDOR')")
    public OperacionResponseDTO registrarEgreso(OperacionRequestDTO request, String username) {
        return crear("EGRESO", request, username, false);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','JEFE_INVENTARIO')")
    public OperacionResponseDTO aprobarEgreso(Integer id, Integer requestedSede, String username) {
        return revisarEgreso(id, requestedSede, username, true, null);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','JEFE_INVENTARIO')")
    public OperacionResponseDTO rechazarEgreso(Integer id, Integer requestedSede, String username, String reason) {
        if (reason == null || reason.isBlank() || reason.trim().length() > 255)
            throw new IllegalArgumentException("Indica una justificación de rechazo de hasta 255 caracteres");
        return revisarEgreso(id, requestedSede, username, false, reason.trim());
    }

    private OperacionResponseDTO revisarEgreso(Integer id, Integer requestedSede, String username, boolean approve, String reason) {
        OperacionInventario operation = operacionRepository.findForUpdate(id)
                .orElseThrow(() -> new EntityNotFoundException("Egreso no encontrado"));
        if (!"EGRESO".equals(operation.getTipo()) || "VENTA".equals(operation.getSubtipo()))
            throw new EntityNotFoundException("Egreso no encontrado");
        Integer siteId = requestedSede == null ? operation.getSede().getIdSede() : requestedSede;
        Integer allowedSite = sedeAccessService.requireAuthorizedSede(siteId);
        if (!allowedSite.equals(operation.getSede().getIdSede())) throw new EntityNotFoundException("Egreso no encontrado");
        Usuario reviewer = usuarioRepository.findByUsuario(username)
                .orElseThrow(() -> new AccessDeniedException("Usuario no autorizado"));
        if (operation.getUsuario() != null && Objects.equals(operation.getUsuario().getIdUsuario(), reviewer.getIdUsuario()))
            throw new AccessDeniedException("No puedes aprobar o rechazar tu propia solicitud");
        if ("CONFIRMADA".equals(operation.getEstado()) && approve) return map(operation);
        if ("RECHAZADA".equals(operation.getEstado()) && !approve
                && Objects.equals(operation.getMotivoRevision(), reason)) return map(operation);
        if (!"PENDIENTE".equals(operation.getEstado()))
            throw new IllegalArgumentException("La solicitud ya fue revisada y no puede cambiar de estado");

        LocalDateTime now = LocalDateTime.now(ZoneId.of("America/Lima"));
        if (approve) {
            List<DetalleOperacionInventario> details = detalleRepository
                    .findByOperacion_IdOperacionOrderByIdDetalle(operation.getIdOperacion());
            if (details.isEmpty()) throw new IllegalStateException("La solicitud no tiene productos");
            for (DetalleOperacionInventario detail : details.stream()
                    .sorted(Comparator.comparing(d -> d.getProducto().getIdProducto())).toList()) {
                Integer productId = detail.getProducto().getIdProducto();
                Inventario inventory = inventarioRepository.findForUpdate(operation.getSede().getIdSede(), productId)
                        .orElseThrow(() -> new EntityNotFoundException("No existe inventario del producto en la sede"));
                int before = Optional.ofNullable(inventory.getStockActual()).orElse(0);
                int quantity = detail.getCantidad();
                if (before < quantity) throw new IllegalArgumentException("Stock insuficiente para aprobar " + detail.getProducto().getNombre());
                int after = before - quantity;
                inventory.setStockActual(after);
                inventarioRepository.save(inventory);
                movimientoRepository.save(Movimiento.builder().sede(operation.getSede()).producto(detail.getProducto())
                        .usuario(reviewer).operacion(operation).tipo("SALIDA").subtipo(operation.getSubtipo())
                        .cantidad(quantity).stockAnterior(before).stockPosterior(after).fecha(now)
                        .referencia(operation.getReferencia()).build());
            }
            operation.setEstado("CONFIRMADA");
            operation.setMotivoRevision(null);
            audit(reviewer, "EGRESO_APROBADO", "Solicitud " + operation.getReferencia() + " creada por " + operation.getUsuario().getUsuario(), now);
        } else {
            operation.setEstado("RECHAZADA");
            operation.setMotivoRevision(reason);
            audit(reviewer, "EGRESO_RECHAZADO", "Solicitud " + operation.getReferencia() + ": " + reason, now);
        }
        operation.setUsuarioAprueba(reviewer);
        operation.setFechaRevision(now);
        operacionRepository.save(operation);
        return map(operation);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','JEFE_INVENTARIO','OPERADOR_ALMACEN','VENDEDOR','ENCARGADO_COMPRAS')")
    public List<OperacionResponseDTO> listar(String tipo, Integer sedeId, String query, LocalDate desde, LocalDate hasta) {
        String normalized = normalizeType(tipo);
        if (desde != null && hasta != null && hasta.isBefore(desde)) throw new IllegalArgumentException("La fecha final no puede ser anterior a la fecha inicial");
        Integer allowedSede = sedeAccessService.requireAuthorizedSede(sedeId);
        Usuario reader = sedeAccessService.authenticatedUser();
        String term = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return operacionRepository.findByTipoAndSede_IdSedeOrderByFechaDesc(normalized, allowedSede).stream()
                .filter(operation -> canReadOperation(reader, operation))
                .filter(o -> term.isEmpty() || String.valueOf(o.getIdOperacion()).contains(term)
                        || o.getReferencia().toLowerCase(Locale.ROOT).contains(term))
                .filter(o -> desde == null || !o.getFecha().toLocalDate().isBefore(desde))
                .filter(o -> hasta == null || !o.getFecha().toLocalDate().isAfter(hasta))
                .map(this::map).toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','JEFE_INVENTARIO','OPERADOR_ALMACEN','VENDEDOR','ENCARGADO_COMPRAS')")
    public OperacionResponseDTO obtener(String tipo, Integer id, Integer sedeId) {
        Integer allowedSede = sedeAccessService.requireAuthorizedSede(sedeId);
        OperacionInventario operation = operacionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Operación no encontrada"));
        if (!normalizeType(tipo).equals(operation.getTipo()) || !allowedSede.equals(operation.getSede().getIdSede())) {
            throw new EntityNotFoundException("Operación no encontrada");
        }
        if (!canReadOperation(sedeAccessService.authenticatedUser(), operation))
            throw new EntityNotFoundException("Operación no encontrada");
        return map(operation);
    }

    private boolean canReadOperation(Usuario reader, OperacionInventario operation) {
        String role = reader.getRol() == null ? "" : reader.getRol().getNombre();
        if ("VENDEDOR".equals(role))
            return "EGRESO".equals(operation.getTipo()) && "VENTA".equals(operation.getSubtipo())
                    && operation.getUsuario() != null && Objects.equals(operation.getUsuario().getIdUsuario(), reader.getIdUsuario());
        if ("ENCARGADO_COMPRAS".equals(role))
            return "INGRESO".equals(operation.getTipo()) && "COMPRA".equals(operation.getSubtipo());
        return true;
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','OPERADOR_ALMACEN')")
    public OperacionResponseDTO editarIngreso(Integer id, OperacionRequestDTO request, String username) {
        Integer sedeId = sedeAccessService.requireAuthorizedSede(request.getIdSede());
        OperacionInventario operation = operacionRepository.findForUpdate(id)
                .orElseThrow(() -> new EntityNotFoundException("Ingreso no encontrado"));
        if (!"INGRESO".equals(operation.getTipo()) || !sedeId.equals(operation.getSede().getIdSede())) {
            throw new EntityNotFoundException("Ingreso no encontrado");
        }
        if ("COMPRA".equals(operation.getSubtipo())) throw new IllegalArgumentException("La recepción vinculada a una compra no se edita desde Ingresos");
        if (request.getSubtipo() != null && !Objects.equals(request.getSubtipo(), operation.getSubtipo()))
            throw new IllegalArgumentException("No se puede cambiar el tipo de un ingreso confirmado");
        request.setSubtipo(operation.getSubtipo());
        Proveedor supplier = resolveSupplierAndValidateContext(operation.getSubtipo(), request);
        List<DetalleOperacionInventario> currentDetails = detalleRepository.findForUpdateByOperacion(id);
        Map<Integer, DetalleOperacionInventario> oldDetails = currentDetails.stream()
                .collect(Collectors.toMap(d -> d.getProducto().getIdProducto(), Function.identity()));
        Map<Integer, Integer> oldLines = currentDetails.stream()
                .collect(Collectors.toMap(d -> d.getProducto().getIdProducto(), DetalleOperacionInventario::getCantidad));
        Map<Integer, Integer> newLines = normalizeLines(request);
        Usuario user = usuarioRepository.findByUsuario(username).orElseThrow(() -> new AccessDeniedException("Usuario no autorizado"));
        Sede sede = operation.getSede();
        Set<Integer> productIds = new TreeSet<>(); productIds.addAll(oldLines.keySet()); productIds.addAll(newLines.keySet());
        Map<Integer, Producto> products = loadActiveProducts(productIds);
        LocalDateTime now = LocalDateTime.now(ZoneId.of("America/Lima"));
        for (Integer productId : productIds) {
            int delta = newLines.getOrDefault(productId, 0) - oldLines.getOrDefault(productId, 0);
            if (delta == 0) continue;
            Inventario inventory = inventarioRepository.findForUpdate(sedeId, productId)
                    .orElseThrow(() -> new EntityNotFoundException("No existe inventario del producto en la sede"));
            int current = Optional.ofNullable(inventory.getStockActual()).orElse(0);
            int after = Math.addExact(current, delta);
            if (after < 0) throw new IllegalArgumentException("La corrección dejaría stock negativo para " + products.get(productId).getNombre());
            inventory.setStockActual(after);
            inventarioRepository.save(inventory);
            movimientoRepository.save(Movimiento.builder().sede(sede).producto(products.get(productId)).usuario(user)
                    .operacion(operation).tipo(delta > 0 ? "ENTRADA" : "SALIDA").subtipo(operation.getSubtipo()).cantidad(Math.abs(delta))
                    .stockAnterior(current).stockPosterior(after).fecha(now)
                    .referencia("CORRECCION ingreso " + operation.getReferencia()).build());
        }
        for (DetalleOperacionInventario detail : currentDetails) {
            Integer productId = detail.getProducto().getIdProducto();
            Integer updatedQuantity = newLines.get(productId);
            if (updatedQuantity == null) detalleRepository.delete(detail);
            else detail.setCantidad(updatedQuantity);
        }
        newLines.forEach((productId, quantity) -> {
            if (!oldDetails.containsKey(productId)) {
                detalleRepository.save(DetalleOperacionInventario.builder().operacion(operation)
                        .producto(products.get(productId)).cantidad(quantity).build());
            }
        });
        operation.setReferencia(request.getReferencia().trim());
        operation.setProveedor(supplier);
        operation.setBeneficiario(clean(request.getBeneficiario()));
        operation.setMotivo(clean(request.getMotivo()));
        operation.setObservaciones(clean(request.getObservaciones()));
        operacionRepository.save(operation);
        audit(user, "INGRESO_EDITADO", "Corrección auditable del ingreso " + operation.getReferencia(), now);
        return map(operation);
    }

    private OperacionResponseDTO crear(String tipo, OperacionRequestDTO request, String username, boolean purchaseReceipt) {
        if (request == null || request.getIdSede() == null || request.getReferencia() == null
                || request.getReferencia().isBlank() || request.getDetalles() == null || request.getDetalles().isEmpty()) {
            throw new IllegalArgumentException("Sede, referencia y al menos un detalle son obligatorios");
        }
        String subtype = normalizeSubtype(tipo, request.getSubtipo(), purchaseReceipt);
        Map<Integer, Integer> lines = normalizeLines(request);
        Usuario user = usuarioRepository.findByUsuario(username)
                .orElseThrow(() -> new AccessDeniedException("Usuario no autorizado"));
        validarPermisoPorSubtipo(user, tipo, subtype);
        boolean approvalRequired = requiresApproval(user, tipo, subtype);
        Integer sedeId = sedeAccessService.requireAuthorizedSede(request.getIdSede());
        String reference = request.getReferencia().trim();
        Proveedor supplier = resolveSupplierAndValidateContext(subtype, request);
        Optional<OperacionInventario> prior = operacionRepository.findByTipoAndReferencia(tipo, reference);
        if (prior.isPresent()) {
            OperacionInventario existing = prior.get();
            Map<Integer, Integer> persisted = detalleRepository.findByOperacion_IdOperacionOrderByIdDetalle(existing.getIdOperacion()).stream()
                    .collect(Collectors.toMap(d -> d.getProducto().getIdProducto(), DetalleOperacionInventario::getCantidad));
            if (existing.getSede().getIdSede().equals(sedeId) && persisted.equals(lines)
                    && Objects.equals(existing.getSubtipo(), subtype)
                    && Objects.equals(existing.getProveedor() == null ? null : existing.getProveedor().getIdProveedor(), request.getIdProveedor())
                    && Objects.equals(existing.getBeneficiario(), clean(request.getBeneficiario()))
                    && Objects.equals(existing.getMotivo(), clean(request.getMotivo()))
                    && Objects.equals(existing.getObservaciones(), clean(request.getObservaciones()))) return map(existing);
            throw new IllegalArgumentException("La referencia ya fue utilizada para otra operación");
        }
        Sede sede = sedeRepository.findById(sedeId).orElseThrow(() -> new EntityNotFoundException("Sede no encontrada"));
        Map<Integer, Producto> products = loadActiveProducts(new TreeSet<>(lines.keySet()));
        OperacionInventario operation = operacionRepository.save(OperacionInventario.builder().tipo(tipo).subtipo(subtype).estado(approvalRequired ? "PENDIENTE" : "CONFIRMADA").sede(sede).usuario(user)
                .proveedor(supplier).beneficiario(clean(request.getBeneficiario())).motivo(clean(request.getMotivo()))
                .observaciones(clean(request.getObservaciones())).fecha(LocalDateTime.now(ZoneId.of("America/Lima"))).referencia(reference).build());
        LocalDateTime now = operation.getFecha();
        if (approvalRequired) {
            saveDetails(operation, lines, products);
            audit(user, "EGRESO_SOLICITADO", "Solicitud " + reference + " con " + lines.size() + " productos", now);
            return map(operation);
        }
        for (Integer productId : new TreeSet<>(lines.keySet())) {
            Inventario inventory = inventarioRepository.findForUpdate(sedeId, productId)
                    .orElseThrow(() -> new EntityNotFoundException("No existe inventario del producto en la sede"));
            int qty = lines.get(productId);
            int current = Optional.ofNullable(inventory.getStockActual()).orElse(0);
            if ("EGRESO".equals(tipo) && current < qty) throw new IllegalArgumentException("Stock insuficiente para " + products.get(productId).getNombre());
            int after = "INGRESO".equals(tipo) ? Math.addExact(current, qty) : current - qty;
            inventory.setStockActual(after);
            inventarioRepository.save(inventory);
            movimientoRepository.save(Movimiento.builder().sede(sede).producto(products.get(productId)).usuario(user)
                    .operacion(operation).tipo("INGRESO".equals(tipo) ? "ENTRADA" : "SALIDA").subtipo(subtype).cantidad(qty)
                    .stockAnterior(current).stockPosterior(after).fecha(now).referencia(reference).build());
        }
        saveDetails(operation, lines, products);
        audit(user, tipo + "_REGISTRADO", tipo + " " + reference + " con " + lines.size() + " productos", now);
        return map(operation);
    }

    private void validarPermisoPorSubtipo(Usuario user, String type, String subtype) {
        String role = user.getRol() == null ? "" : user.getRol().getNombre();
        if ("VENDEDOR".equals(role) && !("EGRESO".equals(type) && "VENTA".equals(subtype)))
            throw new AccessDeniedException("El vendedor solo puede registrar ventas");
        if ("OPERADOR_ALMACEN".equals(role) && "EGRESO".equals(type)) {
            if ("VENTA".equals(subtype)) throw new AccessDeniedException("Operator cannot register sales");
            if (!List.of("DEVOLUCION_PROVEEDOR", "MERMA", "DESMEDRO", "DONACION", "CONSUMO_INTERNO").contains(subtype))
                throw new AccessDeniedException("Expense type is not allowed for warehouse operators");
        }
        if ("ENCARGADO_COMPRAS".equals(role) && !("INGRESO".equals(type) && "COMPRA".equals(subtype)))
            throw new AccessDeniedException("Compras solo puede consultar y gestionar compras pendientes; no registra movimientos directos");
    }

    private boolean requiresApproval(Usuario user, String type, String subtype) {
        return "EGRESO".equals(type) && "OPERADOR_ALMACEN".equals(user.getRol() == null ? "" : user.getRol().getNombre())
                && List.of("DEVOLUCION_PROVEEDOR", "MERMA", "DESMEDRO", "DONACION", "CONSUMO_INTERNO").contains(subtype);
    }

    private Map<Integer, Integer> normalizeLines(OperacionRequestDTO request) {
        Map<Integer, Integer> lines = new LinkedHashMap<>();
        request.getDetalles().forEach(line -> {
            if (line == null || line.getIdProducto() == null || line.getIdProducto() <= 0 || line.getCantidad() == null || line.getCantidad() <= 0)
                throw new IllegalArgumentException("Cada detalle debe tener producto y cantidad positiva");
            if (lines.putIfAbsent(line.getIdProducto(), line.getCantidad()) != null)
                throw new IllegalArgumentException("No se puede repetir un producto en la misma operación");
        });
        return lines;
    }

    private Map<Integer, Producto> loadActiveProducts(Collection<Integer> ids) {
        Map<Integer, Producto> products = productoRepository.findAllById(ids).stream().collect(Collectors.toMap(Producto::getIdProducto, Function.identity()));
        for (Integer id : ids) {
            Producto product = products.get(id);
            if (product == null) throw new EntityNotFoundException("Producto no encontrado: " + id);
            if (!Boolean.TRUE.equals(product.getEstado())) throw new IllegalArgumentException("El producto " + product.getCodigo() + " está inactivo");
        }
        return products;
    }

    private void saveDetails(OperacionInventario operation, Map<Integer, Integer> lines, Map<Integer, Producto> products) {
        lines.forEach((productId, qty) -> detalleRepository.save(DetalleOperacionInventario.builder().operacion(operation).producto(products.get(productId)).cantidad(qty).build()));
    }

    private void audit(Usuario user, String action, String detail, LocalDateTime at) {
        auditoriaRepository.save(Auditoria.builder().usuario(user).accion(action).detalle(detail).fecha(at).ip(ClientAddress.currentRemoteAddress()).build());
    }

    private String normalizeType(String type) {
        String normalized = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        if (!List.of("INGRESO", "EGRESO").contains(normalized)) throw new IllegalArgumentException("Tipo de operación inválido");
        return normalized;
    }

    private String normalizeSubtype(String type, String requested, boolean purchaseReceipt) {
        if ("INGRESO".equals(type)) {
            if (purchaseReceipt) {
                if (!"COMPRA".equals(requested)) throw new IllegalArgumentException("Receipt must originate from a pending purchase");
                return "COMPRA";
            }
            if (!List.of("DEVOLUCION_CLIENTE", "PRODUCCION", "BONIFICACION_PROVEEDOR").contains(requested))
                throw new IllegalArgumentException("Select a valid manual income type; purchases and transfers use their own modules");
            return requested;
        }
        if (!List.of("VENTA", "DEVOLUCION_PROVEEDOR", "MERMA", "DESMEDRO", "DONACION", "CONSUMO_INTERNO").contains(requested))
            throw new IllegalArgumentException("Select a valid manual expense type; transfers use their own module");
        return requested;
    }

    private Proveedor resolveSupplierAndValidateContext(String subtype, OperacionRequestDTO request) {
        boolean supplierRequired = "BONIFICACION_PROVEEDOR".equals(subtype) || "DEVOLUCION_PROVEEDOR".equals(subtype) || "COMPRA".equals(subtype);
        if (supplierRequired && request.getIdProveedor() == null) throw new IllegalArgumentException("Supplier is required for this operation");
        if (!supplierRequired && request.getIdProveedor() != null) throw new IllegalArgumentException("This operation type does not accept a supplier");
        Proveedor supplier = null;
        if (supplierRequired) {
            supplier = proveedorRepository.findById(request.getIdProveedor())
                    .orElseThrow(() -> new EntityNotFoundException("Supplier not found"));
            if (!Boolean.TRUE.equals(supplier.getEstado())) throw new IllegalArgumentException("Supplier is inactive");
        }
        boolean beneficiaryRequired = "DONACION".equals(subtype);
        if (beneficiaryRequired && (request.getBeneficiario() == null || request.getBeneficiario().isBlank()))
            throw new IllegalArgumentException("Donation beneficiary is required");
        if (!beneficiaryRequired && request.getBeneficiario() != null && !request.getBeneficiario().isBlank())
            throw new IllegalArgumentException("This operation type does not accept a beneficiary");
        boolean reasonRequired = List.of("DEVOLUCION_CLIENTE", "PRODUCCION", "BONIFICACION_PROVEEDOR",
                "DEVOLUCION_PROVEEDOR", "MERMA", "DESMEDRO", "DONACION", "CONSUMO_INTERNO").contains(subtype);
        if (reasonRequired && (request.getMotivo() == null || request.getMotivo().isBlank()))
            throw new IllegalArgumentException("Reason is required for this operation");
        return supplier;
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
    private OperacionResponseDTO map(OperacionInventario operation) {
        return OperacionResponseDTO.builder().idOperacion(operation.getIdOperacion()).tipo(operation.getTipo()).subtipo(operation.getSubtipo())
                .estado(operation.getEstado()).aprobadoPor(operation.getUsuarioAprueba() == null ? null : operation.getUsuarioAprueba().getUsuario())
                .fechaRevision(operation.getFechaRevision()).motivoRevision(operation.getMotivoRevision())
                .idSede(operation.getSede().getIdSede()).nombreSede(operation.getSede().getNombre())
                .usuario(operation.getUsuario().getUsuario()).fecha(operation.getFecha()).referencia(operation.getReferencia())
                .idProveedor(operation.getProveedor() == null ? null : operation.getProveedor().getIdProveedor())
                .nombreProveedor(operation.getProveedor() == null ? null : operation.getProveedor().getNombre())
                .beneficiario(operation.getBeneficiario()).motivo(operation.getMotivo()).observaciones(operation.getObservaciones())
                .detalles(detalleRepository.findByOperacion_IdOperacionOrderByIdDetalle(operation.getIdOperacion()).stream()
                        .map(d -> DetalleOperacionResponseDTO.builder().idProducto(d.getProducto().getIdProducto()).codigoProducto(d.getProducto().getCodigo())
                                .nombreProducto(d.getProducto().getNombre()).unidadMedida(d.getProducto().getUnidadMedida())
                                .cantidad(d.getCantidad()).build()).toList()).build();
    }
}

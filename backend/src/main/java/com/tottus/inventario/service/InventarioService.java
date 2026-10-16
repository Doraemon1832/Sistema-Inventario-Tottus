package com.tottus.inventario.service;

import com.tottus.inventario.dto.InventarioResponseDTO;
import com.tottus.inventario.dto.MovimientoRequestDTO;
import com.tottus.inventario.dto.MovimientoResponseDTO;
import com.tottus.inventario.dto.ResumenInventarioDTO;
import com.tottus.inventario.model.*;
import com.tottus.inventario.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
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

    @Transactional(readOnly = true)
    public List<InventarioResponseDTO> getInventario(Integer sedeId, Integer categoriaId, String query, String stockStatus) {
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
    public ResumenInventarioDTO getResumen(Integer sedeId) {
        long totalProductos = inventarioRepository.searchInventario(sedeId, null, null).size();
        long stockBajo = inventarioRepository.countStockBajo(sedeId);
        long sinStock = inventarioRepository.countAgotados(sedeId);
        long totalUnidades = inventarioRepository.sumTotalUnidades(sedeId);
        long movimientosHoy = movimientoRepository.countMovimientosDesde(sedeId, LocalDate.now().atStartOfDay());

        return ResumenInventarioDTO.builder()
                .totalProductos(totalProductos)
                .stockBajo(stockBajo)
                .sinStock(sinStock)
                .totalUnidades(totalUnidades)
                .movimientosHoy(movimientosHoy)
                .build();
    }

    @Transactional
    public MovimientoResponseDTO registrarMovimiento(MovimientoRequestDTO request, String username) {
        Sede sede = sedeRepository.findById(request.getIdSede())
                .orElseThrow(() -> new RuntimeException("Sede no encontrada con ID: " + request.getIdSede()));

        Producto producto = productoRepository.findById(request.getIdProducto())
                .orElseThrow(() -> new RuntimeException("Producto no encontrado con ID: " + request.getIdProducto()));

        if (Boolean.FALSE.equals(producto.getEstado())) {
            throw new IllegalArgumentException("No se pueden registrar operaciones: El producto '" + producto.getNombre() + "' (" + producto.getCodigo() + ") se encuentra INACTIVO.");
        }

        Usuario usuario = usuarioRepository.findByUsuario(username)
                .orElse(null);

        Inventario inventario = inventarioRepository.findBySede_IdSedeAndProducto_IdProducto(request.getIdSede(), request.getIdProducto())
                .orElseGet(() -> {
                    Inventario nuevo = Inventario.builder()
                            .sede(sede)
                            .producto(producto)
                            .stockActual(0)
                            .ubicacion("Almacén General")
                            .build();
                    return inventarioRepository.save(nuevo);
                });

        int stockActual = inventario.getStockActual() != null ? inventario.getStockActual() : 0;
        int cantidad = request.getCantidad() != null ? request.getCantidad() : 0;
        String tipo = request.getTipo() != null ? request.getTipo().toUpperCase() : "ENTRADA";

        switch (tipo) {
            case "ENTRADA":
                inventario.setStockActual(stockActual + cantidad);
                break;
            case "SALIDA":
                if (stockActual < cantidad) {
                    throw new IllegalArgumentException("Stock insuficiente. Stock actual: " + stockActual + ", Cantidad requerida: " + cantidad);
                }
                inventario.setStockActual(stockActual - cantidad);
                break;
            case "AJUSTE":
                // Para ajuste, cantidad representa el nuevo stock o la variación
                inventario.setStockActual(cantidad);
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
                .cantidad(cantidad)
                .fecha(LocalDateTime.now())
                .referencia(request.getReferencia())
                .build();

        Movimiento savedMov = movimientoRepository.save(mov);

        // Guardar auditoria
        Auditoria audit = Auditoria.builder()
                .usuario(usuario)
                .accion("MOVIMIENTO_INVENTARIO_" + tipo)
                .fecha(LocalDateTime.now())
                .ip("127.0.0.1")
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
                .cantidad(savedMov.getCantidad())
                .fecha(savedMov.getFecha())
                .referencia(savedMov.getReferencia())
                .usuario(usuario != null ? usuario.getUsuario() : "Sistema")
                .build();
    }

    @Transactional(readOnly = true)
    public List<MovimientoResponseDTO> getMovimientos(Integer sedeId, String tipo, Integer productoId) {
        String filterTipo = (tipo != null && !tipo.trim().isEmpty() && !"TODOS".equalsIgnoreCase(tipo)) ? tipo.trim() : null;
        List<Movimiento> list = movimientoRepository.findWithFilters(sedeId, filterTipo, productoId);

        return list.stream().map(m -> MovimientoResponseDTO.builder()
                .idMovimiento(m.getIdMovimiento())
                .idSede(m.getSede() != null ? m.getSede().getIdSede() : null)
                .nombreSede(m.getSede() != null ? m.getSede().getNombre() : "")
                .idProducto(m.getProducto() != null ? m.getProducto().getIdProducto() : null)
                .codigoProducto(m.getProducto() != null ? m.getProducto().getCodigo() : "")
                .nombreProducto(m.getProducto() != null ? m.getProducto().getNombre() : "")
                .tipo(m.getTipo())
                .cantidad(m.getCantidad())
                .fecha(m.getFecha())
                .referencia(m.getReferencia())
                .usuario(m.getUsuario() != null ? m.getUsuario().getUsuario() : "Sistema")
                .build())
                .collect(Collectors.toList());
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

package com.tottus.inventario.service;

import com.tottus.inventario.dto.ProductoRequestDTO;
import com.tottus.inventario.model.*;
import com.tottus.inventario.repository.*;
import com.tottus.inventario.security.ClientAddress;
import com.tottus.inventario.security.SedeAccessService;
import org.springframework.security.access.prepost.PreAuthorize;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import jakarta.persistence.EntityNotFoundException;

@Service
@RequiredArgsConstructor
public class ProductoService {

    private final ProductoRepository productoRepository;
    private final CategoriaRepository categoriaRepository;
    private final SedeRepository sedeRepository;
    private final InventarioRepository inventarioRepository;
    private final MovimientoRepository movimientoRepository;
    private final UsuarioRepository usuarioRepository;
    private final AuditoriaRepository auditoriaRepository;
    private final SedeAccessService sedeAccessService;

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','OPERADOR_ALMACEN','ENCARGADO_COMPRAS')")
    public List<Producto> listarProductos(Integer categoriaId, String query) {
        return productoRepository.searchProductos(categoriaId, query);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','OPERADOR_ALMACEN')")
    public List<Categoria> listarCategorias() {
        return categoriaRepository.findByEstadoTrue();
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','OPERADOR_ALMACEN')")
    public Producto guardarProducto(ProductoRequestDTO dto, String username) {
        if (dto == null || dto.getCodigo() == null || dto.getCodigo().isBlank()
                || dto.getNombre() == null || dto.getNombre().isBlank()
                || dto.getPrecio() == null || dto.getPrecio().signum() <= 0
                || (dto.getStockMinimo() != null && dto.getStockMinimo() < 0)
                || (dto.getStockInicial() != null && dto.getStockInicial() < 0)) {
            throw new IllegalArgumentException("Los datos del producto no son válidos");
        }
        sedeAccessService.requireAuthorizedSede(dto.getIdSede());
        String codigo = dto.getCodigo().trim();
        assertCodigoDisponible(codigo, null);
        Categoria categoria = null;
        if (dto.getIdCategoria() != null) {
            categoria = categoriaRepository.findById(dto.getIdCategoria())
                    .orElseThrow(() -> new EntityNotFoundException("Categoría no encontrada"));
        }

        Producto producto = Producto.builder()
                .codigo(codigo)
                .nombre(dto.getNombre().trim())
                .unidadMedida(dto.getUnidadMedida() == null || dto.getUnidadMedida().isBlank() ? null : dto.getUnidadMedida().trim())
                .categoria(categoria)
                .precio(dto.getPrecio())
                .stockMinimo(dto.getStockMinimo() != null ? dto.getStockMinimo() : 5)
                .build();

        Producto saved = productoRepository.save(producto);

        // Si se especificó sede y stock inicial, crear registro de inventario
        if (dto.getIdSede() != null) {
            Sede sede = sedeRepository.findById(dto.getIdSede())
                    .orElseThrow(() -> new EntityNotFoundException("Sede no encontrada"));
            int stockInicial = dto.getStockInicial() != null ? dto.getStockInicial() : 0;
            Inventario inv = Inventario.builder()
                    .sede(sede)
                    .producto(saved)
                    .stockActual(stockInicial)
                    .ubicacion(dto.getUbicacion() != null ? dto.getUbicacion() : "Anaquel General")
                    .build();
            inventarioRepository.save(inv);

            if (stockInicial > 0) {
                Usuario usuario = usuarioRepository.findByUsuario(username).orElse(null);
                Movimiento mov = Movimiento.builder()
                        .sede(sede)
                        .producto(saved)
                        .usuario(usuario)
                        .tipo("ENTRADA")
                        .cantidad(stockInicial)
                        .stockAnterior(0)
                        .stockPosterior(stockInicial)
                        .fecha(LocalDateTime.now(ZoneId.of("America/Lima")))
                        .referencia("Inventario inicial de alta de producto")
                        .build();
                movimientoRepository.save(mov);
            }
        }

        return saved;
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','OPERADOR_ALMACEN')")
    public Producto actualizarProducto(Integer id, ProductoRequestDTO dto, String username) {
        sedeAccessService.requireAuthorizedSede(dto != null ? dto.getIdSede() : null);
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Producto no encontrado"));

        if (dto.getCodigo() != null && !dto.getCodigo().trim().isEmpty()) {
            String codigo = dto.getCodigo().trim();
            assertCodigoDisponible(codigo, id);
            producto.setCodigo(codigo);
        }
        if (dto.getNombre() != null && !dto.getNombre().trim().isEmpty()) {
            producto.setNombre(dto.getNombre().trim());
        }
        if (dto.getUnidadMedida() != null) {
            producto.setUnidadMedida(dto.getUnidadMedida().isBlank() ? null : dto.getUnidadMedida().trim());
        }
        if (dto.getPrecio() != null) {
            producto.setPrecio(dto.getPrecio());
        }
        if (dto.getStockMinimo() != null) {
            producto.setStockMinimo(dto.getStockMinimo());
        }
        if (dto.getIdCategoria() != null) {
            Categoria cat = categoriaRepository.findById(dto.getIdCategoria())
                    .orElseThrow(() -> new EntityNotFoundException("Categoría no encontrada"));
            producto.setCategoria(cat);
        }

        Producto updated = productoRepository.save(producto);

        // Actualizar ubicación en sede si se indicó
        if (dto.getIdSede() != null && dto.getUbicacion() != null && !dto.getUbicacion().trim().isEmpty()) {
            inventarioRepository.findBySede_IdSedeAndProducto_IdProducto(dto.getIdSede(), id).ifPresent(inv -> {
                inv.setUbicacion(dto.getUbicacion().trim());
                inventarioRepository.save(inv);
            });
        }

        Usuario usuario = usuarioRepository.findByUsuario(username).orElse(null);
        Auditoria audit = Auditoria.builder()
                .usuario(usuario)
                .accion("EDICION_PRODUCTO")
                .fecha(LocalDateTime.now(ZoneId.of("America/Lima")))
                .ip(ClientAddress.currentRemoteAddress())
                .detalle("Actualización de datos para el producto: " + updated.getNombre() + " (" + updated.getCodigo() + ")")
                .build();
        auditoriaRepository.save(audit);

        return updated;
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','OPERADOR_ALMACEN')")
    public Producto cambiarEstadoProducto(Integer id, boolean nuevoEstado, String username) {
        sedeAccessService.requireAuthorizedSede(null);
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Producto no encontrado"));

        producto.setEstado(nuevoEstado);
        Producto saved = productoRepository.save(producto);

        Usuario usuario = usuarioRepository.findByUsuario(username).orElse(null);
        String accion = nuevoEstado ? "ACTIVACION_PRODUCTO" : "INACTIVACION_PRODUCTO";
        String detalle = (nuevoEstado ? "Reactivación" : "Inactivación") + " del producto: " + saved.getNombre() + " (" + saved.getCodigo() + ")";

        Auditoria audit = Auditoria.builder()
                .usuario(usuario)
                .accion(accion)
                .fecha(LocalDateTime.now(ZoneId.of("America/Lima")))
                .ip(ClientAddress.currentRemoteAddress())
                .detalle(detalle)
                .build();
        auditoriaRepository.save(audit);

        return saved;
    }

    private void assertCodigoDisponible(String codigo, Integer currentProductId) {
        productoRepository.findByCodigo(codigo)
                .filter(existing -> !existing.getIdProducto().equals(currentProductId))
                .ifPresent(existing -> { throw new IllegalArgumentException("Ya existe un producto con ese código"); });
    }
}


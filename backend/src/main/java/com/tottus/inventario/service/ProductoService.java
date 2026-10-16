package com.tottus.inventario.service;

import com.tottus.inventario.dto.ProductoRequestDTO;
import com.tottus.inventario.model.*;
import com.tottus.inventario.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

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

    @Transactional(readOnly = true)
    public List<Producto> listarProductos(Integer categoriaId, String query) {
        return productoRepository.searchProductos(categoriaId, query);
    }

    @Transactional(readOnly = true)
    public List<Categoria> listarCategorias() {
        return categoriaRepository.findByEstadoTrue();
    }

    @Transactional
    public Producto guardarProducto(ProductoRequestDTO dto, String username) {
        Categoria categoria = null;
        if (dto.getIdCategoria() != null) {
            categoria = categoriaRepository.findById(dto.getIdCategoria())
                    .orElse(null);
        }

        Producto producto = Producto.builder()
                .codigo(dto.getCodigo())
                .nombre(dto.getNombre())
                .categoria(categoria)
                .precio(dto.getPrecio())
                .stockMinimo(dto.getStockMinimo() != null ? dto.getStockMinimo() : 5)
                .build();

        Producto saved = productoRepository.save(producto);

        // Si se especificó sede y stock inicial, crear registro de inventario
        if (dto.getIdSede() != null) {
            Sede sede = sedeRepository.findById(dto.getIdSede()).orElse(null);
            if (sede != null) {
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
                            .fecha(LocalDateTime.now())
                            .referencia("Inventario inicial de alta de producto")
                            .build();
                    movimientoRepository.save(mov);
                }
            }
        }

        return saved;
    }

    @Transactional
    public Producto actualizarProducto(Integer id, ProductoRequestDTO dto, String username) {
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado con ID: " + id));

        if (dto.getCodigo() != null && !dto.getCodigo().trim().isEmpty()) {
            producto.setCodigo(dto.getCodigo().trim());
        }
        if (dto.getNombre() != null && !dto.getNombre().trim().isEmpty()) {
            producto.setNombre(dto.getNombre().trim());
        }
        if (dto.getPrecio() != null) {
            producto.setPrecio(dto.getPrecio());
        }
        if (dto.getStockMinimo() != null) {
            producto.setStockMinimo(dto.getStockMinimo());
        }
        if (dto.getIdCategoria() != null) {
            Categoria cat = categoriaRepository.findById(dto.getIdCategoria()).orElse(null);
            if (cat != null) {
                producto.setCategoria(cat);
            }
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
                .fecha(LocalDateTime.now())
                .ip("127.0.0.1")
                .detalle("Actualización de datos para el producto: " + updated.getNombre() + " (" + updated.getCodigo() + ")")
                .build();
        auditoriaRepository.save(audit);

        return updated;
    }

    @Transactional
    public Producto cambiarEstadoProducto(Integer id, boolean nuevoEstado, String username) {
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado con ID: " + id));

        producto.setEstado(nuevoEstado);
        Producto saved = productoRepository.save(producto);

        Usuario usuario = usuarioRepository.findByUsuario(username).orElse(null);
        String accion = nuevoEstado ? "ACTIVACION_PRODUCTO" : "INACTIVACION_PRODUCTO";
        String detalle = (nuevoEstado ? "Reactivación" : "Inactivación") + " del producto: " + saved.getNombre() + " (" + saved.getCodigo() + ")";

        Auditoria audit = Auditoria.builder()
                .usuario(usuario)
                .accion(accion)
                .fecha(LocalDateTime.now())
                .ip("127.0.0.1")
                .detalle(detalle)
                .build();
        auditoriaRepository.save(audit);

        return saved;
    }
}


package com.tottus.inventario.config;

import com.tottus.inventario.model.*;
import com.tottus.inventario.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final RolRepository rolRepository;
    private final SedeRepository sedeRepository;
    private final UsuarioRepository usuarioRepository;
    private final CategoriaRepository categoriaRepository;
    private final ProductoRepository productoRepository;
    private final InventarioRepository inventarioRepository;
    private final MovimientoRepository movimientoRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("Verificando e inicializando datos para inv_tottus_db...");

        // 1. Roles
        Rol rolAdmin = getOrCreateRol("ADMINISTRADOR");
        Rol rolJefe = getOrCreateRol("JEFE_INVENTARIO");
        Rol rolOperador = getOrCreateRol("OPERADOR_ALMACEN");

        // 2. Sedes
        Sede sedeMegaPlaza = getOrCreateSede("Tottus MegaPlaza", "Av. Alfredo Mendiola 3698, Independencia, Lima");
        Sede sedeJockey = getOrCreateSede("Tottus Jockey Plaza", "Av. Javier Prado Este 4200, Surco, Lima");
        Sede sedeSanMiguel = getOrCreateSede("Tottus San Miguel", "Av. La Marina 2000, San Miguel, Lima");
        Sede sedeMiraflores = getOrCreateSede("Tottus Miraflores", "Av. 28 de Julio 1005, Miraflores, Lima");

        // 3. Usuarios
        Usuario adminUser = getOrCreateUsuario("gerencia_admin", "admin123", rolAdmin, sedeMegaPlaza);
        getOrCreateUsuario("jefe_almacen", "tottus2026", rolJefe, sedeMegaPlaza);

        // 4. Categorías
        Categoria catAbarrotes = getOrCreateCategoria("Abarrotes y Despensa");
        Categoria catLacteos = getOrCreateCategoria("Lácteos y Huevos");
        Categoria catBebidas = getOrCreateCategoria("Bebidas y Licores");
        Categoria catCarnes = getOrCreateCategoria("Carnes, Aves y Pescados");
        Categoria catFrutas = getOrCreateCategoria("Frutas y Verduras");
        Categoria catLimpieza = getOrCreateCategoria("Limpieza y Cuidado del Hogar");

        // 5. Productos
        if (productoRepository.count() == 0) {
            log.info("Sembrando catálogo de productos Tottus...");
            List<ProductoData> productosSeed = List.of(
                    new ProductoData("TOT-ABA-001", "Arroz Extra Añejo Tottus 5kg", catAbarrotes, new BigDecimal("21.50"), 15, 65, "Pasillo 03 - Góndola A"),
                    new ProductoData("TOT-ABA-002", "Aceite Vegetal Primor Premium 900ml", catAbarrotes, new BigDecimal("9.80"), 20, 80, "Pasillo 03 - Góndola B"),
                    new ProductoData("TOT-ABA-003", "Azúcar Rubia Tottus Selección 5kg", catAbarrotes, new BigDecimal("18.90"), 10, 35, "Pasillo 03 - Góndola C"),
                    new ProductoData("TOT-LAC-001", "Leche Evaporada Gloria Azul Pack x6", catLacteos, new BigDecimal("24.90"), 15, 45, "Pasillo 05 - Refrigerados 1"),
                    new ProductoData("TOT-LAC-002", "Yogurt Gloria Fresa Parcialmente Descremado 1kg", catLacteos, new BigDecimal("6.70"), 12, 10, "Pasillo 05 - Lácteos 2"), // Bajo stock
                    new ProductoData("TOT-BEB-001", "Gaseosa Coca Cola Sabor Original 3 Litros", catBebidas, new BigDecimal("13.50"), 25, 120, "Pasillo 07 - Bebidas A"),
                    new ProductoData("TOT-BEB-002", "Agua Mineral San Mateo sin gas 2.5L", catBebidas, new BigDecimal("4.20"), 30, 95, "Pasillo 07 - Bebidas B"),
                    new ProductoData("TOT-CAR-001", "Pechuga de Pollo San Fernando Fresca x kg", catCarnes, new BigDecimal("16.90"), 20, 30, "Pasillo 09 - Carnicería"),
                    new ProductoData("TOT-FRU-001", "Plátano de Seda Selección Tottus x kg", catFrutas, new BigDecimal("3.49"), 25, 50, "Isla 01 - Frutería"),
                    new ProductoData("TOT-FRU-002", "Manzana Royal Gala Importada x kg", catFrutas, new BigDecimal("7.80"), 15, 0, "Isla 02 - Frutería"), // Agotado
                    new ProductoData("TOT-LIM-001", "Detergente Líquido Ariel Doble Poder 3L", catLimpieza, new BigDecimal("36.90"), 10, 6, "Pasillo 11 - Aseo 1"), // Bajo stock
                    new ProductoData("TOT-LIM-002", "Lavavajilla Ayudín Limón Crema 850g", catLimpieza, new BigDecimal("8.20"), 12, 40, "Pasillo 11 - Aseo 2")
            );

            for (ProductoData pd : productosSeed) {
                Producto prod = Producto.builder()
                        .codigo(pd.codigo)
                        .nombre(pd.nombre)
                        .categoria(pd.categoria)
                        .precio(pd.precio)
                        .stockMinimo(pd.stockMinimo)
                        .estado(true)
                        .build();
                Producto savedProd = productoRepository.save(prod);

                // Inventario para Sede Principal (MegaPlaza)
                Inventario invMega = Inventario.builder()
                        .sede(sedeMegaPlaza)
                        .producto(savedProd)
                        .stockActual(pd.stockInicial)
                        .ubicacion(pd.ubicacion)
                        .build();
                inventarioRepository.save(invMega);

                if (pd.stockInicial > 0) {
                    Movimiento mov = Movimiento.builder()
                            .sede(sedeMegaPlaza)
                            .producto(savedProd)
                            .usuario(adminUser)
                            .tipo("ENTRADA")
                            .cantidad(pd.stockInicial)
                            .fecha(LocalDateTime.now().minusHours(2))
                            .referencia("Carga inicial de inventario")
                            .build();
                    movimientoRepository.save(mov);
                }

                // Inventario para Sede Jockey Plaza (stock alternativo)
                Inventario invJockey = Inventario.builder()
                        .sede(sedeJockey)
                        .producto(savedProd)
                        .stockActual((int) (pd.stockInicial * 0.8))
                        .ubicacion(pd.ubicacion)
                        .build();
                inventarioRepository.save(invJockey);
            }
            log.info("Catálogo e inventario de prueba cargados con éxito.");
        }

        log.info("Inicialización de datos completada.");
    }

    private Rol getOrCreateRol(String nombre) {
        return rolRepository.findByNombre(nombre).orElseGet(() -> {
            Rol r = Rol.builder().nombre(nombre).estado(true).build();
            return rolRepository.save(r);
        });
    }

    private Sede getOrCreateSede(String nombre, String direccion) {
        return sedeRepository.findByNombre(nombre).orElseGet(() -> {
            Sede s = Sede.builder().nombre(nombre).direccion(direccion).estado(true).build();
            return sedeRepository.save(s);
        });
    }

    private Usuario getOrCreateUsuario(String username, String rawPassword, Rol rol, Sede sede) {
        return usuarioRepository.findByUsuario(username).orElseGet(() -> {
            Usuario u = Usuario.builder()
                    .usuario(username)
                    .passwordHash(passwordEncoder.encode(rawPassword))
                    .rol(rol)
                    .sede(sede)
                    .estado(true)
                    .build();
            return usuarioRepository.save(u);
        });
    }

    private Categoria getOrCreateCategoria(String nombre) {
        return categoriaRepository.findByNombre(nombre).orElseGet(() -> {
            Categoria c = Categoria.builder().nombre(nombre).estado(true).build();
            return categoriaRepository.save(c);
        });
    }

    private static class ProductoData {
        String codigo;
        String nombre;
        Categoria categoria;
        BigDecimal precio;
        int stockMinimo;
        int stockInicial;
        String ubicacion;

        ProductoData(String codigo, String nombre, Categoria categoria, BigDecimal precio, int stockMinimo, int stockInicial, String ubicacion) {
            this.codigo = codigo;
            this.nombre = nombre;
            this.categoria = categoria;
            this.precio = precio;
            this.stockMinimo = stockMinimo;
            this.stockInicial = stockInicial;
            this.ubicacion = ubicacion;
        }
    }
}

package com.tottus.inventario.repository;

import com.tottus.inventario.model.Producto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductoRepository extends JpaRepository<Producto, Integer> {
    Optional<Producto> findByCodigo(String codigo);
    boolean existsByCodigo(String codigo);

    @Query("SELECT p FROM Producto p WHERE " +
           "(:categoriaId IS NULL OR p.categoria.idCategoria = :categoriaId) AND " +
           "(:query IS NULL OR LOWER(p.nombre) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(p.codigo) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<Producto> searchProductos(@Param("categoriaId") Integer categoriaId, @Param("query") String query);
}

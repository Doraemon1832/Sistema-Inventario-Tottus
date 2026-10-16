package com.tottus.inventario.repository;

import com.tottus.inventario.model.Inventario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventarioRepository extends JpaRepository<Inventario, Integer> {

    Optional<Inventario> findBySede_IdSedeAndProducto_IdProducto(Integer idSede, Integer idProducto);

    List<Inventario> findBySede_IdSede(Integer idSede);

    @Query("SELECT i FROM Inventario i WHERE " +
           "(:sedeId IS NULL OR i.sede.idSede = :sedeId) AND " +
           "(:categoriaId IS NULL OR i.producto.categoria.idCategoria = :categoriaId) AND " +
           "(:query IS NULL OR LOWER(i.producto.nombre) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(i.producto.codigo) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<Inventario> searchInventario(@Param("sedeId") Integer sedeId,
                                     @Param("categoriaId") Integer categoriaId,
                                     @Param("query") String query);

    @Query("SELECT COUNT(i) FROM Inventario i WHERE (:sedeId IS NULL OR i.sede.idSede = :sedeId) AND i.stockActual <= i.producto.stockMinimo AND i.stockActual > 0")
    long countStockBajo(@Param("sedeId") Integer sedeId);

    @Query("SELECT COUNT(i) FROM Inventario i WHERE (:sedeId IS NULL OR i.sede.idSede = :sedeId) AND i.stockActual <= 0")
    long countAgotados(@Param("sedeId") Integer sedeId);

    @Query("SELECT COALESCE(SUM(i.stockActual), 0) FROM Inventario i WHERE (:sedeId IS NULL OR i.sede.idSede = :sedeId)")
    long sumTotalUnidades(@Param("sedeId") Integer sedeId);
}

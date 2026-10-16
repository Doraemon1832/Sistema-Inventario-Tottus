package com.tottus.inventario.repository;

import com.tottus.inventario.model.Movimiento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MovimientoRepository extends JpaRepository<Movimiento, Integer> {

    @Query("SELECT m FROM Movimiento m WHERE " +
           "(:sedeId IS NULL OR m.sede.idSede = :sedeId) AND " +
           "(:tipo IS NULL OR m.tipo = :tipo) AND " +
           "(:productoId IS NULL OR m.producto.idProducto = :productoId) " +
           "ORDER BY m.fecha DESC")
    List<Movimiento> findWithFilters(@Param("sedeId") Integer sedeId,
                                    @Param("tipo") String tipo,
                                    @Param("productoId") Integer productoId);

    @Query("SELECT COUNT(m) FROM Movimiento m WHERE (:sedeId IS NULL OR m.sede.idSede = :sedeId) AND m.fecha >= :desde")
    long countMovimientosDesde(@Param("sedeId") Integer sedeId, @Param("desde") LocalDateTime desde);
}

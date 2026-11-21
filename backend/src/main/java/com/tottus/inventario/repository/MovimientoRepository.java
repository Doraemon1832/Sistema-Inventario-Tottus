package com.tottus.inventario.repository;

import com.tottus.inventario.model.Movimiento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    @Query(value = "SELECT m FROM Movimiento m WHERE m.sede.idSede = :sedeId " +
            "AND (:tipo IS NULL OR m.tipo = :tipo) " +
            "AND (:subtipo IS NULL OR m.subtipo = :subtipo) " +
            "AND (:productoId IS NULL OR m.producto.idProducto = :productoId) " +
            "AND (:desde IS NULL OR m.fecha >= :desde) " +
            "AND (:hasta IS NULL OR m.fecha < :hasta)",
            countQuery = "SELECT COUNT(m) FROM Movimiento m WHERE m.sede.idSede = :sedeId " +
                    "AND (:tipo IS NULL OR m.tipo = :tipo) " +
                    "AND (:subtipo IS NULL OR m.subtipo = :subtipo) " +
                    "AND (:productoId IS NULL OR m.producto.idProducto = :productoId) " +
                    "AND (:desde IS NULL OR m.fecha >= :desde) " +
                    "AND (:hasta IS NULL OR m.fecha < :hasta)")
    Page<Movimiento> findPageWithFilters(@Param("sedeId") Integer sedeId,
                                        @Param("tipo") String tipo,
                                        @Param("subtipo") String subtipo,
                                        @Param("productoId") Integer productoId,
                                        @Param("desde") LocalDateTime desde,
                                        @Param("hasta") LocalDateTime hasta,
                                        Pageable pageable);

    @Query("SELECT COUNT(m) FROM Movimiento m WHERE (:sedeId IS NULL OR m.sede.idSede = :sedeId) AND m.fecha >= :desde")
    long countMovimientosDesde(@Param("sedeId") Integer sedeId, @Param("desde") LocalDateTime desde);
}

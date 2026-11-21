package com.tottus.inventario.repository;

import com.tottus.inventario.model.DetalleOperacionInventario;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface DetalleOperacionInventarioRepository extends JpaRepository<DetalleOperacionInventario, Integer> {
    List<DetalleOperacionInventario> findByOperacion_IdOperacionOrderByIdDetalle(Integer idOperacion);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM DetalleOperacionInventario d WHERE d.operacion.idOperacion = :operationId ORDER BY d.idDetalle")
    List<DetalleOperacionInventario> findForUpdateByOperacion(@Param("operationId") Integer operationId);
    void deleteByOperacion_IdOperacion(Integer idOperacion);
}

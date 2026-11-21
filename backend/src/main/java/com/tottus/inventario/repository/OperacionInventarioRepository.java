package com.tottus.inventario.repository;

import com.tottus.inventario.model.OperacionInventario;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface OperacionInventarioRepository extends JpaRepository<OperacionInventario, Integer> {
    Optional<OperacionInventario> findByTipoAndReferencia(String tipo, String referencia);
    List<OperacionInventario> findByTipoAndSede_IdSedeOrderByFechaDesc(String tipo, Integer idSede);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM OperacionInventario o WHERE o.idOperacion = :id")
    Optional<OperacionInventario> findForUpdate(@Param("id") Integer id);
}

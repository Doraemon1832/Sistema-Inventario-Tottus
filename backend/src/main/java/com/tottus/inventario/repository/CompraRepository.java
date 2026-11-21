package com.tottus.inventario.repository;
import com.tottus.inventario.model.Compra;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
public interface CompraRepository extends JpaRepository<Compra,Integer> {
    List<Compra> findBySede_IdSedeOrderByFechaDesc(Integer sedeId);
    Optional<Compra> findByReferencia(String referencia);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Compra c WHERE c.idCompra = :id")
    Optional<Compra> findForUpdate(@Param("id") Integer id);
}

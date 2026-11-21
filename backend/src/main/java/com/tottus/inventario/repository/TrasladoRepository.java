package com.tottus.inventario.repository;

import com.tottus.inventario.model.Traslado;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface TrasladoRepository extends JpaRepository<Traslado, Integer> {
    Optional<Traslado> findBySedeOrigen_IdSedeAndReferencia(Integer sedeId, String referencia);
    List<Traslado> findBySedeOrigen_IdSedeOrSedeDestino_IdSedeOrderByFechaSolicitudDesc(Integer origen, Integer destino);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Traslado t where t.idTraslado = :id")
    Optional<Traslado> findForUpdate(@Param("id") Integer id);
}

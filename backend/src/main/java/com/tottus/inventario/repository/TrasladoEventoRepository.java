package com.tottus.inventario.repository;

import com.tottus.inventario.model.TrasladoEvento;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface TrasladoEventoRepository extends JpaRepository<TrasladoEvento, Integer> {
    Optional<TrasladoEvento> findByClaveIdempotencia(String key);
}

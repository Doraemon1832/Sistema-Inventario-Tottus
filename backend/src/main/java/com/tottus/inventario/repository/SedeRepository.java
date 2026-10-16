package com.tottus.inventario.repository;

import com.tottus.inventario.model.Sede;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SedeRepository extends JpaRepository<Sede, Integer> {
    List<Sede> findByEstadoTrue();
    Optional<Sede> findByNombre(String nombre);
}

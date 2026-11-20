package com.tottus.inventario.repository;

import com.tottus.inventario.model.DetalleTraslado;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DetalleTrasladoRepository extends JpaRepository<DetalleTraslado, Integer> {
    List<DetalleTraslado> findByTraslado_IdTrasladoOrderByIdDetalleTraslado(Integer trasladoId);
}

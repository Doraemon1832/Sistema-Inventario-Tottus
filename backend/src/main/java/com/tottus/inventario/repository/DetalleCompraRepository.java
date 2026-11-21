package com.tottus.inventario.repository;
import com.tottus.inventario.model.DetalleCompra;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface DetalleCompraRepository extends JpaRepository<DetalleCompra,Integer> {
    List<DetalleCompra> findByCompra_IdCompraOrderByIdDetalle(Integer idCompra);
    void deleteByCompra_IdCompra(Integer idCompra);
}

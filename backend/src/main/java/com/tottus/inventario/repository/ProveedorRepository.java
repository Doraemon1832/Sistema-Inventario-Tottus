package com.tottus.inventario.repository;
import com.tottus.inventario.model.Proveedor;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface ProveedorRepository extends JpaRepository<Proveedor,Integer> {
    Optional<Proveedor> findByDocumento(String documento);
    List<Proveedor> findByNombreContainingIgnoreCaseOrDocumentoContainingIgnoreCaseOrderByNombre(String nombre,String documento);
}

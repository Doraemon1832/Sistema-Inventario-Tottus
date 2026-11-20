package com.tottus.inventario.repository;

import com.tottus.inventario.model.UsuarioSede;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UsuarioSedeRepository extends JpaRepository<UsuarioSede, Integer> {
    boolean existsByUsuario_IdUsuarioAndSede_IdSede(Integer userId, Integer siteId);
    List<UsuarioSede> findByUsuario_IdUsuario(Integer userId);
    void deleteByUsuario_IdUsuario(Integer userId);
}

package com.tottus.inventario.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data @Builder
public class UsuarioAdminResponseDTO {
    private Integer idUsuario;
    private String usuario;
    private String rol;
    private Integer idSedePrincipal;
    private String sedePrincipal;
    private List<Integer> idSedesAutorizadas;
    private boolean accesoGlobal;
    private boolean estado;
    private boolean cambioPasswordRequerido;
}

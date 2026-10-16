package com.tottus.inventario.security.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private String token;
    private Integer idUsuario;
    private String usuario;
    private String rol;
    private Integer idSede;
    private String nombreSede;
}

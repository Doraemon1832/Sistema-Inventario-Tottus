package com.tottus.inventario.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class GlobalAdminBootstrapRequest {
    @NotBlank @Size(min = 16, max = 72)
    private String passwordInicial;
}

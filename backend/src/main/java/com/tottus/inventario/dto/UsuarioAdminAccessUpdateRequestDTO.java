package com.tottus.inventario.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class UsuarioAdminAccessUpdateRequestDTO {
    @NotBlank private String rol;
    @NotEmpty @Size(max = 20) private List<@NotNull @Positive Integer> idSedes;
    private boolean accesoGlobal;
}

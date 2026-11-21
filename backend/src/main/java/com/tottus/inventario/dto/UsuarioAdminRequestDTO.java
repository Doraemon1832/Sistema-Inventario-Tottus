package com.tottus.inventario.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.List;

@Data
public class UsuarioAdminRequestDTO {
    @NotBlank @Size(max = 50) @Pattern(regexp = "[a-z0-9]+(?:[._-][a-z0-9]+)*")
    private String usuario;
    @NotBlank
    private String rol;
    @NotEmpty @Size(max = 20)
    private List<@NotNull @Positive Integer> idSedes;
    @Size(max = 72)
    private String passwordInicial;
    private boolean accesoGlobal;
}

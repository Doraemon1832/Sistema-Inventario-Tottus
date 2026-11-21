package com.tottus.inventario.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EgresoRechazoRequestDTO {
    @NotBlank
    @Size(max = 255)
    private String motivo;
}

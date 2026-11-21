package com.tottus.inventario.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminPasswordResetRequestDTO {
    @NotBlank @Size(min = 12, max = 72)
    private String password;
}

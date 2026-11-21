package com.tottus.inventario.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PasswordChangeRequest {
    @NotBlank @Size(max = 72)
    private String currentPassword;
    @NotBlank @Size(min = 12, max = 72)
    private String newPassword;
}

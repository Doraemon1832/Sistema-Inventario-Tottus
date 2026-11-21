package com.tottus.inventario.dto;
import jakarta.validation.constraints.*;
import lombok.*;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ProveedorRequestDTO {
    @NotBlank @Pattern(regexp="[0-9]{11}", message="El RUC debe contener exactamente 11 dígitos") private String documento;
    @NotBlank @Size(max=150) private String nombre;
    @Size(max=120) private String contacto;
    @Size(max=30) private String telefono;
    @Email @Size(max=150) private String correo;
}

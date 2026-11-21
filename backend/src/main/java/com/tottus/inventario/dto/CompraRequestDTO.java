package com.tottus.inventario.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;
import java.util.List;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CompraRequestDTO {
    @NotNull @Positive private Integer idSede;
    @NotNull @Positive private Integer idProveedor;
    @NotBlank @Size(max=100) private String referencia;
    @NotEmpty @Size(max=100) @Valid private List<CompraDetalleRequestDTO> detalles;
}

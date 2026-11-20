package com.tottus.inventario.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;
import java.util.List;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class TrasladoCreateRequestDTO {
    @NotNull @Positive private Integer idSedeOrigen;
    @NotNull @Positive private Integer idSedeDestino;
    @NotBlank @Size(max = 100) private String referencia;
    @Size(max = 2000) private String observaciones;
    @NotEmpty @Size(max = 100) @Valid private List<TrasladoItemRequestDTO> detalles;
}

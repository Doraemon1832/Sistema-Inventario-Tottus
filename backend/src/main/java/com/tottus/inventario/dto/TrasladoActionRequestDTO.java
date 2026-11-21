package com.tottus.inventario.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;
import java.util.List;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class TrasladoActionRequestDTO {
    @NotBlank @Size(max = 80) private String idempotencyKey;
    @Size(max = 100) private String referenciaDevolucion;
    @Size(max = 100) @Valid private List<TrasladoItemRequestDTO> detalles;
}

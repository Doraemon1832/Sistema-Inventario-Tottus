package com.tottus.inventario.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class TrasladoItemRequestDTO {
    @NotNull @Positive private Integer idProducto;
    @NotNull @Positive private Integer cantidad;
}

package com.tottus.inventario.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DetalleOperacionRequestDTO {
    @NotNull @Positive
    private Integer idProducto;
    @NotNull @Positive
    private Integer cantidad;
}

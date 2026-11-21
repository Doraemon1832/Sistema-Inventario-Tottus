package com.tottus.inventario.dto;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CompraDetalleRequestDTO {
    @NotNull @Positive private Integer idProducto;
    @NotNull @Positive private Integer cantidad;
    @DecimalMin("0.00") private BigDecimal costoUnitario;
}

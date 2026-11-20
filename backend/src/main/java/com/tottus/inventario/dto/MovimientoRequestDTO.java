package com.tottus.inventario.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MovimientoRequestDTO {
    @NotNull @Positive
    private Integer idSede;
    @NotNull @Positive
    private Integer idProducto;
    @NotBlank
    private String tipo; // ENTRADA, SALIDA, AJUSTE
    @NotNull @PositiveOrZero
    private Integer cantidad;
    private String referencia;

    @AssertTrue(message = "ENTRADA y SALIDA requieren cantidad positiva; AJUSTE acepta stock final no negativo")
    public boolean isCantidadValidaParaTipo() {
        if (tipo == null || cantidad == null) return true;
        return "AJUSTE".equalsIgnoreCase(tipo) ? cantidad >= 0
                : ("ENTRADA".equalsIgnoreCase(tipo) || "SALIDA".equalsIgnoreCase(tipo)) && cantidad > 0;
    }
}

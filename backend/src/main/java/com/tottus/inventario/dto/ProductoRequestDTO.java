package com.tottus.inventario.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductoRequestDTO {
    @NotBlank @Size(max = 150)
    private String codigo;
    @NotBlank @Size(max = 150)
    private String nombre;
    @Size(max = 30)
    private String unidadMedida;
    @Min(1)
    private Integer idCategoria;
    @DecimalMin(value = "0.00", inclusive = false)
    private BigDecimal precio;
    @Min(0)
    private Integer stockMinimo;
    // Datos opcionales para inicializar inventario en una sede
    @Min(1)
    private Integer idSede;
    @Min(0)
    private Integer stockInicial;
    @Size(max = 100)
    private String ubicacion;
}

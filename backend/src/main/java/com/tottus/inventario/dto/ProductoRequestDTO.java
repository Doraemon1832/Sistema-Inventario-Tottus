package com.tottus.inventario.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductoRequestDTO {
    private String codigo;
    private String nombre;
    private Integer idCategoria;
    private BigDecimal precio;
    private Integer stockMinimo;
    // Datos opcionales para inicializar inventario en una sede
    private Integer idSede;
    private Integer stockInicial;
    private String ubicacion;
}

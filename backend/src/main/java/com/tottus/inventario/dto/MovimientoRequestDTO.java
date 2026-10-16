package com.tottus.inventario.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MovimientoRequestDTO {
    private Integer idSede;
    private Integer idProducto;
    private String tipo; // ENTRADA, SALIDA, AJUSTE
    private Integer cantidad;
    private String referencia;
}

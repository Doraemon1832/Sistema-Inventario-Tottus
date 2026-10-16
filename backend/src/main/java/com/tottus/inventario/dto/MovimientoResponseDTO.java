package com.tottus.inventario.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MovimientoResponseDTO {
    private Integer idMovimiento;
    private Integer idSede;
    private String nombreSede;
    private Integer idProducto;
    private String codigoProducto;
    private String nombreProducto;
    private String tipo;
    private Integer cantidad;
    private LocalDateTime fecha;
    private String referencia;
    private String usuario;
}

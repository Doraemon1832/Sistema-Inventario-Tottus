package com.tottus.inventario.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumenInventarioDTO {
    private long totalProductos;
    private long stockBajo;
    private long sinStock;
    private long totalUnidades;
    private long movimientosHoy;
}

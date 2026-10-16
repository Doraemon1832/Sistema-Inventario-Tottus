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
public class InventarioResponseDTO {
    private Integer idInventario;
    private Integer idSede;
    private String nombreSede;
    private Integer idProducto;
    private String codigoProducto;
    private String nombreProducto;
    private String categoria;
    private Integer idCategoria;
    private BigDecimal precio;
    private Integer stockActual;
    private Integer stockMinimo;
    private String ubicacion;
    private String estadoStock; // OPTIMO, BAJO_STOCK, AGOTADO
    private Boolean estado; // true = Activo, false = Inactivo
}

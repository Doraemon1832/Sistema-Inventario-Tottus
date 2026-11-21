package com.tottus.inventario.dto;

import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DetalleOperacionResponseDTO {
    private Integer idProducto;
    private String codigoProducto;
    private String nombreProducto;
    private String unidadMedida;
    private Integer cantidad;
}

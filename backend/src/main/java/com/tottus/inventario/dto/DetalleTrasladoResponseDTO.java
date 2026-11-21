package com.tottus.inventario.dto;

import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DetalleTrasladoResponseDTO {
    private Integer idProducto;
    private String codigoProducto;
    private String nombreProducto;
    private String unidadMedida;
    private Integer cantidadSolicitada;
    private Integer cantidadDespachada;
    private Integer cantidadRecibida;
    private Integer cantidadEnTransito;
}

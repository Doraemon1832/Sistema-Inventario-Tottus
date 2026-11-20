package com.tottus.inventario.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class TrasladoResponseDTO {
    private Integer idTraslado;
    private String codigo;
    private Integer idSedeOrigen;
    private String sedeOrigen;
    private Integer idSedeDestino;
    private String sedeDestino;
    private String estado;
    private String referencia;
    private String observaciones;
    private String responsable;
    private String responsableDespacho;
    private String responsableRecepcion;
    private LocalDateTime fechaSolicitud;
    private LocalDateTime fechaDespacho;
    private LocalDateTime fechaFinalizacion;
    private List<DetalleTrasladoResponseDTO> detalles;
}

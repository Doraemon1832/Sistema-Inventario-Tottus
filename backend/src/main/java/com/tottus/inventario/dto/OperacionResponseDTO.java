package com.tottus.inventario.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class OperacionResponseDTO {
    private Integer idOperacion;
    private String tipo;
    private String subtipo;
    private String estado;
    private Integer idSede;
    private String nombreSede;
    private String usuario;
    private LocalDateTime fecha;
    private String referencia;
    private Integer idProveedor;
    private String nombreProveedor;
    private String beneficiario;
    private String motivo;
    private String observaciones;
    private String aprobadoPor;
    private LocalDateTime fechaRevision;
    private String motivoRevision;
    private List<DetalleOperacionResponseDTO> detalles;
}

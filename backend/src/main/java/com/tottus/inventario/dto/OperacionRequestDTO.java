package com.tottus.inventario.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class OperacionRequestDTO {
    @NotNull @Positive
    private Integer idSede;
    @Pattern(regexp = "INGRESO_GENERAL|DEVOLUCION_CLIENTE|PRODUCCION|BONIFICACION_PROVEEDOR|COMPRA|VENTA|DEVOLUCION_PROVEEDOR|MERMA|DESMEDRO|DONACION|CONSUMO_INTERNO", message = "Subtipo de operación inválido")
    private String subtipo;
    @Positive private Integer idProveedor;
    @Size(max = 150) private String beneficiario;
    @Size(max = 150) private String motivo;
    @Size(max = 2000) private String observaciones;
    @NotBlank @Size(max = 100)
    private String referencia;
    @NotEmpty @Size(max = 100)
    @Valid
    private List<DetalleOperacionRequestDTO> detalles;
}

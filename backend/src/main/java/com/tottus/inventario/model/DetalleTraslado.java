package com.tottus.inventario.model;

import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name = "detalle_traslado", uniqueConstraints =
        @UniqueConstraint(name = "uq_traslado_producto", columnNames = {"id_traslado", "id_producto"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DetalleTraslado {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_detalle_traslado") private Integer idDetalleTraslado;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "id_traslado", nullable = false) private Traslado traslado;
    @ManyToOne(fetch = FetchType.EAGER) @JoinColumn(name = "id_producto", nullable = false) private Producto producto;
    @Column(name = "cantidad_solicitada", nullable = false) private Integer cantidadSolicitada;
    @Builder.Default @Column(name = "cantidad_despachada", nullable = false) private Integer cantidadDespachada = 0;
    @Builder.Default @Column(name = "cantidad_recibida", nullable = false) private Integer cantidadRecibida = 0;
}

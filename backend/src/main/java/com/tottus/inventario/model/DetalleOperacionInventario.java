package com.tottus.inventario.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "detalle_operacion_inventario", uniqueConstraints =
        @UniqueConstraint(name = "uq_detalle_operacion_producto", columnNames = {"id_operacion", "id_producto"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DetalleOperacionInventario {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_detalle")
    private Integer idDetalle;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "id_operacion", nullable = false)
    private OperacionInventario operacion;

    @ManyToOne(fetch = FetchType.EAGER) @JoinColumn(name = "id_producto", nullable = false)
    private Producto producto;

    @Column(name = "cantidad", nullable = false)
    private Integer cantidad;
}

package com.tottus.inventario.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity @Table(name="detalle_compra", uniqueConstraints=@UniqueConstraint(name="uq_detalle_compra_producto", columnNames={"id_compra","id_producto"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DetalleCompra {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id_detalle") private Integer idDetalle;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="id_compra", nullable=false) private Compra compra;
    @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="id_producto", nullable=false) private Producto producto;
    @Column(name="cantidad", nullable=false) private Integer cantidad;
    @Column(name="costo_unitario", precision=10, scale=2) private BigDecimal costoUnitario;
}

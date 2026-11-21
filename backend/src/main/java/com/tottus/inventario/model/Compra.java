package com.tottus.inventario.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name="compra") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Compra {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id_compra") private Integer idCompra;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="id_proveedor", nullable=false) private Proveedor proveedor;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="id_sede", nullable=false) private Sede sede;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="id_usuario", nullable=false) private Usuario usuario;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="id_operacion_recepcion") private OperacionInventario operacionRecepcion;
    @Column(name="referencia", nullable=false, unique=true, length=100) private String referencia;
    @Builder.Default @Column(name="estado", nullable=false, length=15) private String estado="PENDIENTE";
    @Column(name="fecha", nullable=false) private LocalDateTime fecha;
}

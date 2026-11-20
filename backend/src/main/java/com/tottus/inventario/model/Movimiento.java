package com.tottus.inventario.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "movimiento")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Movimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_movimiento")
    private Integer idMovimiento;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_sede")
    private Sede sede;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_producto")
    private Producto producto;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_usuario")
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_operacion")
    private OperacionInventario operacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_traslado")
    private Traslado traslado;

    @Column(name = "tipo", length = 20)
    private String tipo; // ENTRADA, SALIDA, AJUSTE

    @Column(name = "subtipo", length = 30)
    private String subtipo;

    @Column(name = "cantidad")
    private Integer cantidad;

    @Column(name = "fecha")
    private LocalDateTime fecha;

    @Column(name = "referencia", length = 100)
    private String referencia;

    @Column(name = "stock_anterior")
    private Integer stockAnterior;

    @Column(name = "stock_posterior")
    private Integer stockPosterior;
}

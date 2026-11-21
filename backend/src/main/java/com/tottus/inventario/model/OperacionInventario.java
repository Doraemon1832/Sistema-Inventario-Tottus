package com.tottus.inventario.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "operacion_inventario", uniqueConstraints =
        @UniqueConstraint(name = "uq_operacion_tipo_referencia", columnNames = {"tipo", "referencia"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OperacionInventario {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_operacion")
    private Integer idOperacion;

    @Column(name = "tipo", nullable = false, length = 10)
    private String tipo;

    @Builder.Default
    @Column(name = "estado", nullable = false, length = 15)
    private String estado = "CONFIRMADA";

    @Column(name = "subtipo", length = 30)
    private String subtipo;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "id_sede", nullable = false)
    private Sede sede;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "id_usuario_aprueba")
    private Usuario usuarioAprueba;

    @Column(name = "fecha_revision")
    private LocalDateTime fechaRevision;

    @Column(name = "motivo_revision", length = 255)
    private String motivoRevision;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "id_proveedor")
    private Proveedor proveedor;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;

    @Column(name = "referencia", nullable = false, length = 100)
    private String referencia;

    @Column(name = "motivo", length = 150)
    private String motivo;

    @Column(name = "observaciones", columnDefinition = "TEXT")
    private String observaciones;

    @Column(name = "beneficiario", length = 150)
    private String beneficiario;
}

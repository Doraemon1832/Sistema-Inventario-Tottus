package com.tottus.inventario.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name = "traslado")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Traslado {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_traslado") private Integer idTraslado;
    @Column(name = "codigo", nullable = false, unique = true, length = 40) private String codigo;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "id_sede_origen", nullable = false) private Sede sedeOrigen;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "id_sede_destino", nullable = false) private Sede sedeDestino;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "id_usuario_solicita", nullable = false) private Usuario usuarioSolicita;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "id_usuario_despacha") private Usuario usuarioDespacha;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "id_usuario_recibe") private Usuario usuarioRecibe;
    @Column(name = "estado", nullable = false, length = 24) private String estado;
    @Column(name = "referencia", nullable = false, length = 100) private String referencia;
    @Column(name = "observaciones", columnDefinition = "TEXT") private String observaciones;
    @Column(name = "referencia_devolucion", length = 100) private String referenciaDevolucion;
    @Column(name = "fecha_solicitud", nullable = false) private LocalDateTime fechaSolicitud;
    @Column(name = "fecha_despacho") private LocalDateTime fechaDespacho;
    @Column(name = "fecha_finalizacion") private LocalDateTime fechaFinalizacion;
}

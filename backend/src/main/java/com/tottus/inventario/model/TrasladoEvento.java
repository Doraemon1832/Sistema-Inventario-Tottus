package com.tottus.inventario.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name = "traslado_evento", uniqueConstraints =
        @UniqueConstraint(name = "uq_traslado_evento_idempotencia", columnNames = "clave_idempotencia"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TrasladoEvento {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id_evento") private Integer idEvento;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "id_traslado", nullable = false) private Traslado traslado;
    @Column(name = "clave_idempotencia", nullable = false, unique = true, length = 80) private String claveIdempotencia;
    @Column(name = "accion", nullable = false, length = 20) private String accion;
    @Column(name = "hash_solicitud", nullable = false, length = 64) private String hashSolicitud;
    @Column(name = "fecha", nullable = false) private LocalDateTime fecha;
}

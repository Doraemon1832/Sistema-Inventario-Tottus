package com.tottus.inventario.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "auditoria")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Auditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_auditoria")
    private Integer idAuditoria;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario")
    private Usuario usuario;

    @Column(name = "accion", length = 100)
    private String accion;

    @Column(name = "fecha")
    private LocalDateTime fecha;

    @Column(name = "ip", length = 45)
    private String ip;

    @Column(name = "detalle", columnDefinition = "TEXT")
    private String detalle;
}

package com.tottus.inventario.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "usuario_sede", uniqueConstraints =
        @UniqueConstraint(name = "uq_usuario_sede", columnNames = {"id_usuario", "id_sede"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UsuarioSede {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_usuario_sede")
    private Integer idUsuarioSede;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "id_sede", nullable = false)
    private Sede sede;
}

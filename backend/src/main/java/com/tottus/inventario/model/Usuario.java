package com.tottus.inventario.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "usuario")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_usuario")
    private Integer idUsuario;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_rol")
    private Rol rol;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_sede")
    private Sede sede;

    @Column(name = "usuario", unique = true, length = 50)
    private String usuario;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "estado")
    private Boolean estado;
}

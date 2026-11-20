package com.tottus.inventario.model;

import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name="proveedor") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Proveedor {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id_proveedor") private Integer idProveedor;
    @Column(name="documento", nullable=false, unique=true, length=20) private String documento;
    @Column(name="nombre", nullable=false, length=150) private String nombre;
    @Column(name="contacto", length=120) private String contacto;
    @Column(name="telefono", length=30) private String telefono;
    @Column(name="correo", length=150) private String correo;
    @Builder.Default @Column(name="estado", nullable=false) private Boolean estado=true;
}

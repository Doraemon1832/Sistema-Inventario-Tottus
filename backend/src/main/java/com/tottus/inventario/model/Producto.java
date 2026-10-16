package com.tottus.inventario.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "producto")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_producto")
    private Integer idProducto;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_categoria")
    private Categoria categoria;

    @Column(name = "codigo", unique = true, length = 150)
    private String codigo;

    @Column(name = "nombre", length = 150)
    private String nombre;

    @Column(name = "precio", precision = 10, scale = 2)
    private BigDecimal precio;

    @Column(name = "stock_minimo")
    private Integer stockMinimo;

    @Builder.Default
    @Column(name = "estado")
    private Boolean estado = true;
}


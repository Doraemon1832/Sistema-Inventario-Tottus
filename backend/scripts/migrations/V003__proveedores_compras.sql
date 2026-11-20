-- Extiende el esquema base + V002. No elimina ni altera datos históricos.
CREATE TABLE proveedor (
    id_proveedor INT AUTO_INCREMENT PRIMARY KEY,
    documento VARCHAR(20) NOT NULL,
    nombre VARCHAR(150) NOT NULL,
    contacto VARCHAR(120) NULL,
    telefono VARCHAR(30) NULL,
    correo VARCHAR(150) NULL,
    estado BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_proveedor_documento UNIQUE (documento)
);

CREATE TABLE compra (
    id_compra INT AUTO_INCREMENT PRIMARY KEY,
    id_proveedor INT NOT NULL,
    id_sede INT NOT NULL,
    id_usuario INT NOT NULL,
    id_operacion_recepcion INT NULL,
    referencia VARCHAR(100) NOT NULL,
    estado VARCHAR(15) NOT NULL DEFAULT 'PENDIENTE',
    fecha DATETIME NOT NULL,
    CONSTRAINT uq_compra_referencia UNIQUE (referencia),
    CONSTRAINT ck_compra_estado CHECK (estado IN ('PENDIENTE', 'RECIBIDA')),
    CONSTRAINT fk_compra_proveedor FOREIGN KEY (id_proveedor) REFERENCES proveedor(id_proveedor),
    CONSTRAINT fk_compra_sede FOREIGN KEY (id_sede) REFERENCES sede(id_sede),
    CONSTRAINT fk_compra_usuario FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario),
    CONSTRAINT fk_compra_operacion FOREIGN KEY (id_operacion_recepcion) REFERENCES operacion_inventario(id_operacion)
);

CREATE TABLE detalle_compra (
    id_detalle INT AUTO_INCREMENT PRIMARY KEY,
    id_compra INT NOT NULL,
    id_producto INT NOT NULL,
    cantidad INT NOT NULL,
    costo_unitario DECIMAL(10,2) NULL,
    CONSTRAINT uq_detalle_compra_producto UNIQUE (id_compra, id_producto),
    CONSTRAINT ck_detalle_compra_cantidad CHECK (cantidad > 0),
    CONSTRAINT fk_detalle_compra FOREIGN KEY (id_compra) REFERENCES compra(id_compra),
    CONSTRAINT fk_detalle_compra_producto FOREIGN KEY (id_producto) REFERENCES producto(id_producto)
);
CREATE INDEX ix_compra_sede_fecha ON compra (id_sede, fecha);

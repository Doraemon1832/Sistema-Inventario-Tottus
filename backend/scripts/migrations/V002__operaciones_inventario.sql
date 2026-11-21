-- Requiere el esquema base de backend/scripts/01_schema_inv_tottus_db.sql.
-- Migración aditiva: conserva movimientos históricos y permite vincular nuevos movimientos.
-- Aplicar una sola vez sobre una instalación respaldada, después de verificar el esquema.

CREATE TABLE operacion_inventario (
    id_operacion INT AUTO_INCREMENT PRIMARY KEY,
    tipo VARCHAR(10) NOT NULL,
    id_sede INT NOT NULL,
    id_usuario INT NOT NULL,
    fecha DATETIME NOT NULL,
    referencia VARCHAR(100) NOT NULL,
    CONSTRAINT uq_operacion_tipo_referencia UNIQUE (tipo, referencia),
    CONSTRAINT fk_operacion_sede FOREIGN KEY (id_sede) REFERENCES sede(id_sede),
    CONSTRAINT fk_operacion_usuario FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario),
    CONSTRAINT ck_operacion_tipo CHECK (tipo IN ('INGRESO', 'EGRESO'))
);

CREATE TABLE detalle_operacion_inventario (
    id_detalle INT AUTO_INCREMENT PRIMARY KEY,
    id_operacion INT NOT NULL,
    id_producto INT NOT NULL,
    cantidad INT NOT NULL,
    CONSTRAINT uq_detalle_operacion_producto UNIQUE (id_operacion, id_producto),
    CONSTRAINT fk_detalle_operacion FOREIGN KEY (id_operacion) REFERENCES operacion_inventario(id_operacion),
    CONSTRAINT fk_detalle_producto FOREIGN KEY (id_producto) REFERENCES producto(id_producto),
    CONSTRAINT ck_detalle_cantidad CHECK (cantidad > 0)
);

ALTER TABLE movimiento
    ADD COLUMN id_operacion INT NULL,
    ADD CONSTRAINT fk_movimiento_operacion FOREIGN KEY (id_operacion)
        REFERENCES operacion_inventario(id_operacion);

CREATE INDEX ix_operacion_sede_fecha ON operacion_inventario (id_sede, fecha);
CREATE INDEX ix_operacion_referencia ON operacion_inventario (referencia);

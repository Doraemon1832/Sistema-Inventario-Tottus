-- Adds transfer lifecycle data without rewriting historical inventory movements.
-- Nullable balances remain unavailable for legacy rows; they are never inferred here.
ALTER TABLE producto
    ADD COLUMN unidad_medida VARCHAR(30) NULL;

ALTER TABLE operacion_inventario
    ADD COLUMN motivo VARCHAR(150) NULL,
    ADD COLUMN observaciones TEXT NULL,
    ADD COLUMN id_proveedor INT NULL,
    ADD COLUMN beneficiario VARCHAR(150) NULL,
    ADD CONSTRAINT fk_operacion_proveedor FOREIGN KEY (id_proveedor)
        REFERENCES proveedor(id_proveedor);

CREATE TABLE traslado (
    id_traslado INT AUTO_INCREMENT PRIMARY KEY,
    codigo VARCHAR(40) NOT NULL,
    id_sede_origen INT NOT NULL,
    id_sede_destino INT NOT NULL,
    id_usuario_solicita INT NOT NULL,
    id_usuario_despacha INT NULL,
    id_usuario_recibe INT NULL,
    estado VARCHAR(24) NOT NULL,
    referencia VARCHAR(100) NOT NULL,
    observaciones TEXT NULL,
    referencia_devolucion VARCHAR(100) NULL,
    fecha_solicitud DATETIME NOT NULL,
    fecha_despacho DATETIME NULL,
    fecha_finalizacion DATETIME NULL,
    CONSTRAINT uq_traslado_codigo UNIQUE (codigo),
    CONSTRAINT uq_traslado_origen_referencia UNIQUE (id_sede_origen, referencia),
    CONSTRAINT ck_traslado_sedes CHECK (id_sede_origen <> id_sede_destino),
    CONSTRAINT ck_traslado_estado CHECK (estado IN ('PENDIENTE','EN_TRANSITO','RECIBIDO','RECIBIDO_PARCIAL','CANCELADO')),
    CONSTRAINT fk_traslado_origen FOREIGN KEY (id_sede_origen) REFERENCES sede(id_sede),
    CONSTRAINT fk_traslado_destino FOREIGN KEY (id_sede_destino) REFERENCES sede(id_sede),
    CONSTRAINT fk_traslado_solicita FOREIGN KEY (id_usuario_solicita) REFERENCES usuario(id_usuario),
    CONSTRAINT fk_traslado_despacha FOREIGN KEY (id_usuario_despacha) REFERENCES usuario(id_usuario),
    CONSTRAINT fk_traslado_recibe FOREIGN KEY (id_usuario_recibe) REFERENCES usuario(id_usuario)
);

CREATE INDEX ix_traslado_origen_estado_fecha ON traslado (id_sede_origen, estado, fecha_solicitud);
CREATE INDEX ix_traslado_destino_estado_fecha ON traslado (id_sede_destino, estado, fecha_solicitud);

CREATE TABLE detalle_traslado (
    id_detalle_traslado INT AUTO_INCREMENT PRIMARY KEY,
    id_traslado INT NOT NULL,
    id_producto INT NOT NULL,
    cantidad_solicitada INT NOT NULL,
    cantidad_despachada INT NOT NULL DEFAULT 0,
    cantidad_recibida INT NOT NULL DEFAULT 0,
    CONSTRAINT uq_traslado_producto UNIQUE (id_traslado, id_producto),
    CONSTRAINT ck_traslado_cantidad_solicitada CHECK (cantidad_solicitada > 0),
    CONSTRAINT ck_traslado_cantidad_despachada CHECK (cantidad_despachada >= 0 AND cantidad_despachada <= cantidad_solicitada),
    CONSTRAINT ck_traslado_cantidad_recibida CHECK (cantidad_recibida >= 0 AND cantidad_recibida <= cantidad_despachada),
    CONSTRAINT fk_detalle_traslado FOREIGN KEY (id_traslado) REFERENCES traslado(id_traslado),
    CONSTRAINT fk_detalle_traslado_producto FOREIGN KEY (id_producto) REFERENCES producto(id_producto)
);

CREATE TABLE traslado_evento (
    id_evento INT AUTO_INCREMENT PRIMARY KEY,
    id_traslado INT NOT NULL,
    clave_idempotencia VARCHAR(80) NOT NULL,
    accion VARCHAR(20) NOT NULL,
    hash_solicitud CHAR(64) NOT NULL,
    fecha DATETIME NOT NULL,
    CONSTRAINT uq_traslado_evento_idempotencia UNIQUE (clave_idempotencia),
    CONSTRAINT fk_evento_traslado FOREIGN KEY (id_traslado) REFERENCES traslado(id_traslado)
);

ALTER TABLE movimiento
    ADD COLUMN stock_anterior INT NULL,
    ADD COLUMN stock_posterior INT NULL,
    ADD COLUMN id_traslado INT NULL,
    ADD CONSTRAINT fk_movimiento_traslado FOREIGN KEY (id_traslado)
        REFERENCES traslado(id_traslado);

CREATE INDEX ix_movimiento_traslado ON movimiento (id_traslado);

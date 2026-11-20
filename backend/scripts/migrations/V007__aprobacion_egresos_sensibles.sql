-- Pending warehouse expenses are stored without stock movements until an authorized reviewer approves them.
-- Existing operations become CONFIRMADA and retain their historical stock/Kardex state.
ALTER TABLE operacion_inventario
    ADD COLUMN estado VARCHAR(15) NOT NULL DEFAULT 'CONFIRMADA' AFTER tipo,
    ADD COLUMN id_usuario_aprueba INT NULL AFTER id_usuario,
    ADD COLUMN fecha_revision DATETIME NULL,
    ADD COLUMN motivo_revision VARCHAR(255) NULL,
    ADD CONSTRAINT fk_operacion_aprobador FOREIGN KEY (id_usuario_aprueba)
        REFERENCES usuario(id_usuario);

CREATE INDEX ix_operacion_sede_estado_fecha ON operacion_inventario (id_sede, estado, fecha);

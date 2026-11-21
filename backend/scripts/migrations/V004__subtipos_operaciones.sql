-- Optional classification for new inventory movements.
-- NULL is retained for historical movements; no legacy record is inferred or rewritten.
ALTER TABLE operacion_inventario
    ADD COLUMN subtipo VARCHAR(30) NULL AFTER tipo;

ALTER TABLE movimiento
    ADD COLUMN subtipo VARCHAR(30) NULL AFTER tipo;

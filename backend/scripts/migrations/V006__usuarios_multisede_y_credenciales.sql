-- Additive security migration. Keeps the legacy primary sede and all historical accounts.
ALTER TABLE usuario
    ADD COLUMN acceso_global BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN cambio_password_requerido BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE usuario_sede (
    id_usuario_sede INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    id_usuario INT NOT NULL,
    id_sede INT NOT NULL,
    CONSTRAINT uq_usuario_sede UNIQUE (id_usuario, id_sede),
    CONSTRAINT fk_usuario_sede_usuario FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario),
    CONSTRAINT fk_usuario_sede_sede FOREIGN KEY (id_sede) REFERENCES sede(id_sede)
);

INSERT INTO usuario_sede (id_usuario, id_sede)
SELECT id_usuario, id_sede FROM usuario WHERE id_sede IS NOT NULL;

CREATE INDEX ix_usuario_sede_sede ON usuario_sede (id_sede, id_usuario);

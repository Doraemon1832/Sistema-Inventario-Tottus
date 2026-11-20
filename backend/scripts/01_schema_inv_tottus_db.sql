-- Esquema canonico completo para instalaciones NUEVAS de Sistema-Inventario-Tottus.
-- No ejecutar sobre una instalacion existente. Para actualizar una base antigua,
-- respaldar, probar en una copia y aplicar V002 y V003 una sola vez.
-- Spring Boot es la unica autoridad de stock; este esquema no instala triggers de stock.
-- No incluye datos: DataInitializer crea los datos iniciales de la aplicación.

CREATE DATABASE IF NOT EXISTS inv_tottus_db
    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

USE inv_tottus_db;

CREATE TABLE rol (
    id_rol INT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(50),
    estado BOOLEAN
);

CREATE TABLE sede (
    id_sede INT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(100),
    direccion VARCHAR(200),
    estado BOOLEAN
);

CREATE TABLE usuario (
    id_usuario INT AUTO_INCREMENT PRIMARY KEY,
    id_rol INT,
    id_sede INT,
    usuario VARCHAR(50) UNIQUE,
    password_hash VARCHAR(255),
    estado BOOLEAN,
    CONSTRAINT fk_rol_usuario FOREIGN KEY (id_rol) REFERENCES rol(id_rol),
    CONSTRAINT fk_sede_usuario FOREIGN KEY (id_sede) REFERENCES sede(id_sede)
);

CREATE TABLE categoria (
    id_categoria INT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(100),
    estado BOOLEAN
);

CREATE TABLE producto (
    id_producto INT AUTO_INCREMENT PRIMARY KEY,
    id_categoria INT,
    codigo VARCHAR(150) UNIQUE,
    nombre VARCHAR(150),
    precio DECIMAL(10,2),
    stock_minimo INT,
    estado BOOLEAN DEFAULT TRUE,
    CONSTRAINT fk_categoria_producto FOREIGN KEY (id_categoria) REFERENCES categoria(id_categoria)
);

CREATE TABLE inventario (
    id_inventario INT AUTO_INCREMENT PRIMARY KEY,
    id_sede INT,
    id_producto INT,
    stock_actual INT DEFAULT 0,
    ubicacion VARCHAR(100),
    UNIQUE (id_sede, id_producto),
    CONSTRAINT fk_sede_inventario FOREIGN KEY (id_sede) REFERENCES sede(id_sede),
    CONSTRAINT fk_producto_inventario FOREIGN KEY (id_producto) REFERENCES producto(id_producto)
);

CREATE TABLE movimiento (
    id_movimiento INT AUTO_INCREMENT PRIMARY KEY,
    id_sede INT,
    id_producto INT,
    id_usuario INT,
    id_operacion INT NULL,
    subtipo VARCHAR(30) NULL,
    tipo VARCHAR(20),
    cantidad INT,
    fecha DATETIME,
    referencia VARCHAR(100),
    CONSTRAINT fk_sede_movimiento FOREIGN KEY (id_sede) REFERENCES sede(id_sede),
    CONSTRAINT fk_producto_movimiento FOREIGN KEY (id_producto) REFERENCES producto(id_producto),
    CONSTRAINT fk_usuario_movimiento FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario)
);

CREATE TABLE auditoria (
    id_auditoria INT AUTO_INCREMENT PRIMARY KEY,
    id_usuario INT,
    accion VARCHAR(100),
    fecha DATETIME,
    ip VARCHAR(45),
    detalle TEXT,
    CONSTRAINT fk_usuario_auditoria FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario)
);

CREATE TABLE operacion_inventario (
    id_operacion INT AUTO_INCREMENT PRIMARY KEY,
    tipo VARCHAR(10) NOT NULL,
    subtipo VARCHAR(30) NULL,
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
    ADD CONSTRAINT fk_movimiento_operacion FOREIGN KEY (id_operacion)
        REFERENCES operacion_inventario(id_operacion);

CREATE INDEX ix_operacion_sede_fecha ON operacion_inventario (id_sede, fecha);
CREATE INDEX ix_operacion_referencia ON operacion_inventario (referencia);

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

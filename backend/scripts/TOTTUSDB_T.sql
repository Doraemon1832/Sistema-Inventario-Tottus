-- =========================================================
-- BASE DE DATOS: TOTTUS - SISTEMA DE INVENTARIO
-- Script completo con triggers
-- =========================================================

DROP DATABASE IF EXISTS inv_tottus_db;
CREATE DATABASE inv_tottus_db;
USE inv_tottus_db;

-- =========================================================
-- TABLAS
-- =========================================================

CREATE TABLE Proveedor (
    id_proveedor INT AUTO_INCREMENT PRIMARY KEY,
    ruc VARCHAR(20) UNIQUE,
    razon_social VARCHAR(150),
    telefono VARCHAR(20),
    estado BOOLEAN
);

CREATE TABLE Rol (
    id_rol INT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(50),
    estado BOOLEAN
);

CREATE TABLE Sede (
    id_sede INT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(100),
    direccion VARCHAR(200),
    estado BOOLEAN
);

CREATE TABLE Usuario (
    id_usuario INT AUTO_INCREMENT PRIMARY KEY,
    id_rol INT,
    id_sede INT,
    usuario VARCHAR(50) UNIQUE,
    password_hash VARCHAR(255),
    estado BOOLEAN,

    CONSTRAINT fk_rol_usuario
        FOREIGN KEY (id_rol) REFERENCES Rol(id_rol),

    CONSTRAINT fk_sede_usuario
        FOREIGN KEY (id_sede) REFERENCES Sede(id_sede)
);

CREATE TABLE Categoria (
    id_categoria INT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(100),
    estado BOOLEAN
);

CREATE TABLE Producto (
    id_producto INT AUTO_INCREMENT PRIMARY KEY,
    id_categoria INT,
    codigo VARCHAR(150) UNIQUE,
    nombre VARCHAR(150),
    precio DECIMAL(10,2),
    stock_minimo INT,

    CONSTRAINT fk_categoria_producto
        FOREIGN KEY (id_categoria)
        REFERENCES Categoria(id_categoria)
);

CREATE TABLE Inventario (
    id_inventario INT AUTO_INCREMENT PRIMARY KEY,
    id_sede INT,
    id_producto INT,
    stock_actual INT DEFAULT 0,
    ubicacion VARCHAR(100),

    UNIQUE (id_sede, id_producto),

    CONSTRAINT fk_sede_inventario
        FOREIGN KEY (id_sede)
        REFERENCES Sede(id_sede),

    CONSTRAINT fk_producto_inventario
        FOREIGN KEY (id_producto)
        REFERENCES Producto(id_producto)
);

CREATE TABLE Compra (
    id_compra INT AUTO_INCREMENT PRIMARY KEY,
    id_sede INT,
    id_proveedor INT,
    id_usuario INT,
    fecha DATETIME,
    total DECIMAL(12,2) DEFAULT 0,

    CONSTRAINT fk_sede_compra
        FOREIGN KEY (id_sede)
        REFERENCES Sede(id_sede),

    CONSTRAINT fk_proveedor_compra
        FOREIGN KEY (id_proveedor)
        REFERENCES Proveedor(id_proveedor),

    CONSTRAINT fk_usuario_compra
        FOREIGN KEY (id_usuario)
        REFERENCES Usuario(id_usuario)
);

CREATE TABLE Detalle_compra (
    id_det_compra INT AUTO_INCREMENT PRIMARY KEY,
    id_compra INT,
    id_producto INT,
    cantidad INT,
    precio DECIMAL(10,2),

    CONSTRAINT fk_compra_detcompra
        FOREIGN KEY (id_compra)
        REFERENCES Compra(id_compra),

    CONSTRAINT fk_producto_detcompra
        FOREIGN KEY (id_producto)
        REFERENCES Producto(id_producto)
);

CREATE TABLE Movimiento (
    id_movimiento INT AUTO_INCREMENT PRIMARY KEY,
    id_sede INT,
    id_producto INT,
    id_usuario INT,
    tipo VARCHAR(20),
    cantidad INT,
    fecha DATETIME,
    referencia VARCHAR(100),

    CONSTRAINT fk_sede_movimiento
        FOREIGN KEY (id_sede)
        REFERENCES Sede(id_sede),

    CONSTRAINT fk_producto_movimiento
        FOREIGN KEY (id_producto)
        REFERENCES Producto(id_producto),

    CONSTRAINT fk_usuario_movimiento
        FOREIGN KEY (id_usuario)
        REFERENCES Usuario(id_usuario)
);

CREATE TABLE Auditoria (
    id_auditoria INT AUTO_INCREMENT PRIMARY KEY,
    id_usuario INT,
    accion VARCHAR(100),
    fecha DATETIME,
    ip VARCHAR(45),
    detalle TEXT,

    CONSTRAINT fk_usuario_auditoria
        FOREIGN KEY (id_usuario)
        REFERENCES Usuario(id_usuario)
);

-- =========================================================
-- TRIGGERS
-- =========================================================

DELIMITER $$

-- ---------------------------------------------------------
-- 1. VALIDAR LOS DATOS DE UN DETALLE DE COMPRA
-- ---------------------------------------------------------
CREATE TRIGGER trg_validar_detalle_compra
BEFORE INSERT ON Detalle_compra
FOR EACH ROW
BEGIN
    IF NEW.cantidad IS NULL OR NEW.cantidad <= 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'La cantidad de compra debe ser mayor a cero';
    END IF;

    IF NEW.precio IS NULL OR NEW.precio <= 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'El precio de compra debe ser mayor a cero';
    END IF;
END$$


-- ---------------------------------------------------------
-- 2. REGISTRAR AUTOMATICAMENTE UNA ENTRADA POR COMPRA
-- ---------------------------------------------------------
CREATE TRIGGER trg_registrar_movimiento_compra
AFTER INSERT ON Detalle_compra
FOR EACH ROW
BEGIN
    DECLARE v_id_sede INT;
    DECLARE v_id_usuario INT;

    SELECT id_sede, id_usuario
    INTO v_id_sede, v_id_usuario
    FROM Compra
    WHERE id_compra = NEW.id_compra;

    INSERT INTO Movimiento (
        id_sede,
        id_producto,
        id_usuario,
        tipo,
        cantidad,
        fecha,
        referencia
    )
    VALUES (
        v_id_sede,
        NEW.id_producto,
        v_id_usuario,
        'ENTRADA',
        NEW.cantidad,
        NOW(),
        CONCAT('Compra N° ', NEW.id_compra)
    );
END$$


-- ---------------------------------------------------------
-- 3. CALCULAR AUTOMATICAMENTE EL TOTAL DE LA COMPRA
-- ---------------------------------------------------------
CREATE TRIGGER trg_actualizar_total_compra
AFTER INSERT ON Detalle_compra
FOR EACH ROW
BEGIN
    UPDATE Compra
    SET total = (
        SELECT COALESCE(SUM(cantidad * precio), 0)
        FROM Detalle_compra
        WHERE id_compra = NEW.id_compra
    )
    WHERE id_compra = NEW.id_compra;
END$$


-- ---------------------------------------------------------
-- 4. VALIDAR LOS MOVIMIENTOS DE INVENTARIO
-- ---------------------------------------------------------
CREATE TRIGGER trg_validar_movimiento
BEFORE INSERT ON Movimiento
FOR EACH ROW
BEGIN
    DECLARE v_stock_actual INT DEFAULT 0;
    DECLARE v_existe_inventario INT DEFAULT 0;

    IF NEW.tipo NOT IN ('ENTRADA', 'SALIDA') THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'El tipo de movimiento debe ser ENTRADA o SALIDA';
    END IF;

    IF NEW.cantidad IS NULL OR NEW.cantidad <= 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'La cantidad del movimiento debe ser mayor a cero';
    END IF;

    IF NEW.tipo = 'SALIDA' THEN

        SELECT COUNT(*)
        INTO v_existe_inventario
        FROM Inventario
        WHERE id_sede = NEW.id_sede
          AND id_producto = NEW.id_producto;

        IF v_existe_inventario = 0 THEN
            SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'El producto no existe en el inventario de esta sede';
        END IF;

        SELECT stock_actual
        INTO v_stock_actual
        FROM Inventario
        WHERE id_sede = NEW.id_sede
          AND id_producto = NEW.id_producto;

        IF NEW.cantidad > v_stock_actual THEN
            SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Stock insuficiente para realizar la salida';
        END IF;

    END IF;
END$$


-- ---------------------------------------------------------
-- 5. ACTUALIZAR EL INVENTARIO SEGUN EL MOVIMIENTO
-- ---------------------------------------------------------
CREATE TRIGGER trg_actualizar_stock_movimiento
AFTER INSERT ON Movimiento
FOR EACH ROW
BEGIN
    DECLARE v_existe INT DEFAULT 0;

    SELECT COUNT(*)
    INTO v_existe
    FROM Inventario
    WHERE id_sede = NEW.id_sede
      AND id_producto = NEW.id_producto;

    IF NEW.tipo = 'ENTRADA' THEN

        IF v_existe = 1 THEN

            UPDATE Inventario
            SET stock_actual = stock_actual + NEW.cantidad
            WHERE id_sede = NEW.id_sede
              AND id_producto = NEW.id_producto;

        ELSE

            INSERT INTO Inventario (
                id_sede,
                id_producto,
                stock_actual,
                ubicacion
            )
            VALUES (
                NEW.id_sede,
                NEW.id_producto,
                NEW.cantidad,
                'ALMACEN'
            );

        END IF;

    ELSEIF NEW.tipo = 'SALIDA' THEN

        UPDATE Inventario
        SET stock_actual = stock_actual - NEW.cantidad
        WHERE id_sede = NEW.id_sede
          AND id_producto = NEW.id_producto;

    END IF;
END$$


-- ---------------------------------------------------------
-- 6. VALIDAR QUE EL STOCK NUNCA SEA NEGATIVO
-- ---------------------------------------------------------
CREATE TRIGGER trg_validar_stock_inventario
BEFORE UPDATE ON Inventario
FOR EACH ROW
BEGIN
    IF NEW.stock_actual < 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'El stock no puede ser negativo';
    END IF;
END$$

DELIMITER ;

-- =========================================================
-- FIN DEL SCRIPT
-- =========================================================

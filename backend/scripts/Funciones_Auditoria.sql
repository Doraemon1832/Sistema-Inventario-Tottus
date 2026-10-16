USE inv_tottus_db;
DROP FUNCTION IF EXISTS fn_total_auditorias;
DROP FUNCTION IF EXISTS fn_ultima_accion_usuario;
DELIMITER //
CREATE FUNCTION fn_total_auditorias() 
RETURNS INT
DETERMINISTIC
READS SQL DATA
BEGIN
    DECLARE total INT;
    SELECT COUNT(*) INTO total FROM Auditoria;
    RETURN total;
END //
CREATE FUNCTION fn_ultima_accion_usuario(p_id_usuario INT) 
RETURNS VARCHAR(255)
DETERMINISTIC
READS SQL DATA
BEGIN
    DECLARE v_accion VARCHAR(255);
    SELECT CONCAT(accion, ' - ', fecha) INTO v_accion
    FROM Auditoria
    WHERE id_usuario = p_id_usuario
    ORDER BY fecha DESC
    LIMIT 1;
    IF v_accion IS NULL THEN
        SET v_accion = 'Sin registros de auditoría';
    END IF;
    RETURN v_accion;
END //
DELIMITER ;
SELECT fn_total_auditorias() AS total_auditorias;
SELECT fn_ultima_accion_usuario(1) AS ultima_accion;

INSERT INTO Auditoria (id_usuario, accion, fecha, ip, detalle) 
VALUES (1, 'Cierre de inventario mensual', NOW(), '192.168.1.50', 'Prueba insana');
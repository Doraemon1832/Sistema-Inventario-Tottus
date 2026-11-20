-- Renombra las cinco cuentas existentes de MegaPlaza en sitio.
-- No crea/elimina usuarios ni modifica claves, roles, sedes o relaciones.
-- La rutina admite una repetición completa y rechaza estados parciales.
DELIMITER //
CREATE PROCEDURE aplicar_v008_usuarios_megaplaza()
BEGIN
    DECLARE v_old_count INT DEFAULT 0;
    DECLARE v_target_count INT DEFAULT 0;
    DECLARE v_valid_old_count INT DEFAULT 0;
    DECLARE v_valid_target_count INT DEFAULT 0;

    SELECT COUNT(*) INTO v_old_count
      FROM usuario
     WHERE usuario IN ('gerencia_admin', 'jefe_almacen', 'operador_almacen', 'vendedor_tottus', 'encargado_compras');
    SELECT COUNT(*) INTO v_target_count
      FROM usuario
     WHERE usuario IN ('admin.megaplaza', 'jinv.megaplaza', 'alm.megaplaza', 'ven.megaplaza', 'compras.megaplaza');

    -- Parentheses keep the site predicate applicable to every role mapping.
    SELECT COUNT(*) INTO v_valid_old_count
      FROM usuario u
      JOIN rol r ON r.id_rol = u.id_rol
      JOIN sede s ON s.id_sede = u.id_sede
     WHERE s.nombre = 'Tottus MegaPlaza'
       AND ((u.usuario = 'gerencia_admin' AND r.nombre = 'ADMINISTRADOR')
         OR (u.usuario = 'jefe_almacen' AND r.nombre = 'JEFE_INVENTARIO')
         OR (u.usuario = 'operador_almacen' AND r.nombre = 'OPERADOR_ALMACEN')
         OR (u.usuario = 'vendedor_tottus' AND r.nombre = 'VENDEDOR')
         OR (u.usuario = 'encargado_compras' AND r.nombre = 'ENCARGADO_COMPRAS'));

    SELECT COUNT(*) INTO v_valid_target_count
      FROM usuario u
      JOIN rol r ON r.id_rol = u.id_rol
      JOIN sede s ON s.id_sede = u.id_sede
     WHERE s.nombre = 'Tottus MegaPlaza'
       AND ((u.usuario = 'admin.megaplaza' AND r.nombre = 'ADMINISTRADOR')
         OR (u.usuario = 'jinv.megaplaza' AND r.nombre = 'JEFE_INVENTARIO')
         OR (u.usuario = 'alm.megaplaza' AND r.nombre = 'OPERADOR_ALMACEN')
         OR (u.usuario = 'ven.megaplaza' AND r.nombre = 'VENDEDOR')
         OR (u.usuario = 'compras.megaplaza' AND r.nombre = 'ENCARGADO_COMPRAS'));

    IF v_old_count = 0 AND v_target_count = 5 AND v_valid_target_count = 5 THEN
        SELECT 'V008 already applied; no changes made' AS resultado;
    ELSEIF v_old_count = 5 AND v_target_count = 0 AND v_valid_old_count = 5 THEN
        START TRANSACTION;
        UPDATE usuario
           SET usuario = CASE usuario
               WHEN 'gerencia_admin' THEN 'admin.megaplaza'
               WHEN 'jefe_almacen' THEN 'jinv.megaplaza'
               WHEN 'operador_almacen' THEN 'alm.megaplaza'
               WHEN 'vendedor_tottus' THEN 'ven.megaplaza'
               WHEN 'encargado_compras' THEN 'compras.megaplaza'
               ELSE usuario
           END
         WHERE usuario IN ('gerencia_admin', 'jefe_almacen', 'operador_almacen', 'vendedor_tottus', 'encargado_compras');
        IF ROW_COUNT() <> 5 THEN
            ROLLBACK;
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'V008 expected to rename exactly five existing users';
        END IF;
        COMMIT;
        SELECT 'V008 applied; five existing users renamed' AS resultado;
    ELSE
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'V008 preconditions failed; no username changes made';
    END IF;
END//
DELIMITER ;

CALL aplicar_v008_usuarios_megaplaza();
DROP PROCEDURE aplicar_v008_usuarios_megaplaza;

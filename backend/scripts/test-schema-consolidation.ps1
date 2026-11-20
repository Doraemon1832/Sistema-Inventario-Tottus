$ErrorActionPreference = 'Stop'

$containerName = 'tottus-schema-verify-' + [guid]::NewGuid().ToString('N').Substring(0, 10)
$password = 'Disposable-' + [guid]::NewGuid().ToString('N')
$backendRoot = Split-Path -Parent $PSScriptRoot
$repositoryRoot = Split-Path -Parent $backendRoot
$containerStarted = $false

function Invoke-MySql([string]$database, [string]$query) {
    if ([string]::IsNullOrWhiteSpace($database)) {
        $output = docker exec -e "MYSQL_PWD=$password" $containerName mysql --protocol=TCP -h 127.0.0.1 -uroot -N -e $query
    } else {
        $output = docker exec -e "MYSQL_PWD=$password" $containerName mysql --protocol=TCP -h 127.0.0.1 -uroot -N $database -e $query
    }
    if ($LASTEXITCODE -ne 0) { throw "MySQL query failed for $database." }
    return @($output | ForEach-Object { $_.ToString().TrimEnd() })
}

function Apply-SqlFile([string]$database, [string]$path) {
    Get-Content -Raw $path | docker exec -i -e "MYSQL_PWD=$password" $containerName mysql -uroot $database
    if ($LASTEXITCODE -ne 0) { throw "SQL application failed: $path" }
}

try {
    docker run -d --rm --name $containerName `
        -e "MYSQL_ROOT_PASSWORD=$password" `
        -e 'MYSQL_DATABASE=inv_tottus_db' `
        --tmpfs '/var/lib/mysql:rw,noexec,nosuid,size=1g' `
        -p '127.0.0.1::3306' mysql:8.4 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Could not start disposable MySQL 8.4.' }
    $containerStarted = $true

    $ready = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        $previousPreference = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        try { $probe = docker exec -e "MYSQL_PWD=$password" $containerName mysql --protocol=TCP -h 127.0.0.1 -uroot -N -e 'SELECT 1' 2>$null }
        finally { $ErrorActionPreference = $previousPreference }
        if ($LASTEXITCODE -eq 0 -and ($probe -join '').Trim() -eq '1') { $ready = $true; break }
        Start-Sleep -Seconds 2
    }
    if (-not $ready) { throw 'Disposable MySQL 8.4 did not become ready.' }

    $canonicalDatabase = 'tottus_canonical_probe'
    $upgradeDatabase = 'tottus_upgrade_probe'
    $null = Invoke-MySql '' "CREATE DATABASE $canonicalDatabase CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; CREATE DATABASE $upgradeDatabase CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"

    $canonicalPath = Join-Path $backendRoot 'scripts\01_schema_inv_tottus_db.sql'
    $baseSchemaPath = Join-Path $backendRoot 'scripts\01_schema_inv_tottus_db.sql'
    $migration2 = Join-Path $backendRoot 'scripts\migrations\V002__operaciones_inventario.sql'
    $migration3 = Join-Path $backendRoot 'scripts\migrations\V003__proveedores_compras.sql'
    $migration4 = Join-Path $backendRoot 'scripts\migrations\V004__subtipos_operaciones.sql'

    $canonicalSql = [System.IO.File]::ReadAllText($canonicalPath, [System.Text.Encoding]::UTF8).Replace('inv_tottus_db', $canonicalDatabase)
    $canonicalSql | docker exec -i -e "MYSQL_PWD=$password" $containerName mysql -uroot
    if ($LASTEXITCODE -ne 0) { throw 'Fresh canonical schema installation failed.' }

    $legacySql = [System.IO.File]::ReadAllText($baseSchemaPath, [System.Text.Encoding]::UTF8)
    $legacySql = $legacySql.Substring(0, $legacySql.IndexOf('CREATE TABLE operacion_inventario', [System.StringComparison]::Ordinal))
    $legacySql = [System.Text.RegularExpressions.Regex]::Replace($legacySql, '(?m)^    id_operacion INT NULL,\r?\n', '')
    $legacySql = [System.Text.RegularExpressions.Regex]::Replace($legacySql, '(?m)^    subtipo VARCHAR\(30\) NULL,\r?\n', '')
    $legacySql = $legacySql.Replace('CREATE DATABASE IF NOT EXISTS inv_tottus_db' + [Environment]::NewLine + '    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;' + [Environment]::NewLine, '')
    $legacySql = $legacySql.Replace('inv_tottus_db', $upgradeDatabase)
    $legacySql | docker exec -i -e "MYSQL_PWD=$password" $containerName mysql -uroot
    if ($LASTEXITCODE -ne 0) { throw 'Could not create the base-schema upgrade fixture.' }

    $fixture = @'
INSERT INTO rol (nombre, estado) VALUES ('SCHEMA_TEST', TRUE);
INSERT INTO sede (nombre, direccion, estado) VALUES ('Disposable schema test', 'Temporary', TRUE);
INSERT INTO usuario (id_rol, id_sede, usuario, password_hash, estado) VALUES (1, 1, 'schema_probe', 'not-a-login-credential', TRUE);
INSERT INTO categoria (nombre, estado) VALUES ('Temporary category', TRUE);
INSERT INTO producto (id_categoria, codigo, nombre, precio, stock_minimo, estado) VALUES (1, 'SCHEMA-PROBE', 'Temporary product', 1.00, 1, TRUE);
INSERT INTO inventario (id_sede, id_producto, stock_actual, ubicacion) VALUES (1, 1, 5, 'Temporary');
INSERT INTO movimiento (id_sede, id_producto, id_usuario, tipo, cantidad, fecha, referencia) VALUES (1, 1, 1, 'ENTRADA', 5, '2026-01-01 00:00:00', 'schema-probe');
'@
    $fixture | docker exec -i -e "MYSQL_PWD=$password" $containerName mysql -uroot $upgradeDatabase
    if ($LASTEXITCODE -ne 0) { throw 'Could not insert the disposable migration fixture.' }
    $before = (Invoke-MySql $upgradeDatabase "SELECT CONCAT((SELECT COUNT(*) FROM movimiento),'|',(SELECT MAX(id_movimiento) FROM movimiento),'|',(SELECT stock_actual FROM inventario WHERE id_sede=1 AND id_producto=1),'|',(SELECT referencia FROM movimiento WHERE id_movimiento=1))")[0]

    Apply-SqlFile $upgradeDatabase $migration2
    Apply-SqlFile $upgradeDatabase $migration3
    Apply-SqlFile $upgradeDatabase $migration4
    $after = (Invoke-MySql $upgradeDatabase "SELECT CONCAT((SELECT COUNT(*) FROM movimiento),'|',(SELECT MAX(id_movimiento) FROM movimiento),'|',(SELECT stock_actual FROM inventario WHERE id_sede=1 AND id_producto=1),'|',(SELECT referencia FROM movimiento WHERE id_movimiento=1))")[0]
    if ($before -ne $after) { throw "Migration changed the historical movement/stock fixture: before=$before after=$after" }

    $queries = @(
        "SELECT CONCAT(TABLE_NAME,'|',COLUMN_NAME,'|',COLUMN_TYPE,'|',IS_NULLABLE,'|',COALESCE(COLUMN_DEFAULT,'<NULL>'),'|',EXTRA) FROM information_schema.columns WHERE table_schema='%DB%' ORDER BY TABLE_NAME,COLUMN_NAME",
        "SELECT CONCAT(TABLE_NAME,'|',INDEX_NAME,'|',NON_UNIQUE,'|',SEQ_IN_INDEX,'|',COLUMN_NAME,'|',INDEX_TYPE) FROM information_schema.statistics WHERE table_schema='%DB%' ORDER BY TABLE_NAME,INDEX_NAME,SEQ_IN_INDEX",
        "SELECT CONCAT(k.TABLE_NAME,'|',k.CONSTRAINT_NAME,'|',k.COLUMN_NAME,'|',k.REFERENCED_TABLE_NAME,'|',k.REFERENCED_COLUMN_NAME) FROM information_schema.key_column_usage k WHERE k.table_schema='%DB%' AND k.REFERENCED_TABLE_NAME IS NOT NULL ORDER BY k.TABLE_NAME,k.CONSTRAINT_NAME,k.ORDINAL_POSITION",
        "SELECT CONCAT(tc.TABLE_NAME,'|',tc.CONSTRAINT_NAME,'|',cc.CHECK_CLAUSE) FROM information_schema.table_constraints tc JOIN information_schema.check_constraints cc ON cc.CONSTRAINT_SCHEMA=tc.CONSTRAINT_SCHEMA AND cc.CONSTRAINT_NAME=tc.CONSTRAINT_NAME WHERE tc.CONSTRAINT_SCHEMA='%DB%' AND tc.CONSTRAINT_TYPE='CHECK' ORDER BY tc.TABLE_NAME,tc.CONSTRAINT_NAME",
        "SELECT TABLE_NAME FROM information_schema.tables WHERE table_schema='%DB%' AND table_type='BASE TABLE' ORDER BY TABLE_NAME",
        "SELECT TRIGGER_NAME,EVENT_MANIPULATION,EVENT_OBJECT_TABLE,ACTION_TIMING FROM information_schema.triggers WHERE trigger_schema='%DB%' ORDER BY EVENT_OBJECT_TABLE,TRIGGER_NAME"
    )

    for ($index = 0; $index -lt $queries.Count; $index++) {
        $canonical = Invoke-MySql '' $queries[$index].Replace('%DB%', $canonicalDatabase)
        $upgraded = Invoke-MySql '' $queries[$index].Replace('%DB%', $upgradeDatabase)
        if (($canonical -join "`n") -ne ($upgraded -join "`n")) {
            throw "Canonical and migrated schemas differ in metadata check $index."
        }
    }

    Write-Output 'PASS: fresh canonical schema and base+V002+V003+V004 have equivalent columns, indexes, foreign keys, checks, and tables.'
    Write-Output "PASS: upgrade preserved historical movement/stock fingerprint $after."
    Write-Output 'PASS: MySQL 8.4 disposable database only; no persistent database or volume used.'
}
finally {
    if ($containerStarted) { docker stop $containerName | Out-Null }
}

param([switch]$RunPlaywright, [switch]$RunPerformance, [switch]$SkipConcurrency, [string]$PlaywrightGrep = '')

$ErrorActionPreference = 'Stop'

$containerName = 'tottus-mysql-test-' + [guid]::NewGuid().ToString('N').Substring(0, 10)
$temporaryPassword = 'Disposable-' + [guid]::NewGuid().ToString('N')
$backendRoot = Split-Path -Parent $PSScriptRoot
$repositoryRoot = Split-Path -Parent $backendRoot
$containerStarted = $false
$backendProcess = $null

try {
    docker run -d --rm --name $containerName `
        -e "MYSQL_ROOT_PASSWORD=$temporaryPassword" `
        -e 'MYSQL_DATABASE=inv_tottus_db' `
        --tmpfs '/var/lib/mysql:rw,noexec,nosuid,size=1g' `
        -p '127.0.0.1::3306' mysql:8.4 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo iniciar MySQL temporal.' }
    $containerStarted = $true

    $hostPort = (docker port $containerName '3306/tcp').Split(':')[-1]
    $ready = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        $previousErrorPreference = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        try {
            $authCheck = docker exec -e "MYSQL_PWD=$temporaryPassword" $containerName mysql -uroot -N -e 'SELECT 1' 2>$null
            if ($LASTEXITCODE -eq 0 -and ($authCheck -join '').Trim() -eq '1') { $ready = $true; break }
        }
        finally { $ErrorActionPreference = $previousErrorPreference }
        Start-Sleep -Seconds 2
    }
    if (-not $ready) { throw 'MySQL temporal no quedÃ³ listo.' }

$schema = Join-Path $backendRoot 'scripts\01_schema_inv_tottus_db.sql'
Get-Content -Raw $schema | docker exec -i -e "MYSQL_PWD=$temporaryPassword" $containerName mysql -uroot
if ($LASTEXITCODE -ne 0) { throw 'Failed to apply the canonical schema in disposable MySQL.' }

$transferMigration = Join-Path $backendRoot 'scripts\migrations\V005__traslados_y_trazabilidad.sql'
Get-Content -Raw $transferMigration | docker exec -i -e "MYSQL_PWD=$temporaryPassword" $containerName mysql -uroot inv_tottus_db
if ($LASTEXITCODE -ne 0) { throw 'Failed to apply V005 in disposable MySQL.' }

$userMigration = Join-Path $backendRoot 'scripts\migrations\V006__usuarios_multisede_y_credenciales.sql'
Get-Content -Raw $userMigration | docker exec -i -e "MYSQL_PWD=$temporaryPassword" $containerName mysql -uroot inv_tottus_db
if ($LASTEXITCODE -ne 0) { throw 'Failed to apply V006 in disposable MySQL.' }

    Push-Location $backendRoot
    try {
        & .\mvnw.cmd '-Dmaven.repo.local=C:\Users\jeamp\.m2\repository' '-DskipTests' package
        if ($LASTEXITCODE -ne 0) { throw 'No se pudo empaquetar Spring Boot para el perfil de prueba.' }
    }
    finally { Pop-Location }

    $jdbcUrl = "jdbc:mysql://127.0.0.1:$hostPort/inv_tottus_db?useSSL=false&serverTimezone=America/Lima&allowPublicKeyRetrieval=true&characterEncoding=UTF-8"
    $env:TOTTUS_TEST_JDBC_URL = $jdbcUrl
    $env:TOTTUS_TEST_DB_USERNAME = 'root'
    $env:TOTTUS_TEST_DB_PASSWORD = $temporaryPassword

    if ($RunPlaywright -or $RunPerformance) {
        $env:ADMIN_INITIAL_PASSWORD = 'Temporary-' + [guid]::NewGuid().ToString('N') + '!'
        $env:INVENTORY_MANAGER_INITIAL_PASSWORD = 'Temporary-' + [guid]::NewGuid().ToString('N') + '!'
        $env:SPRING_DATASOURCE_URL = $jdbcUrl
        $env:SPRING_DATASOURCE_USERNAME = 'root'
        $env:SPRING_DATASOURCE_PASSWORD = $temporaryPassword
        $env:JWT_SECRET = 'MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY='
        $env:SPRING_PROFILES_ACTIVE = 'local'
        $env:SERVER_PORT = '18080'
        $env:APP_CORS_ALLOWED_ORIGINS = 'http://localhost:44200'
        $jarPath = Join-Path $backendRoot 'target\tottus-inventario-0.0.1-SNAPSHOT.jar'
        $javaPath = (Get-Command java.exe).Source
        $backendProcess = Start-Process -FilePath $javaPath -ArgumentList @('-jar', $jarPath) -WorkingDirectory $backendRoot -WindowStyle Hidden -PassThru
        $serverReady = $false
        for ($attempt = 0; $attempt -lt 90; $attempt++) {
            if ($backendProcess.HasExited) { throw 'El backend aislado terminÃ³ durante el arranque.' }
            try {
                $null = Invoke-WebRequest -Uri 'http://127.0.0.1:18080/api/inventario' -TimeoutSec 2
                $serverReady = $true; break
            } catch {
                if ($_.Exception.Response -and [int]$_.Exception.Response.StatusCode -eq 401) { $serverReady = $true; break }
                Start-Sleep -Seconds 2
            }
        }
        if (-not $serverReady) { throw 'El backend aislado no respondiÃ³ a tiempo.' }
        Remove-Item Env:\SPRING_PROFILES_ACTIVE -ErrorAction SilentlyContinue
        # This disposable E2E database starts with a test administrator whose initial password was already exercised by this script.
        "UPDATE usuario SET cambio_password_requerido=FALSE WHERE usuario='admin.megaplaza';" |
            docker exec -i -e "MYSQL_PWD=$temporaryPassword" $containerName mysql -uroot inv_tottus_db
        if ($LASTEXITCODE -ne 0) { throw 'Could not prepare the isolated smoke account.' }
    }

    if (-not $SkipConcurrency) {
        Push-Location $backendRoot
        try {
            $previousErrorPreference = $ErrorActionPreference
            $ErrorActionPreference = 'Continue'
            try {
                & .\mvnw.cmd `
                    '-Dmaven.repo.local=C:\Users\jeamp\.m2\repository' `
                    '-Dtottus.mysql.integration=true' `
                    '-Dtest=OperacionConcurrencyMySqlTest' test 2>&1 | ForEach-Object { Write-Output $_ }
                $mavenExitCode = $LASTEXITCODE
            }
            finally { $ErrorActionPreference = $previousErrorPreference }
            if ($mavenExitCode -ne 0) { throw "Pruebas MySQL fallidas (Maven $mavenExitCode)." }
        }
        finally { Pop-Location }
    }

    if ($RunPlaywright -or $RunPerformance) {
        $env:TOTTUS_E2E_USERNAME = 'admin.megaplaza'
        $env:TOTTUS_E2E_PASSWORD = $env:ADMIN_INITIAL_PASSWORD
    }

    if ($RunPlaywright) {
        Push-Location (Join-Path $repositoryRoot 'frontend')
        try {
            $previousErrorPreference = $ErrorActionPreference
            $ErrorActionPreference = 'Continue'
            try {
                $playwrightArgs = @('run', 'test:integration')
                if (-not [string]::IsNullOrWhiteSpace($PlaywrightGrep)) {
                    $playwrightArgs += @('--', '--grep', $PlaywrightGrep)
                }
                & npm.cmd @playwrightArgs 2>&1 | ForEach-Object { Write-Output $_ }
                $npmExitCode = $LASTEXITCODE
            }
            finally { $ErrorActionPreference = $previousErrorPreference }
            if ($npmExitCode -ne 0) { throw "Playwright fallÃ³ (npm $npmExitCode)." }
        }
        finally { Pop-Location }
    }

    if ($RunPerformance) {
        $perfComputer = Get-CimInstance Win32_ComputerSystem
        $perfProcessor = Get-CimInstance Win32_Processor | Select-Object -First 1
        $perfJavaVersion = (& java.exe -version 2>&1 | Select-Object -First 1).ToString()
        $env:TOTTUS_PERF_JAVA_VERSION = $perfJavaVersion
        $env:TOTTUS_PERF_OS = [System.Runtime.InteropServices.RuntimeInformation]::OSDescription
        $env:TOTTUS_PERF_MACHINE = "$($perfProcessor.Name); $($perfProcessor.NumberOfCores) cores/$($perfProcessor.NumberOfLogicalProcessors) threads; $([math]::Round($perfComputer.TotalPhysicalMemory / 1GB, 1)) GiB RAM"
        Push-Location (Join-Path $repositoryRoot 'frontend')
        try {
            & node .\tests\performance\kardex-load.mjs
            if ($LASTEXITCODE -ne 0) { throw "MediciÃ³n HU20 fallÃ³ el objetivo de referencia (Node $LASTEXITCODE)." }
        }
        finally { Pop-Location }
    }
}
finally {
    Remove-Item Env:\TOTTUS_TEST_JDBC_URL -ErrorAction SilentlyContinue
    Remove-Item Env:\TOTTUS_TEST_DB_USERNAME -ErrorAction SilentlyContinue
    Remove-Item Env:\TOTTUS_TEST_DB_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:\SPRING_DATASOURCE_URL -ErrorAction SilentlyContinue
    Remove-Item Env:\SPRING_DATASOURCE_USERNAME -ErrorAction SilentlyContinue
    Remove-Item Env:\SPRING_DATASOURCE_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:\JWT_SECRET -ErrorAction SilentlyContinue
    Remove-Item Env:\SPRING_PROFILES_ACTIVE -ErrorAction SilentlyContinue
    Remove-Item Env:\SERVER_PORT -ErrorAction SilentlyContinue
    Remove-Item Env:\APP_CORS_ALLOWED_ORIGINS -ErrorAction SilentlyContinue
    Remove-Item Env:\TOTTUS_E2E_USERNAME -ErrorAction SilentlyContinue
    Remove-Item Env:\TOTTUS_E2E_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:\ADMIN_INITIAL_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:\INVENTORY_MANAGER_INITIAL_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:\TOTTUS_PERF_JAVA_VERSION -ErrorAction SilentlyContinue
    Remove-Item Env:\TOTTUS_PERF_OS -ErrorAction SilentlyContinue
    Remove-Item Env:\TOTTUS_PERF_MACHINE -ErrorAction SilentlyContinue
    if ($backendProcess -and -not $backendProcess.HasExited) { Stop-Process -Id $backendProcess.Id -Force }
    if ($containerStarted) { docker stop $containerName | Out-Null }
}

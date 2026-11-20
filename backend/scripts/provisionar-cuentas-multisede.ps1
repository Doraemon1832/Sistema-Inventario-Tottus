$ErrorActionPreference = 'Stop'
$baseUrl = 'http://localhost:8080'
$vaultDir = Join-Path $env:LOCALAPPDATA 'Tottus'
$vaultPath = Join-Path $vaultDir 'initial-credentials.secure'
New-Item -ItemType Directory -Path $vaultDir -Force | Out-Null
if (Test-Path -LiteralPath $vaultPath) {
    $existingEncrypted = (Get-Content -LiteralPath $vaultPath -Raw).Trim()
    try {
        $existingSecure = $existingEncrypted | ConvertTo-SecureString
        $existingPtr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($existingSecure)
        try { $existingJson = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($existingPtr) }
        finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($existingPtr); $existingSecure.Dispose() }
        $existingCredentials = ConvertFrom-Json -InputObject $existingJson
    }
    catch { throw 'Credential vault exists but cannot be opened by this Windows account; provisioning was not started.' }
    finally { $existingEncrypted = $null; $existingJson = $null }
    if (@($existingCredentials | Where-Object usuario -eq 'gerencia_admin_global').Count -gt 0) {
        Write-Output 'Administrator credentials already exist in the protected vault. No provisioning or database changes were made.'
        return
    }
    throw 'A credential vault already exists without the global administrator entry; inspect it before provisioning. The vault was not overwritten.'
}

function New-RandomSecret([int]$Length = 28) {
    $alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%*-_+'
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $bytes = [byte[]]::new($Length * 4)
    $rng.GetBytes($bytes)
    $chars = [char[]]::new($Length)
    for ($i = 0; $i -lt $Length; $i++) {
        $index = [int]([BitConverter]::ToUInt32($bytes, $i * 4) % [uint32]$alphabet.Length)
        $chars[$i] = $alphabet[$index]
    }
    [Array]::Clear($bytes, 0, $bytes.Length)
    $rng.Dispose()
    return -join $chars
}

$vaultEntries = [System.Collections.Generic.List[object]]::new()
function Save-CredentialVault {
    $json = ConvertTo-Json -InputObject @($vaultEntries) -Depth 4 -Compress
    $protected = ConvertFrom-SecureString (ConvertTo-SecureString -String $json -AsPlainText -Force)
    Set-Content -LiteralPath $vaultPath -Value $protected -Encoding ascii
    $json = $null
    $protected = $null
}

$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$randomBytes = [byte[]]::new(48)
$rng.GetBytes($randomBytes)
$bootstrapToken = [Convert]::ToBase64String($randomBytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
[Array]::Clear($randomBytes, 0, $randomBytes.Length)
$rng.Dispose()
$adminTemporaryPassword = New-RandomSecret 32
$adminPassword = New-RandomSecret 32
$vaultEntries.Add([pscustomobject]@{
    usuario = 'gerencia_admin_global'; rol = 'ADMINISTRADOR'; sede = 'GLOBAL (4 sedes)'; passwordInicial = $adminPassword
})
Save-CredentialVault

$env:APP_SECURITY_GLOBAL_ADMIN_BOOTSTRAP_TOKEN = $bootstrapToken
$createdCount = 0
$loginChecks = 0
$crossSiteResult = 'no comprobado'
try {
    docker compose up -d --no-deps --force-recreate backend | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'backend-start' }
    Add-Type -AssemblyName System.Net.Http
    $httpClient = [System.Net.Http.HttpClient]::new()
    $httpClient.Timeout = [TimeSpan]::FromSeconds(3)
    $ready = $false
    for ($attempt = 0; $attempt -lt 40; $attempt++) {
        try {
            $probe = $httpClient.GetAsync("$baseUrl/api/sedes").GetAwaiter().GetResult()
            if ([int]$probe.StatusCode -ge 200 -and [int]$probe.StatusCode -lt 500) { $ready = $true }
            $probe.Dispose()
        } catch { $ready = $false }
        if ($ready) { break }
        Start-Sleep -Milliseconds 500
    }
    $httpClient.Dispose()
    if (-not $ready) { throw 'backend-not-ready' }
    $boot = Invoke-RestMethod -Uri "$baseUrl/api/admin/bootstrap/global" -Method Post -TimeoutSec 20 `
        -Headers @{ 'X-Global-Admin-Bootstrap' = $bootstrapToken } -ContentType 'application/json' `
        -Body (ConvertTo-Json @{ passwordInicial = $adminTemporaryPassword } -Compress)
    if ($boot.usuario -ne 'gerencia_admin_global' -or -not $boot.accesoGlobal -or -not $boot.cambioPasswordRequerido) { throw 'bootstrap-state' }
    Write-Output 'Administrador General creado; comprobando primer acceso.'

    $login = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method Post -TimeoutSec 20 -ContentType 'application/json' `
        -Body (ConvertTo-Json @{ usuario = 'gerencia_admin_global'; password = $adminTemporaryPassword } -Compress)
    if (-not $login.cambioPasswordRequerido) { throw 'admin-password-change-flag' }
    Invoke-RestMethod -Uri "$baseUrl/api/auth/change-password" -Method Post -TimeoutSec 20 `
        -Headers @{ Authorization = "Bearer $($login.token)" } -ContentType 'application/json' `
        -Body (ConvertTo-Json @{ currentPassword = $adminTemporaryPassword; newPassword = $adminPassword } -Compress) | Out-Null
    $globalLogin = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method Post -TimeoutSec 20 -ContentType 'application/json' `
        -Body (ConvertTo-Json @{ usuario = 'gerencia_admin_global'; password = $adminPassword } -Compress)
    if ($globalLogin.cambioPasswordRequerido -or -not $globalLogin.accesoGlobal -or @($globalLogin.idSedesAutorizadas).Count -ne 4) { throw 'admin-scope' }
    $adminHeaders = @{ Authorization = "Bearer $($globalLogin.token)" }
    $existing = Invoke-RestMethod -Uri "$baseUrl/api/admin/usuarios" -Headers $adminHeaders -Method Get -TimeoutSec 20

    $sites = @(
        @{ id = 2; slug = 'jockeyplaza'; name = 'Tottus Jockey Plaza' },
        @{ id = 3; slug = 'sanmiguel'; name = 'Tottus San Miguel' },
        @{ id = 4; slug = 'miraflores'; name = 'Tottus Miraflores' }
    )
    $roles = @(
        @{ prefix = 'admin'; name = 'ADMINISTRADOR' },
        @{ prefix = 'jinv'; name = 'JEFE_INVENTARIO' },
        @{ prefix = 'alm'; name = 'OPERADOR_ALMACEN' },
        @{ prefix = 'ven'; name = 'VENDEDOR' },
        @{ prefix = 'compras'; name = 'ENCARGADO_COMPRAS' }
    )
    foreach ($site in $sites) {
        foreach ($role in $roles) {
            $username = "$($role.prefix).$($site.slug)"
            if (@($existing | Where-Object usuario -eq $username).Count -gt 0) { continue }
            $password = New-RandomSecret 28
            $payload = ConvertTo-Json @{ usuario = $username; rol = $role.name; idSedes = @([int]$site.id); passwordInicial = $password; accesoGlobal = $false } -Compress
            Invoke-RestMethod -Uri "$baseUrl/api/admin/usuarios" -Method Post -TimeoutSec 20 -Headers $adminHeaders `
                -ContentType 'application/json' -Body $payload | Out-Null
            $vaultEntries.Add([pscustomobject]@{ usuario = $username; rol = $role.name; sede = $site.name; passwordInicial = $password })
            Save-CredentialVault
            $createdCount++
        }
    }

    $loginChecks = 0
    foreach ($entry in @($vaultEntries | Where-Object { $_.usuario -ne 'gerencia_admin_global' })) {
        $response = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method Post -TimeoutSec 20 -ContentType 'application/json' `
            -Body (ConvertTo-Json @{ usuario = $entry.usuario; password = $entry.passwordInicial } -Compress)
        if (-not $response.cambioPasswordRequerido -or $response.accesoGlobal -or @($response.idSedesAutorizadas).Count -ne 1) { throw 'account-login-scope' }
        $loginChecks++
    }
    $authorizedSites = Invoke-RestMethod -Uri "$baseUrl/api/sedes" -Headers $adminHeaders -Method Get -TimeoutSec 20
    if (@($authorizedSites).Count -ne 4) { throw 'global-site-list' }
    $seller = $vaultEntries | Where-Object usuario -eq 'ven.jockeyplaza' | Select-Object -First 1
    $sellerLogin = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method Post -TimeoutSec 20 -ContentType 'application/json' `
        -Body (ConvertTo-Json @{ usuario = $seller.usuario; password = $seller.passwordInicial } -Compress)
    try {
        Invoke-RestMethod -Uri "$baseUrl/api/inventario?sedeId=1" -Headers @{ Authorization = "Bearer $($sellerLogin.token)" } `
            -Method Get -TimeoutSec 20 | Out-Null
        throw 'cross-site-read-allowed'
    } catch {
        if ($_.Exception.Response -and [int]$_.Exception.Response.StatusCode -eq 403) { $crossSiteResult = 'denegado (403)' }
        elseif ($_.Exception.Message -eq 'cross-site-read-allowed') { throw }
        else { throw 'cross-site-check-failed' }
    }
} catch {
    throw "La provisión se detuvo en una validación segura: $($_.Exception.Message)"
} finally {
    $env:APP_SECURITY_GLOBAL_ADMIN_BOOTSTRAP_TOKEN = ''
    docker compose up -d --no-deps --force-recreate backend | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo retirar el token temporal del backend' }
    $bootstrapToken = $null; $adminTemporaryPassword = $null; $adminPassword = $null
    $login = $null; $globalLogin = $null; $adminHeaders = $null; $sellerLogin = $null
}

[pscustomobject]@{
    AdministradorGeneral = 'creado y probado; token temporal retirado'
    UsuariosNuevos = $createdCount
    IniciosDeSesionVerificados = $loginChecks
    AccesoEntreSedes = $crossSiteResult
    BovedaCifrada = $vaultPath
} | Format-List

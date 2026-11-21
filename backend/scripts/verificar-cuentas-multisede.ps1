$ErrorActionPreference = 'Stop'
$vaultPath = Join-Path $env:LOCALAPPDATA 'Tottus\initial-credentials.secure'
$encrypted = (Get-Content -LiteralPath $vaultPath -Raw).Trim()
$secure = $encrypted | ConvertTo-SecureString
$ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try {
    $json = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
    $entries = ConvertFrom-Json -InputObject $json
}
finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
    $secure.Dispose()
    $json = $null
    $encrypted = $null
}

$verifiedLogins = 0
foreach ($entry in $entries) {
    $login = Invoke-RestMethod -Uri 'http://localhost:8080/api/auth/login' -Method Post -TimeoutSec 20 `
        -ContentType 'application/json' -Body (ConvertTo-Json @{ usuario = $entry.usuario; password = $entry.passwordInicial } -Compress)
    if ($entry.usuario -eq 'gerencia_admin_global') {
        if ($login.cambioPasswordRequerido -or -not $login.accesoGlobal -or @($login.idSedesAutorizadas).Count -ne 4) { throw 'Global admin verification failed' }
    }
    elseif ($entry.usuario -eq 'ven.jockeyplaza') {
        if ($login.accesoGlobal -or @($login.idSedesAutorizadas).Count -ne 1) { throw 'Seller site scope verification failed' }
    }
    elseif (-not $login.cambioPasswordRequerido -or $login.accesoGlobal -or @($login.idSedesAutorizadas).Count -ne 1) {
        throw 'Initial account verification failed'
    }
    $verifiedLogins++
}

$seller = $entries | Where-Object usuario -eq 'ven.jockeyplaza' | Select-Object -First 1
$sellerLogin = Invoke-RestMethod -Uri 'http://localhost:8080/api/auth/login' -Method Post -TimeoutSec 20 `
    -ContentType 'application/json' -Body (ConvertTo-Json @{ usuario = $seller.usuario; password = $seller.passwordInicial } -Compress)
if ($sellerLogin.cambioPasswordRequerido) {
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $randomBytes = [byte[]]::new(128)
    $rng.GetBytes($randomBytes)
    $alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%*-_+'
    $newChars = [char[]]::new(32)
    for ($i = 0; $i -lt 32; $i++) { $newChars[$i] = $alphabet[[int]([BitConverter]::ToUInt32($randomBytes, $i * 4) % [uint32]$alphabet.Length)] }
    $newPassword = -join $newChars
    [Array]::Clear($randomBytes, 0, $randomBytes.Length)
    $rng.Dispose()
    Invoke-RestMethod -Uri 'http://localhost:8080/api/auth/change-password' -Method Post -TimeoutSec 20 `
        -Headers @{ Authorization = "Bearer $($sellerLogin.token)" } -ContentType 'application/json' `
        -Body (ConvertTo-Json @{ currentPassword = $seller.passwordInicial; newPassword = $newPassword } -Compress) | Out-Null
    $seller.passwordInicial = $newPassword
    $sellerLogin = $null
    $json = ConvertTo-Json -InputObject @($entries) -Depth 4 -Compress
    $protected = ConvertFrom-SecureString (ConvertTo-SecureString -String $json -AsPlainText -Force)
    Set-Content -LiteralPath $vaultPath -Value $protected -Encoding ascii
    $json = $null; $protected = $null; $newPassword = $null
    $sellerLogin = Invoke-RestMethod -Uri 'http://localhost:8080/api/auth/login' -Method Post -TimeoutSec 20 `
        -ContentType 'application/json' -Body (ConvertTo-Json @{ usuario = $seller.usuario; password = $seller.passwordInicial } -Compress)
}
$headers = @{ Authorization = "Bearer $($sellerLogin.token)" }
$ownSiteStatus = 0
try {
    $ownResponse = Invoke-WebRequest -Uri 'http://localhost:8080/api/inventario?sedeId=2' -Headers $headers -Method Get -TimeoutSec 20 -UseBasicParsing
    $ownSiteStatus = [int]$ownResponse.StatusCode
}
catch { if ($_.Exception.Response) { $ownSiteStatus = [int]$_.Exception.Response.StatusCode } else { throw 'Authorized-site request failed' } }
$otherSiteStatus = 0
try {
    $otherResponse = Invoke-WebRequest -Uri 'http://localhost:8080/api/inventario?sedeId=1' -Headers $headers -Method Get -TimeoutSec 20 -UseBasicParsing
    $otherSiteStatus = [int]$otherResponse.StatusCode
}
catch { if ($_.Exception.Response) { $otherSiteStatus = [int]$_.Exception.Response.StatusCode } else { throw 'Cross-site request failed' } }

[pscustomobject]@{
    LoginsVerificados = $verifiedLogins
    EstadoInicialObligatorio = 'confirmado para las cuentas nuevas'
    ConsultaSedeAutorizada = $ownSiteStatus
    ConsultaSedeNoAutorizada = $otherSiteStatus
} | Format-List

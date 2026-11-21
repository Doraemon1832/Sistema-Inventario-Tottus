param(
    [string]$VaultPath = (Join-Path $env:LOCALAPPDATA 'Tottus\initial-credentials.secure')
)

$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath $VaultPath)) {
    throw "No existe el archivo protegido de credenciales: $VaultPath"
}

$encrypted = (Get-Content -LiteralPath $VaultPath -Raw).Trim()
$secure = $encrypted | ConvertTo-SecureString
$ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try {
    $json = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
    $entries = ConvertFrom-Json -InputObject $json
    $entries | Select-Object usuario, rol, sede, passwordInicial | Format-Table -AutoSize
}
finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
    $json = $null
    $secure.Dispose()
}

param(
    [int]$Port = 8080
)

$ErrorActionPreference = "Stop"

$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$JarPath = Join-Path $ProjectRoot "backend\target\middleware-resource-manager-0.0.1-SNAPSHOT-exec.jar"
$RunnerPath = Join-Path $PSScriptRoot "run-local-backend-jar.ps1"
$OutLogPath = Join-Path $ProjectRoot "backend-local.out.log"
$ErrLogPath = Join-Path $ProjectRoot "backend-local.err.log"

if (-not (Test-Path -LiteralPath $JarPath)) {
    throw "Backend jar not found: $JarPath"
}

$listener = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue
if ($listener) {
    Write-Host "Backend port $Port is already listening. PID: $($listener.OwningProcess)"
    exit 0
}

$process = Start-Process -FilePath "powershell.exe" `
    -ArgumentList @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $RunnerPath) `
    -WorkingDirectory (Join-Path $ProjectRoot 'backend') `
    -RedirectStandardOutput $OutLogPath `
    -RedirectStandardError $ErrLogPath `
    -WindowStyle Hidden `
    -PassThru

Write-Host "Backend started. Launcher PID: $($process.Id)"
Write-Host "Backend stdout log: $OutLogPath"
Write-Host "Backend stderr log: $ErrLogPath"

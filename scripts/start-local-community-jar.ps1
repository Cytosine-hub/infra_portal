param(
    [int]$Port = 8082
)

$ErrorActionPreference = "Stop"

$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$JarPath = Join-Path $ProjectRoot "backend\community-service\target\community-service-0.0.1-SNAPSHOT-exec.jar"
$RunnerPath = Join-Path $PSScriptRoot "run-local-community-jar.ps1"
$OutLogPath = Join-Path $ProjectRoot "community-local.out.log"
$ErrLogPath = Join-Path $ProjectRoot "community-local.err.log"

if (-not (Test-Path -LiteralPath $JarPath)) {
    throw "Community service jar not found: $JarPath"
}

$listener = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue
if ($listener) {
    Write-Host "Community service port $Port is already listening. PID: $($listener.OwningProcess)"
    exit 0
}

$process = Start-Process -FilePath "powershell.exe" `
    -ArgumentList @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $RunnerPath) `
    -WorkingDirectory (Join-Path $ProjectRoot 'backend') `
    -RedirectStandardOutput $OutLogPath `
    -RedirectStandardError $ErrLogPath `
    -WindowStyle Hidden `
    -PassThru

Write-Host "Community service started. Launcher PID: $($process.Id)"
Write-Host "Community stdout log: $OutLogPath"
Write-Host "Community stderr log: $ErrLogPath"

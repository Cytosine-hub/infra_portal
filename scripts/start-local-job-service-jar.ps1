param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("middleware-service", "database-service", "host-service", "network-service", "security-service")]
    [string]$ServiceName,

    [Parameter(Mandatory = $true)]
    [ValidateRange(1, 65535)]
    [int]$Port
)

$ErrorActionPreference = "Stop"

$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$JarPath = Join-Path $ProjectRoot "backend\$ServiceName\target\$ServiceName-0.0.1-SNAPSHOT-exec.jar"
$RunnerPath = Join-Path $PSScriptRoot "run-local-job-service-jar.ps1"
$OutLogPath = Join-Path $ProjectRoot "$ServiceName-local.out.log"
$ErrLogPath = Join-Path $ProjectRoot "$ServiceName-local.err.log"

if (-not (Test-Path -LiteralPath $JarPath)) {
    throw "$ServiceName jar not found: $JarPath"
}

$listener = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue
if ($listener) {
    Write-Host "$ServiceName port $Port is already listening. PID: $($listener.OwningProcess)"
    exit 0
}

$process = Start-Process -FilePath "powershell.exe" `
    -ArgumentList @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $RunnerPath, "-ServiceName", $ServiceName) `
    -WorkingDirectory (Join-Path $ProjectRoot "backend") `
    -RedirectStandardOutput $OutLogPath `
    -RedirectStandardError $ErrLogPath `
    -WindowStyle Hidden `
    -PassThru

Write-Host "$ServiceName started. Launcher PID: $($process.Id)"
Write-Host "$ServiceName stdout log: $OutLogPath"
Write-Host "$ServiceName stderr log: $ErrLogPath"

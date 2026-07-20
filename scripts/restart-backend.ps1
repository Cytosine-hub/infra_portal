# Restart Spring Boot Backend
# Usage: powershell -ExecutionPolicy Bypass -File .\scripts\restart-backend.ps1

$projectRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location (Join-Path $projectRoot 'backend')

Write-Host "==> Stopping backend on port 8080..." -ForegroundColor Yellow
$pid8080 = (netstat -ano | Select-String ':8080.*LISTENING' | ForEach-Object { ($_ -split '\s+')[-1] } | Select-Object -First 1)
if ($pid8080) {
    Stop-Process -Id $pid8080 -Force -ErrorAction SilentlyContinue
    Write-Host "    Stopped PID $pid8080" -ForegroundColor Gray
    Start-Sleep -Seconds 2
} else {
    Write-Host "    No process on port 8080" -ForegroundColor Gray
}

Write-Host "==> Starting backend..." -ForegroundColor Yellow
$env:JAVA_HOME = if (Test-Path "C:\Program Files\Java\jdk-1.8") { "C:\Program Files\Java\jdk-1.8" } else { $env:JAVA_HOME }

mvn spring-boot:run 1>backend-local.out.log 2>backend-local.err.log &
$mvnPid = $LASTPROCESSID

Write-Host "    Maven PID: $mvnPid" -ForegroundColor Gray
Write-Host "==> Waiting for backend to start (max 120s)..." -ForegroundColor Yellow

$timeout = 120
$elapsed = 0
while ($elapsed -lt $timeout) {
    Start-Sleep -Seconds 3
    $elapsed += 3
    $pid = (netstat -ano | Select-String ':8080.*LISTENING' | ForEach-Object { ($_ -split '\s+')[-1] } | Select-Object -First 1)
    if ($pid) {
        Write-Host "==> Backend started on port 8080 (PID $pid) in ${elapsed}s" -ForegroundColor Green
        Write-Host "    Logs: $projectRoot\backend-local.out.log" -ForegroundColor Gray
        Write-Host "    API:  http://localhost:8080/api/public/releases" -ForegroundColor Gray
        exit 0
    }
    Write-Host "    ... ${elapsed}s" -ForegroundColor Gray
}

Write-Host "==> ERROR: Backend failed to start within ${timeout}s" -ForegroundColor Red
Write-Host "    Check $projectRoot\backend-local.out.log" -ForegroundColor Red
exit 1

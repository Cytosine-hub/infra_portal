$ErrorActionPreference = "Stop"

$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$Services = @(
    @{ Name = "middleware-service"; Port = 8085 },
    @{ Name = "database-service"; Port = 8086 },
    @{ Name = "host-service"; Port = 8087 },
    @{ Name = "network-service"; Port = 8088 },
    @{ Name = "security-service"; Port = 8089 }
)

foreach ($service in $Services) {
    Write-Host "==> Stopping $($service.Name) on port $($service.Port)..." -ForegroundColor Yellow
    $listener = Get-NetTCPConnection -State Listen -LocalPort $service.Port -ErrorAction SilentlyContinue
    if ($listener) {
        Stop-Process -Id $listener.OwningProcess -Force -ErrorAction SilentlyContinue
    }
}

Set-Location (Join-Path $ProjectRoot "backend")
mvn -pl middleware-service,database-service,host-service,network-service,security-service -am package -DskipTests

foreach ($service in $Services) {
    & (Join-Path $PSScriptRoot "start-local-job-service-jar.ps1") `
        -ServiceName $service.Name `
        -Port $service.Port
}

Write-Host "==> Five job services restarted." -ForegroundColor Green

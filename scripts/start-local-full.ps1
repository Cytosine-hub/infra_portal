$ErrorActionPreference = "Stop"

$ScriptRoot = $PSScriptRoot

& (Join-Path $ScriptRoot "start-local-core-jar.ps1")
& (Join-Path $ScriptRoot "start-local-community-jar.ps1")
& (Join-Path $ScriptRoot "start-local-ai-jar.ps1")
& (Join-Path $ScriptRoot "start-local-job-service-jar.ps1") -ServiceName middleware-service -Port 8085
& (Join-Path $ScriptRoot "start-local-job-service-jar.ps1") -ServiceName database-service -Port 8086
& (Join-Path $ScriptRoot "start-local-job-service-jar.ps1") -ServiceName host-service -Port 8087
& (Join-Path $ScriptRoot "start-local-job-service-jar.ps1") -ServiceName network-service -Port 8088
& (Join-Path $ScriptRoot "start-local-job-service-jar.ps1") -ServiceName security-service -Port 8089
& (Join-Path $ScriptRoot "start-local-gateway-jar.ps1")
& (Join-Path $ScriptRoot "start-local-frontend-dev.ps1")

Write-Host ""
Write-Host "Local services are starting."
Write-Host "Gateway:  http://localhost:8080"
Write-Host "Community: http://localhost:8082"
Write-Host "AI:       http://localhost:8083"
Write-Host "Core:     http://localhost:8084"
Write-Host "Middleware: http://localhost:8085"
Write-Host "Database:   http://localhost:8086"
Write-Host "Host:       http://localhost:8087"
Write-Host "Network:    http://localhost:8088"
Write-Host "Security:   http://localhost:8089"
Write-Host "Frontend: http://localhost:5173"

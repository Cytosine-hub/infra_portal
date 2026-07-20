$ErrorActionPreference = "Stop"

$ScriptRoot = $PSScriptRoot

& (Join-Path $ScriptRoot "start-local-backend-jar.ps1")
& (Join-Path $ScriptRoot "start-local-community-jar.ps1")
& (Join-Path $ScriptRoot "start-local-gateway-jar.ps1")
& (Join-Path $ScriptRoot "start-local-frontend-dev.ps1")

Write-Host ""
Write-Host "Local services are starting."
Write-Host "Gateway:  http://localhost:8080"
Write-Host "Backend:  http://localhost:8081"
Write-Host "Community: http://localhost:8082"
Write-Host "Frontend: http://localhost:5173"

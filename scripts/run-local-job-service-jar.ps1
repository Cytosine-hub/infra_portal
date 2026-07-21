param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("middleware-service", "database-service", "host-service", "network-service", "security-service")]
    [string]$ServiceName
)

$ErrorActionPreference = "Stop"

$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$JarPath = Join-Path $ProjectRoot "backend\$ServiceName\target\$ServiceName-0.0.1-SNAPSHOT-exec.jar"

Set-Location -LiteralPath (Join-Path $ProjectRoot "backend")
java -jar $JarPath

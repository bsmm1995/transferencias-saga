<#
.SYNOPSIS
  Builds the four services and starts them as background Java processes (logs in ./logs).
  Requires the infrastructure from docker-compose.yml to be running.
#>
param(
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$logs = Join-Path $root 'logs'
New-Item -ItemType Directory -Force $logs | Out-Null

if (-not $SkipBuild) {
    & (Join-Path $root 'gradlew.bat') -p $root assemble -x test --quiet
    if ($LASTEXITCODE -ne 0) { throw 'Build failed' }
}

# Local credentials match docker-compose.yml; real environments inject them from a secret store.
$env:DB_PASSWORD = 'transfers'
$env:SWAGGER_UI_ENABLED = 'true'

$services = 'core-banking-mock', 'fraud-mock', 'payment-connector-mock', 'transfer-service'
foreach ($service in $services) {
    $jar = Join-Path $root "services/$service/build/quarkus-app/quarkus-run.jar"
    if (-not (Test-Path $jar)) { throw "Missing $jar, run without -SkipBuild" }
    $process = Start-Process -FilePath 'java' -ArgumentList '-jar', "`"$jar`"" `
        -RedirectStandardOutput (Join-Path $logs "$service.log") `
        -RedirectStandardError (Join-Path $logs "$service.err.log") `
        -WindowStyle Hidden -PassThru
    Set-Content -Path (Join-Path $logs "$service.pid") -Value $process.Id
    Write-Host "Started $service (pid $($process.Id))"
}

$ports = @{ 'transfer-service' = 8080; 'core-banking-mock' = 8081; 'fraud-mock' = 8082; 'payment-connector-mock' = 8083 }
foreach ($service in $services) {
    $url = "http://localhost:$($ports[$service])/q/health/ready"
    $deadline = (Get-Date).AddSeconds(60)
    while ($true) {
        try {
            Invoke-RestMethod -Uri $url -TimeoutSec 2 | Out-Null
            Write-Host "$service is ready on port $($ports[$service])"
            break
        } catch {
            if ((Get-Date) -gt $deadline) { throw "$service did not become ready, see logs/$service.log" }
            Start-Sleep -Milliseconds 500
        }
    }
}

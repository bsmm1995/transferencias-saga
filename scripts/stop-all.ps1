<#
.SYNOPSIS
  Stops the services started by start-all.ps1.
#>
$root = Split-Path -Parent $PSScriptRoot
Get-ChildItem -Path (Join-Path $root 'logs') -Filter '*.pid' -ErrorAction SilentlyContinue | ForEach-Object {
    $processId = [int](Get-Content $_.FullName)
    try {
        Stop-Process -Id $processId -Force -ErrorAction Stop
        Write-Host "Stopped $($_.BaseName) (pid $processId)"
    } catch {
        Write-Host "$($_.BaseName) was not running"
    }
    Remove-Item $_.FullName
}

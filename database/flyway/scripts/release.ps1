[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [ValidateSet('dev', 'test', 'preprod', 'prod')]
    [string]$Environment = 'dev'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$scriptRoot = Split-Path -Parent $PSCommandPath
$environmentCheck = Join-Path $scriptRoot 'check-environment.ps1'
$flywayScript = Join-Path $scriptRoot 'flyway.ps1'

Write-Host 'Release stage 1/3: pre-migration policy checks'
& $environmentCheck -Environment $Environment -Phase pre

Write-Host 'Release stage 2/3: Flyway migrate'
& $flywayScript -Command migrate -Environment $Environment

Write-Host 'Release stage 3/3: strict post-migration validation'
& $environmentCheck -Environment $Environment -Phase post

Write-Host 'Flyway release flow completed successfully.'

param(
    [Parameter(Mandatory = $true)][string]$Flyway,
    [Parameter(Mandatory = $true)][string]$FreshUrl,
    [Parameter(Mandatory = $true)][string]$UpgradeUrl,
    [Parameter(Mandatory = $true)][string]$User,
    [Parameter(Mandatory = $true)][string]$Password
)

$ErrorActionPreference = 'Stop'
$migrationRoot = (Resolve-Path "$PSScriptRoot\..\migration\mysql").Path

function Assert-EphemeralLocalUrl([string]$url, [string]$label) {
    if ($url -notmatch '^jdbc:mysql://(127\.0\.0\.1|localhost):\d+/[A-Za-z0-9_]+') {
        throw "$label must be an explicit loopback-only MySQL schema"
    }
    if ($url -match '(prod|production|preprod|uat)') {
        throw "$label must not reference a managed or release environment"
    }
}

function Invoke-Flyway([string]$url, [string]$target, [string]$command) {
    & $Flyway "-url=$url" "-user=$User" "-password=$Password" `
        "-locations=filesystem:$migrationRoot" "-baselineOnMigrate=true" `
        "-baselineVersion=2.0.0" "-target=$target" "-validateMigrationNaming=true" $command
    if ($LASTEXITCODE -ne 0) { throw "Flyway $command failed for isolated validation schema" }
}

Assert-EphemeralLocalUrl $FreshUrl 'FreshUrl'
Assert-EphemeralLocalUrl $UpgradeUrl 'UpgradeUrl'
if ($FreshUrl -eq $UpgradeUrl) { throw 'Fresh and Upgrade schemas must be isolated' }

# Fresh path: complete governed chain, strict validate, then a no-op migrate.
Invoke-Flyway $FreshUrl '2.6.13' 'migrate'
Invoke-Flyway $FreshUrl '2.6.13' 'validate'
Invoke-Flyway $FreshUrl '2.6.13' 'migrate'
Invoke-Flyway $FreshUrl '2.6.13' 'info'

# Upgrade path: establish V2.6.12 first, then execute only V2.6.13.
Invoke-Flyway $UpgradeUrl '2.6.12' 'migrate'
Invoke-Flyway $UpgradeUrl '2.6.12' 'validate'
Invoke-Flyway $UpgradeUrl '2.6.13' 'migrate'
Invoke-Flyway $UpgradeUrl '2.6.13' 'validate'
Invoke-Flyway $UpgradeUrl '2.6.13' 'migrate'
Invoke-Flyway $UpgradeUrl '2.6.13' 'info'

Write-Host 'V2.6.13 candidate validation commands completed; compare schema fingerprints separately.'

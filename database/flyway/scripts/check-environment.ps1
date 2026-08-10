[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [ValidateSet('dev', 'test', 'preprod', 'prod')]
    [string]$Environment = 'dev',

    [Parameter(Position = 1)]
    [ValidateSet('pre', 'post')]
    [string]$Phase = 'pre'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)

function Require-EnvironmentVariable {
    param([Parameter(Mandatory = $true)][string]$Name)

    $value = [Environment]::GetEnvironmentVariable($Name)
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "Required environment variable '$Name' is not set."
    }
    return $value
}

function Invoke-MySqlScalar {
    param(
        [Parameter(Mandatory = $true)][string]$DefaultsFile,
        [Parameter(Mandatory = $true)][string]$Database,
        [Parameter(Mandatory = $true)][string]$Query
    )

    $result = & mysql "--defaults-extra-file=$DefaultsFile" '--batch' '--raw' '--skip-column-names' "--database=$Database" "--execute=$Query"
    if ($LASTEXITCODE -ne 0) {
        throw "MySQL read-only query failed with exit code $LASTEXITCODE."
    }
    return (($result | Select-Object -First 1) -as [string]).Trim()
}

$scriptRoot = Split-Path -Parent $PSCommandPath
$repositoryRoot = (Resolve-Path (Join-Path $scriptRoot '..\..\..')).Path
$flywayScript = Join-Path $scriptRoot 'flyway.ps1'
$fingerprintSql = Join-Path $repositoryRoot 'database\mysql\verification\schema_fingerprint.sql'
$migrationDirectory = Join-Path $repositoryRoot 'database\migration\mysql'
$checksumManifest = Join-Path $migrationDirectory 'SHA256SUMS'

$vendor = if ([string]::IsNullOrWhiteSpace($env:FLYWAY_VENDOR)) { 'mysql' } else { $env:FLYWAY_VENDOR.ToLowerInvariant() }
if ($vendor -ne 'mysql') {
    throw "Schema fingerprint implementation is currently available only for MySQL. Vendor '$vendor' requires its reviewed adapter."
}

$database = if ([string]::IsNullOrWhiteSpace($env:DB_NAME)) { 'enterprise_platform' } else { $env:DB_NAME }
$defaultsFile = Require-EnvironmentVariable 'MYSQL_CLIENT_DEFAULTS_FILE'
if (-not (Test-Path -LiteralPath $defaultsFile -PathType Leaf)) {
    throw "MYSQL_CLIENT_DEFAULTS_FILE does not exist: $defaultsFile"
}
if (-not (Get-Command mysql -ErrorAction SilentlyContinue)) {
    throw 'The mysql client is required for version and schema-fingerprint checks.'
}

Write-Host "Environment check: $Environment"
Write-Host "Validation phase: $Phase"
Write-Host "Vendor: $vendor"
Write-Host "Database: $database"

$historyExists = Invoke-MySqlScalar -DefaultsFile $defaultsFile -Database $database -Query "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'flyway_schema_history'"
if ($historyExists -ne '1') {
    throw 'flyway_schema_history is missing. This database is unmanaged; run the approved onboarding and baseline process before validate/migrate.'
}

$currentVersion = Invoke-MySqlScalar -DefaultsFile $defaultsFile -Database $database -Query "SELECT COALESCE(version, '') FROM flyway_schema_history WHERE success = 1 AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1"
if ([string]::IsNullOrWhiteSpace($currentVersion)) {
    throw 'No successful versioned or baseline record exists in flyway_schema_history.'
}
Write-Host "Current database version: $currentVersion"

$failedHistoryCount = Invoke-MySqlScalar -DefaultsFile $defaultsFile -Database $database -Query 'SELECT COUNT(*) FROM flyway_schema_history WHERE success = 0'
if ($failedHistoryCount -ne '0') {
    throw "Flyway history contains $failedHistoryCount failed migration record(s)."
}

$expectedVersion = if ($Phase -eq 'pre') {
    if (-not [string]::IsNullOrWhiteSpace($env:EXPECTED_SOURCE_DB_VERSION)) { $env:EXPECTED_SOURCE_DB_VERSION } else { $env:EXPECTED_DB_VERSION }
}
else {
    if (-not [string]::IsNullOrWhiteSpace($env:EXPECTED_TARGET_DB_VERSION)) { $env:EXPECTED_TARGET_DB_VERSION } else { $env:EXPECTED_DB_VERSION }
}
$requiredVersionName = if ($Phase -eq 'pre') { 'EXPECTED_SOURCE_DB_VERSION' } else { 'EXPECTED_TARGET_DB_VERSION' }

if (-not [string]::IsNullOrWhiteSpace($expectedVersion) -and $currentVersion -ne $expectedVersion) {
    throw "Database version mismatch during '$Phase' check. Expected '$expectedVersion', actual '$currentVersion'."
}
if ($Environment -in @('preprod', 'prod') -and [string]::IsNullOrWhiteSpace($expectedVersion)) {
    throw "$requiredVersionName is mandatory in preprod and prod."
}

if ($Phase -eq 'pre') {
    if (-not (Test-Path -LiteralPath $checksumManifest -PathType Leaf)) {
        throw "Migration checksum manifest is missing: $checksumManifest"
    }

    $manifestFiles = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    foreach ($line in Get-Content -LiteralPath $checksumManifest -Encoding utf8) {
        if ([string]::IsNullOrWhiteSpace($line) -or $line.TrimStart().StartsWith('#')) {
            continue
        }
        if ($line -notmatch '^([0-9a-fA-F]{64})\s+\*?(.+)$') {
            throw "Invalid SHA256SUMS entry: $line"
        }
        $expectedHash = $Matches[1].ToLowerInvariant()
        $relativeName = $Matches[2].Trim()
        if ([System.IO.Path]::GetExtension($relativeName) -ne '.sql') {
            throw "Only SQL migrations may be listed in SHA256SUMS: $relativeName"
        }
        $assetPath = [System.IO.Path]::GetFullPath((Join-Path $migrationDirectory $relativeName))
        $migrationRoot = [System.IO.Path]::GetFullPath($migrationDirectory).TrimEnd('\') + '\'
        if (-not $assetPath.StartsWith($migrationRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
            throw "Checksum manifest path escapes the governed directory: $relativeName"
        }
        if (-not (Test-Path -LiteralPath $assetPath -PathType Leaf)) {
            throw "Migration listed in SHA256SUMS is missing: $relativeName"
        }
        $actualHash = (Get-FileHash -LiteralPath $assetPath -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($actualHash -ne $expectedHash) {
            throw "Migration checksum mismatch for '$relativeName'. Expected '$expectedHash', actual '$actualHash'."
        }
        if (-not $manifestFiles.Add($relativeName)) {
            throw "Duplicate SHA256SUMS entry: $relativeName"
        }
    }

    if ($manifestFiles.Count -eq 0) {
        throw 'SHA256SUMS contains no migration entries.'
    }

    $unlistedFiles = Get-ChildItem -LiteralPath $migrationDirectory -File -Filter '*.sql' |
        Where-Object { -not $manifestFiles.Contains($_.Name) }
    if ($unlistedFiles) {
        throw "Governed migration files missing from SHA256SUMS: $($unlistedFiles.Name -join ', ')"
    }

    Write-Host "Migration asset policy passed: $($manifestFiles.Count) checksum(s) verified."
    Write-Host 'Running Flyway info. Pending migrations are permitted during the pre-migration policy phase.'
    & $flywayScript -Command info -Environment $Environment
}
else {
    Write-Host 'Running strict post-migration Flyway validate and info...'
    & $flywayScript -Command validate -Environment $Environment
    & $flywayScript -Command info -Environment $Environment
}

$fingerprintInput = Get-Content -LiteralPath $fingerprintSql -Encoding utf8 -Raw | & mysql "--defaults-extra-file=$defaultsFile" '--batch' '--raw' '--skip-column-names' "--database=$database"
if ($LASTEXITCODE -ne 0) {
    throw "Schema fingerprint query failed with exit code $LASTEXITCODE."
}
$canonicalText = (($fingerprintInput | ForEach-Object { [string]$_ }) -join "`n") + "`n"
$sha256 = [System.Security.Cryptography.SHA256]::Create()
try {
    $hashBytes = $sha256.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($canonicalText))
    $fingerprint = -join ($hashBytes | ForEach-Object { $_.ToString('x2') })
}
finally {
    $sha256.Dispose()
}

Write-Host "Schema fingerprint (SHA-256): $fingerprint"

$expectedFingerprint = if ($Phase -eq 'pre') {
    if (-not [string]::IsNullOrWhiteSpace($env:EXPECTED_PRE_SCHEMA_FINGERPRINT)) { $env:EXPECTED_PRE_SCHEMA_FINGERPRINT } else { $env:EXPECTED_SCHEMA_FINGERPRINT }
}
else {
    if (-not [string]::IsNullOrWhiteSpace($env:EXPECTED_POST_SCHEMA_FINGERPRINT)) { $env:EXPECTED_POST_SCHEMA_FINGERPRINT } else { $env:EXPECTED_SCHEMA_FINGERPRINT }
}
$requiredFingerprintName = if ($Phase -eq 'pre') { 'EXPECTED_PRE_SCHEMA_FINGERPRINT' } else { 'EXPECTED_POST_SCHEMA_FINGERPRINT' }

if (-not [string]::IsNullOrWhiteSpace($expectedFingerprint) -and $fingerprint -ne $expectedFingerprint.ToLowerInvariant()) {
    throw "Schema fingerprint mismatch during '$Phase' check. Expected '$expectedFingerprint', actual '$fingerprint'."
}
if ($Environment -in @('preprod', 'prod') -and [string]::IsNullOrWhiteSpace($expectedFingerprint)) {
    throw "$requiredFingerprintName is mandatory in preprod and prod."
}

Write-Host "Environment '$Phase' validation passed. No repair or migration was executed by this check."

[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [ValidateSet('info', 'validate', 'migrate', 'baseline')]
    [string]$Command = 'validate',

    [Parameter(Position = 1)]
    [ValidateSet('dev', 'test', 'preprod', 'prod')]
    [string]$Environment = 'dev'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Require-EnvironmentVariable {
    param([Parameter(Mandatory = $true)][string]$Name)

    $value = [Environment]::GetEnvironmentVariable($Name)
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "Required environment variable '$Name' is not set."
    }
    return $value
}

$scriptRoot = Split-Path -Parent $PSCommandPath
$repositoryRoot = (Resolve-Path (Join-Path $scriptRoot '..\..\..')).Path
$vendor = if ([string]::IsNullOrWhiteSpace($env:FLYWAY_VENDOR)) { 'mysql' } else { $env:FLYWAY_VENDOR.ToLowerInvariant() }
$supportedVendors = @('mysql', 'dm', 'kingbase')
if ($vendor -notin $supportedVendors) {
    throw "Unsupported FLYWAY_VENDOR '$vendor'. Expected one of: $($supportedVendors -join ', ')."
}

$migrationDirectory = Join-Path $repositoryRoot "database\migration\$vendor"
if (-not (Test-Path -LiteralPath $migrationDirectory -PathType Container)) {
    throw "Governed migration directory does not exist: $migrationDirectory"
}

$image = Require-EnvironmentVariable 'FLYWAY_IMAGE'
[void](Require-EnvironmentVariable 'FLYWAY_URL')
[void](Require-EnvironmentVariable 'FLYWAY_USER')
[void](Require-EnvironmentVariable 'FLYWAY_PASSWORD')

$mutatingCommands = @('migrate', 'baseline')
if ($Command -in $mutatingCommands) {
    $expectedConfirmation = "${Environment}:${Command}"
    if ($env:CONFIRM_FLYWAY_MUTATION -ne $expectedConfirmation) {
        throw "Mutating command blocked. Set CONFIRM_FLYWAY_MUTATION='$expectedConfirmation' for this invocation."
    }
    if ($Environment -in @('preprod', 'prod')) {
        [void](Require-EnvironmentVariable 'CHANGE_TICKET')
    }
}

if ($Command -in @('clean', 'repair')) {
    throw "Command '$Command' is prohibited by the repository wrapper."
}

$dockerArguments = @(
    'run', '--rm',
    '--mount', "type=bind,src=$migrationDirectory,dst=/flyway/sql,readonly",
    '--env', 'FLYWAY_URL',
    '--env', 'FLYWAY_USER',
    '--env', 'FLYWAY_PASSWORD'
)

if (-not [string]::IsNullOrWhiteSpace($env:FLYWAY_DOCKER_NETWORK)) {
    $dockerArguments += @('--network', $env:FLYWAY_DOCKER_NETWORK)
}

$dockerArguments += @(
    $image,
    '-locations=filesystem:/flyway/sql',
    '-table=flyway_schema_history',
    '-validateMigrationNaming=true',
    '-validateOnMigrate=true',
    '-baselineOnMigrate=false',
    '-cleanDisabled=true',
    '-outOfOrder=false',
    '-mixed=false',
    '-connectRetries=3',
    $Command
)

Write-Host "Flyway command: $Command"
Write-Host "Environment: $Environment"
Write-Host "Vendor: $vendor"
Write-Host "Migration directory: $migrationDirectory"
Write-Host "Image: $image"

& docker @dockerArguments
if ($LASTEXITCODE -ne 0) {
    throw "Flyway $Command failed with exit code $LASTEXITCODE. No repair was attempted."
}

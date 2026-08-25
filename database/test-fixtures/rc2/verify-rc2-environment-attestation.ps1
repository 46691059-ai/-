[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)]
    [string]$EvidencePath,
    [string]$ResultPath
)

$ErrorActionPreference = 'Stop'
$fingerprintBaselinePath = Join-Path $PSScriptRoot 'rc2-schema-fingerprint-baseline.json'
$fingerprintBaseline = Get-Content -LiteralPath $fingerprintBaselinePath -Raw | ConvertFrom-Json
$expectedStructuralFingerprint = [string]$fingerprintBaseline.structuralFingerprint
$expectedMetadataFingerprint = [string]$fingerprintBaseline.metadataFingerprint
$expectedStructuralCanonicalVersion = [string]$fingerprintBaseline.canonicalVersion
$canonicalVersion = 'RC2_ENVIRONMENT_ATTESTATION_CANONICAL_V2'

function Read-StableJsonFile([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { throw 'environment evidence is missing' }
    Read-StableJsonText ([IO.File]::ReadAllText($Path))
}
function Read-StableJsonText([string]$Text) {
    if ($PSVersionTable.PSVersion.Major -le 5) {
        Add-Type -AssemblyName System.Web.Extensions
        $serializer = New-Object System.Web.Script.Serialization.JavaScriptSerializer
        $serializer.MaxJsonLength = [int]::MaxValue
        return $serializer.DeserializeObject($Text)
    }
    function Convert-JsonElement($Element) {
        switch ([string]$Element.ValueKind) {
            'Object' {$result=@{};foreach($property in $Element.EnumerateObject()){$result[$property.Name]=Convert-JsonElement $property.Value};return $result}
            'Array' {return @($Element.EnumerateArray()|ForEach-Object{Convert-JsonElement $_})}
            'String' {return [string]$Element.GetString()}
            'Number' {[long]$integer=0;if($Element.TryGetInt64([ref]$integer)){return $integer};return [decimal]$Element.GetDecimal()}
            'True' {return $true}
            'False' {return $false}
            'Null' {return $null}
            default {throw "Unsupported JSON token: $($Element.ValueKind)"}
        }
    }
    $document=[Text.Json.JsonDocument]::Parse($Text)
    try{return Convert-JsonElement $document.RootElement}finally{$document.Dispose()}
}
function Write-Utf8NoBom([string]$Path,[string]$Content) {
    $parent = Split-Path -Parent $Path
    if ($parent -and -not (Test-Path -LiteralPath $parent)) {
        New-Item -ItemType Directory -Path $parent -Force | Out-Null
    }
    [IO.File]::WriteAllText($Path,$Content,(New-Object Text.UTF8Encoding($false)))
}
function Get-CanonicalPath([string]$Path) {
    try { ([IO.Path]::GetFullPath($Path) -replace '/','\').TrimEnd([char[]]@('\','/')) }
    catch { '' }
}
function Get-CanonicalHash($Evidence) {
    $fields = [ordered]@{
        canonicalVersion=$canonicalVersion
        collectorVersion=[string]$Evidence['collectorVersion']
        host=[string]$Evidence['host']
        requestedPort=[string]$Evidence['requestedPort']
        actualListeningPort=[string]$Evidence['actualListeningPort']
        requestedDatadir=Get-CanonicalPath ([string]$Evidence['requestedDatadir'])
        actualMysqlDatadir=Get-CanonicalPath ([string]$Evidence['actualMysqlDatadir'])
        databaseName=[string]$Evidence['databaseName']
        markerVersion=[string]$Evidence['environmentMarkerVersion']
        latestMigration=[string]$Evidence['latestMigration']
        structuralFingerprint=[string]$Evidence['schemaStructuralFingerprint']
        structuralCanonicalVersion=[string]$Evidence['structuralFingerprintCanonicalVersion']
        migrationShaPassed=[string]$Evidence['migrationShaPassed']
        migrationShaTotal=[string]$Evidence['migrationShaTotal']
        roleRuntimeEnabled=([bool]$Evidence['roleRuntimeEnabled']).ToString().ToLowerInvariant()
        canaryAuthorized=([bool]$Evidence['canaryAuthorized']).ToString().ToLowerInvariant()
        canaryEnabled=([bool]$Evidence['canaryEnabled']).ToString().ToLowerInvariant()
        killSwitch=[string]$Evidence['killSwitch']
        productionDirectoryProviderEnabled=([bool]$Evidence['productionDirectoryProviderEnabled']).ToString().ToLowerInvariant()
    }
    $builder = New-Object Text.StringBuilder
    foreach ($entry in $fields.GetEnumerator()) {
        $key=[string]$entry.Key;$value=[string]$entry.Value
        [void]$builder.Append($key.Length).Append(':').Append($key).Append($value.Length).Append(':').Append($value)
    }
    $sha=[Security.Cryptography.SHA256]::Create()
    try {
        $bytes=(New-Object Text.UTF8Encoding($false)).GetBytes($builder.ToString())
        ([BitConverter]::ToString($sha.ComputeHash($bytes))).Replace('-','').ToLowerInvariant()
    } finally { $sha.Dispose() }
}
function Add-Assertion([Collections.Generic.List[string]]$Failures,[bool]$Condition,[string]$Code) {
    if (-not $Condition) { $Failures.Add($Code) }
}

$evidence = Read-StableJsonFile $EvidencePath
$failures = New-Object Collections.Generic.List[string]
$warnings = New-Object Collections.Generic.List[string]

Add-Assertion $failures ([string]$evidence['collectorVersion'] -ceq 'RC2_ENVIRONMENT_EVIDENCE_COLLECTOR_V2') 'COLLECTOR_VERSION'
Add-Assertion $failures ($evidence['timestampUtc'] -is [string]) 'TIMESTAMP_MUST_REMAIN_STRING'
Add-Assertion $failures ([string]$evidence['mysqlVersion'] -match '^8\.4\.') 'MYSQL_VERSION'
Add-Assertion $failures ([string]$evidence['host'] -ceq '127.0.0.1') 'HOST_LOOPBACK'
Add-Assertion $failures ([int]$evidence['requestedPort'] -eq [int]$evidence['actualListeningPort']) 'PORT_IDENTITY'
Add-Assertion $failures ([int]$evidence['requestedPort'] -ne 34061) 'RC1_PORT_FORBIDDEN'
Add-Assertion $failures ([int]$evidence['requestedPort'] -ge 1024 -and [int]$evidence['requestedPort'] -le 65535) 'PORT_RANGE'
$requestedDataDir=Get-CanonicalPath ([string]$evidence['requestedDatadir'])
$actualDataDir=Get-CanonicalPath ([string]$evidence['actualMysqlDatadir'])
Add-Assertion $failures (-not [string]::IsNullOrWhiteSpace($requestedDataDir)) 'REQUESTED_DATADIR'
Add-Assertion $failures ([StringComparer]::OrdinalIgnoreCase.Equals($requestedDataDir,$actualDataDir)) 'DATADIR_IDENTITY'
Add-Assertion $failures (-not [StringComparer]::OrdinalIgnoreCase.Equals($actualDataDir,'D:\mysql-rc1\data')) 'RC1_DATADIR_FORBIDDEN'
Add-Assertion $failures ([string]$evidence['databaseName'] -ceq 'enterprise_platform') 'DATABASE_NAME'
Add-Assertion $failures ([string]$evidence['environmentMarkerVersion'] -ceq 'EPHEMERAL_RC2_TEST_IDENTITY_V1') 'MARKER_VERSION'
Add-Assertion $failures ([string]$evidence['latestMigration'] -ceq '2.6.23') 'LATEST_MIGRATION'
Add-Assertion $failures ([int]$evidence['failedMigrationCount'] -eq 0) 'FAILED_MIGRATIONS'
Add-Assertion $failures ([int]$evidence['migrationCount'] -eq 45) 'MIGRATION_COUNT'
Add-Assertion $failures ([int]$evidence['migrationShaPassed'] -eq 45 -and [int]$evidence['migrationShaTotal'] -eq 45) 'MIGRATION_SHA'
Add-Assertion $failures ([string]$evidence['schemaStructuralFingerprint'] -ceq $expectedStructuralFingerprint) 'STRUCTURAL_FINGERPRINT'
Add-Assertion $failures ([string]$evidence['structuralFingerprintCanonicalVersion'] -ceq $expectedStructuralCanonicalVersion) 'STRUCTURAL_CANONICAL_VERSION'
if ([string]$evidence['schemaMetadataFingerprint'] -cne $expectedMetadataFingerprint) {
    $warnings.Add('METADATA_FINGERPRINT_DRIFT')
}
Add-Assertion $failures (-not [bool]$evidence['roleRuntimeEnabled']) 'ROLE_RUNTIME_DISABLED'
Add-Assertion $failures (-not [bool]$evidence['canaryAuthorized']) 'CANARY_NOT_AUTHORIZED'
Add-Assertion $failures (-not [bool]$evidence['canaryEnabled']) 'CANARY_DISABLED'
Add-Assertion $failures ([string]$evidence['killSwitch'] -ceq 'STOP_NEW_AND_CLAIM') 'KILL_SWITCH'
Add-Assertion $failures (-not [bool]$evidence['productionDirectoryProviderEnabled']) 'PRODUCTION_DIRECTORY_PROVIDER'
Add-Assertion $failures ([int]$evidence['fixtureIdConflictCount'] -eq 0) 'FIXTURE_ID_CONFLICT'
Add-Assertion $failures ([int]$evidence['fixtureBusinessKeyConflictCount'] -eq 0) 'FIXTURE_BUSINESS_KEY_CONFLICT'
Add-Assertion $failures ([string]$evidence['partialFixtureState'] -ceq 'NONE_PRESENT') 'PARTIAL_FIXTURE'
Add-Assertion $failures ($evidence['fixtureEffectiveAtRaw'] -is [string] -and [string]$evidence['fixtureEffectiveAtRaw'] -ceq '2026-01-01T00:00:00Z') 'FIXTURE_EFFECTIVE_AT_STRING'
Add-Assertion $failures ([int]$evidence['migrationSnapshotSourceCount'] -eq 45) 'MIGRATION_SNAPSHOT_SOURCE_COUNT'
Add-Assertion $failures ([int]$evidence['migrationSnapshotFileCount'] -eq 45) 'MIGRATION_SNAPSHOT_FILE_COUNT'
Add-Assertion $failures ([int]$evidence['migrationSnapshotShaPassed'] -eq 45 -and [int]$evidence['migrationSnapshotShaTotal'] -eq 45 -and [string]$evidence['migrationSnapshotShaValidation'] -ceq '45/45_PASS') 'MIGRATION_SNAPSHOT_SHA'

try {
    $marker = Read-StableJsonText ([string]$evidence['environmentMarkerRaw'])
} catch { $marker = $null }
# The marker path is evidence only. The verifier parses the Collector-embedded raw marker and never
# reads the marker source, invokes MySQL, scans ports, recalculates schema, or scans migrations.
Add-Assertion $failures ($null -ne $marker) 'MARKER_RAW_JSON'
if ($null -ne $marker) {
    Add-Assertion $failures ([string]$marker['contract'] -ceq [string]$evidence['environmentMarkerVersion']) 'MARKER_CONTRACT_MATCH'
    Add-Assertion $failures ([string]$marker['host'] -ceq [string]$evidence['host']) 'MARKER_HOST_MATCH'
    Add-Assertion $failures ([int]$marker['port'] -eq [int]$evidence['requestedPort']) 'MARKER_PORT_MATCH'
    Add-Assertion $failures ([string]$marker['database'] -ceq [string]$evidence['databaseName']) 'MARKER_DATABASE_MATCH'
    Add-Assertion $failures ([StringComparer]::OrdinalIgnoreCase.Equals(
        (Get-CanonicalPath ([string]$marker['dataDir'])),$requestedDataDir)) 'MARKER_DATADIR_MATCH'
    Add-Assertion $failures ([string]$marker['releaseVersion'] -ceq [string]$evidence['latestMigration']) 'MARKER_RELEASE_MATCH'
    Add-Assertion $failures ([string]$marker['roleRuntime'] -ceq 'DISABLED') 'MARKER_RUNTIME_SAFETY'
    Add-Assertion $failures ([string]$marker['canary'] -ceq 'NOT_AUTHORIZED_NOT_ENABLED') 'MARKER_CANARY_SAFETY'
    Add-Assertion $failures ([string]$marker['killSwitch'] -ceq 'STOP_NEW_AND_CLAIM') 'MARKER_KILL_SWITCH'
    Add-Assertion $failures ([string]$marker['directoryProvider'] -ceq 'DISABLED') 'MARKER_PROVIDER_SAFETY'
}

$passed = $failures.Count -eq 0
$attestationId = if ($passed) { Get-CanonicalHash $evidence } else { $null }
$result = [ordered]@{
    attestationVersion='RC2_ENVIRONMENT_ATTESTATION_V2'
    result=$(if($passed){'PASS'}else{'FAIL'})
    failedAssertions=@($failures)
    warnings=@($warnings)
    attestationId=$attestationId
    evidencePath=[IO.Path]::GetFullPath($EvidencePath)
}
if (-not [string]::IsNullOrWhiteSpace($ResultPath)) {
    Write-Utf8NoBom $ResultPath ($result | ConvertTo-Json -Depth 8)
}
Write-Output ("ENVIRONMENT_ATTESTATION={0}" -f $result.result)
Write-Output ("FAILED_ASSERTIONS={0}" -f ($failures -join ','))
Write-Output ("ATTESTATION_WARNINGS={0}" -f ($warnings -join ','))
Write-Output ("RC2_ENVIRONMENT_ATTESTATION_ID={0}" -f $attestationId)
if (-not $passed) { exit 2 }

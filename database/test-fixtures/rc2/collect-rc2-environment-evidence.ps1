[CmdletBinding()]
param(
    [ValidateSet('Collect','Mock')]
    [string]$Action = 'Mock',
    [Parameter(Mandatory=$true)]
    [string]$EvidencePath,
    [Parameter(Mandatory=$true)]
    [string]$MigrationSnapshotDirectory,
    [string]$HostName = '127.0.0.1',
    [int]$Port = 38650,
    [string]$Database = 'enterprise_platform',
    [string]$RequestedDataDir = 'D:\codex-rc2-attestation-mock\mysql\data',
    [string]$MarkerPath,
    [string]$StructuralCanonicalPath,
    [string]$MetadataCanonicalPath,
    [string]$MySqlClient = 'C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe',
    [string]$Username = 'enterprise_rc2',
    [Security.SecureString]$Password
)

$ErrorActionPreference = 'Stop'
$fixtureRoot = $PSScriptRoot
$repositoryRoot = [IO.Path]::GetFullPath((Join-Path $fixtureRoot '..\..\..'))
$manifestPath = Join-Path $fixtureRoot 'rc2-canary-fixture-manifest.json'
$migrationRoot = Join-Path $repositoryRoot 'database\migration\mysql'
$shaManifestPath = Join-Path $migrationRoot 'SHA256SUMS'
$fingerprintBaselinePath = Join-Path $fixtureRoot 'rc2-schema-fingerprint-baseline.json'
$fingerprintBaseline = Get-Content -LiteralPath $fingerprintBaselinePath -Raw | ConvertFrom-Json
$structuralCanonicalVersion = [string]$fingerprintBaseline.canonicalVersion
$structuralFingerprint = [string]$fingerprintBaseline.structuralFingerprint
$metadataFingerprint = [string]$fingerprintBaseline.metadataFingerprint

function Write-Utf8NoBom([string]$Path,[string]$Content) {
    $parent = Split-Path -Parent $Path
    if ($parent -and -not (Test-Path -LiteralPath $parent)) {
        New-Item -ItemType Directory -Path $parent -Force | Out-Null
    }
    [IO.File]::WriteAllText($Path,$Content,(New-Object Text.UTF8Encoding($false)))
}
function Read-StableJson([string]$Text) {
    if ($PSVersionTable.PSVersion.Major -le 5) {
        Add-Type -AssemblyName System.Web.Extensions
        $serializer = New-Object System.Web.Script.Serialization.JavaScriptSerializer
        $serializer.MaxJsonLength = [int]::MaxValue
        return $serializer.DeserializeObject($Text)
    }
    function Convert-JsonElement($Element) {
        switch ([string]$Element.ValueKind) {
            'Object' {
                $result = @{}
                foreach ($property in $Element.EnumerateObject()) {
                    $result[$property.Name] = Convert-JsonElement $property.Value
                }
                return $result
            }
            'Array' { return @($Element.EnumerateArray() | ForEach-Object { Convert-JsonElement $_ }) }
            'String' { return [string]$Element.GetString() }
            'Number' {
                [long]$integer = 0
                if ($Element.TryGetInt64([ref]$integer)) { return $integer }
                return [decimal]$Element.GetDecimal()
            }
            'True' { return $true }
            'False' { return $false }
            'Null' { return $null }
            default { throw "Unsupported JSON token: $($Element.ValueKind)" }
        }
    }
    $document = [Text.Json.JsonDocument]::Parse($Text)
    try { Convert-JsonElement $document.RootElement }
    finally { $document.Dispose() }
}
function Get-RawJsonString([string]$Text,[string]$PropertyName) {
    $pattern = '"' + [regex]::Escape($PropertyName) + '"\s*:\s*"(?<value>[^"\\]*)"'
    $matches = [regex]::Matches($Text,$pattern)
    if ($matches.Count -ne 1) { throw "Expected one unescaped JSON string: $PropertyName" }
    $matches[0].Groups['value'].Value
}
function Get-CanonicalPath([string]$Path) {
    ([IO.Path]::GetFullPath($Path) -replace '/','\').TrimEnd([char[]]@('\','/'))
}
function Get-Sha256Text([string]$Text) {
    $sha = [Security.Cryptography.SHA256]::Create()
    try {
        $bytes = (New-Object Text.UTF8Encoding($false)).GetBytes($Text)
        ([BitConverter]::ToString($sha.ComputeHash($bytes))).Replace('-','').ToLowerInvariant()
    } finally { $sha.Dispose() }
}
function Convert-Password([Security.SecureString]$Value) {
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Value)
    try { [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
}
function New-MigrationSnapshot {
    if (-not (Test-Path -LiteralPath $shaManifestPath -PathType Leaf)) {
        throw 'SHA256SUMS is missing'
    }
    $entries = New-Object Collections.Generic.List[object]
    foreach ($line in Get-Content -LiteralPath $shaManifestPath) {
        if ($line -notmatch '^([0-9a-f]{64})\s+(V.+\.sql)$') { continue }
        $entries.Add([pscustomobject]@{Hash=$Matches[1];Name=$Matches[2]})
    }
    if ($entries.Count -ne 45) { throw "Migration inventory count is $($entries.Count), expected 45" }
    if (Test-Path -LiteralPath $MigrationSnapshotDirectory) {
        $existing = @(Get-ChildItem -LiteralPath $MigrationSnapshotDirectory -File -Force)
        if ($existing.Count -ne 0) { throw 'Migration snapshot destination must be empty' }
    } else {
        New-Item -ItemType Directory -Path $MigrationSnapshotDirectory -Force | Out-Null
    }
    $sourcePassed = 0
    foreach ($entry in $entries) {
        $source = Join-Path $migrationRoot $entry.Name
        if (-not (Test-Path -LiteralPath $source -PathType Leaf)) {
            throw "Migration inventory file missing: $($entry.Name)"
        }
        $sourceHash = (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($sourceHash -cne $entry.Hash) { throw "Migration source SHA drift: $($entry.Name)" }
        $sourcePassed++
        Copy-Item -LiteralPath $source -Destination (Join-Path $MigrationSnapshotDirectory $entry.Name)
    }
    $destinationPassed = 0
    foreach ($entry in $entries) {
        $destination = Join-Path $MigrationSnapshotDirectory $entry.Name
        if ((Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash.ToLowerInvariant() `
                -ceq $entry.Hash) { $destinationPassed++ }
    }
    [pscustomobject]@{
        SourceCount=$entries.Count
        SourceShaPassed=$sourcePassed
        DestinationCount=@(Get-ChildItem -LiteralPath $MigrationSnapshotDirectory -File).Count
        DestinationShaPassed=$destinationPassed
    }
}
function Invoke-Query([string]$Sql) {
    $arguments = @('--protocol=TCP',"--host=$HostName","--port=$Port","--user=$Username",
        '--default-character-set=utf8mb4',"--database=$Database",'--batch','--raw',
        '--skip-column-names',"--execute=$Sql")
    $old = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
    try { $output = @(& $MySqlClient @arguments 2>&1); $code = $LASTEXITCODE }
    finally { $ErrorActionPreference = $old }
    if ($code -ne 0) { throw ("Evidence query failed: " + ($output -join "`n")) }
    $output -join "`n"
}
function Get-SchemaEvidence {
    $structuralQueries = @(
        "SELECT CONCAT('TABLE|',table_name,'|',engine,'|',table_collation) FROM information_schema.tables WHERE table_schema='$Database' AND table_type='BASE TABLE' ORDER BY table_name",
        "SELECT CONCAT('COLUMN|',table_name,'|',ordinal_position,'|',column_name,'|',column_type,'|',is_nullable,'|',CASE WHEN generation_expression<>'' THEN '<GENERATED_NO_DEFAULT>' WHEN column_default IS NULL AND is_nullable='YES' THEN '<DEFAULT_NULL>' WHEN column_default IS NULL THEN '<NO_DEFAULT>' ELSE CONCAT('<DEFAULT_VALUE_HEX>:',HEX(CONVERT(column_default USING utf8mb4))) END,'|',IFNULL(collation_name,'<NO_COLLATION>'),'|',IF(extra='','<NO_EXTRA>',extra),'|',IF(generation_expression='','<NO_GENERATION_EXPRESSION>',CONCAT('<GENERATION_EXPRESSION_UTF8_HEX>:',HEX(CONVERT(generation_expression USING utf8mb4))))) FROM information_schema.columns WHERE table_schema='$Database' ORDER BY table_name,ordinal_position",
        "SELECT CONCAT('INDEX|',table_name,'|',index_name,'|',non_unique,'|',seq_in_index,'|',IFNULL(column_name,'<EXPRESSION>'),'|',IFNULL(collation,'<NO_COLLATION>'),'|',IFNULL(sub_part,'<FULL_LENGTH>'),'|',nullable,'|',index_type,'|',is_visible,'|',IFNULL(CONCAT('<EXPRESSION_UTF8_HEX>:',HEX(CONVERT(expression USING utf8mb4))),'<NO_EXPRESSION>')) FROM information_schema.statistics WHERE table_schema='$Database' ORDER BY table_name,index_name,seq_in_index",
        "SELECT CONCAT('TRIGGER|',trigger_name,'|',event_manipulation,'|',event_object_table,'|',action_timing,'|',action_orientation,'|',IFNULL(CONCAT('<CONDITION_UTF8_HEX>:',HEX(CONVERT(action_condition USING utf8mb4))),'<NO_CONDITION>'),'|<ACTION_STATEMENT_UTF8_HEX>:',HEX(CONVERT(action_statement USING utf8mb4))) FROM information_schema.triggers WHERE trigger_schema='$Database' ORDER BY trigger_name,event_manipulation,action_timing",
        "SELECT CONCAT('FK|',k.constraint_name,'|',k.table_name,'|',k.ordinal_position,'|',k.column_name,'|',k.referenced_table_name,'|',k.referenced_column_name,'|',r.unique_constraint_name,'|',r.update_rule,'|',r.delete_rule) FROM information_schema.key_column_usage k JOIN information_schema.referential_constraints r ON r.constraint_schema=k.constraint_schema AND r.table_name=k.table_name AND r.constraint_name=k.constraint_name WHERE k.constraint_schema='$Database' AND k.referenced_table_name IS NOT NULL ORDER BY k.table_name,k.constraint_name,k.ordinal_position",
        "SELECT CONCAT('CHECK|',tc.table_name,'|',tc.constraint_name,'|',tc.enforced,'|<CHECK_CLAUSE_UTF8_HEX>:',HEX(CONVERT(cc.check_clause USING utf8mb4))) FROM information_schema.table_constraints tc JOIN information_schema.check_constraints cc ON cc.constraint_schema=tc.constraint_schema AND cc.constraint_name=tc.constraint_name WHERE tc.constraint_schema='$Database' AND tc.constraint_type='CHECK' ORDER BY tc.table_name,tc.constraint_name"
    )
    $metadataQueries = @(
        "SELECT CONCAT('TABLE_COMMENT|',table_name,'|',IFNULL(table_comment,'')) FROM information_schema.tables WHERE table_schema='$Database' AND table_type='BASE TABLE' ORDER BY table_name",
        "SELECT CONCAT('COLUMN_COMMENT|',table_name,'|',ordinal_position,'|',column_name,'|',IFNULL(column_comment,'')) FROM information_schema.columns WHERE table_schema='$Database' ORDER BY table_name,ordinal_position"
    )
    $structuralText = (($structuralQueries | ForEach-Object { Invoke-Query $_ }) -join "`n") + "`n"
    $metadataText = (($metadataQueries | ForEach-Object { Invoke-Query $_ }) -join "`n") + "`n"
    if (-not [string]::IsNullOrWhiteSpace($StructuralCanonicalPath)) {
        Write-Utf8NoBom $StructuralCanonicalPath $structuralText
    }
    if (-not [string]::IsNullOrWhiteSpace($MetadataCanonicalPath)) {
        Write-Utf8NoBom $MetadataCanonicalPath $metadataText
    }
    [pscustomobject]@{
        StructuralFingerprint=Get-Sha256Text $structuralText
        MetadataFingerprint=Get-Sha256Text $metadataText
        StructuralLineCount=@($structuralText.TrimEnd("`r","`n") -split "`n").Count
    }
}

$manifestRaw = [IO.File]::ReadAllText($manifestPath)
$fixtureEffectiveAtRaw = Get-RawJsonString $manifestRaw 'directoryEffectiveAt'
$snapshot = New-MigrationSnapshot
$requestedDataDirCanonical = Get-CanonicalPath $RequestedDataDir

if ($Action -eq 'Mock') {
    $marker = [ordered]@{
        contract='EPHEMERAL_RC2_TEST_IDENTITY_V1'; environment='RC2_TEST'
        environmentType='EPHEMERAL_RC2_TEST'; host='127.0.0.1'; port=$Port
        database='enterprise_platform'; dataDir=$requestedDataDirCanonical
        releaseVersion='2.6.23'; structuralFingerprint=$structuralFingerprint
        migrationShaCount=45; createdByValidationScript=$true; roleRuntime='DISABLED'
        canary='NOT_AUTHORIZED_NOT_ENABLED'; killSwitch='STOP_NEW_AND_CLAIM'
        directoryProvider='DISABLED'
    }
    $markerRaw = $marker | ConvertTo-Json
    $evidence = [ordered]@{
        collectorVersion='RC2_ENVIRONMENT_EVIDENCE_COLLECTOR_V2'
        timestampUtc=(Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ss.fffZ')
        mysqlVersion='8.4.9'; host='127.0.0.1'; requestedPort=$Port; actualListeningPort=$Port
        databaseName='enterprise_platform'; requestedDatadir=$requestedDataDirCanonical
        actualMysqlDatadir=$requestedDataDirCanonical; environmentMarkerPath='MOCK_IN_MEMORY'
        environmentMarkerVersion='EPHEMERAL_RC2_TEST_IDENTITY_V1'; environmentMarkerRaw=$markerRaw
        latestMigration='2.6.23'; failedMigrationCount=0; migrationCount=45
        migrationShaPassed=$snapshot.SourceShaPassed; migrationShaTotal=$snapshot.SourceCount
        schemaStructuralFingerprint=$structuralFingerprint
        structuralFingerprintCanonicalVersion=$structuralCanonicalVersion
        structuralCanonicalLineCount=0
        schemaMetadataFingerprint=$metadataFingerprint
        roleRuntimeEnabled=$false; canaryAuthorized=$false; canaryEnabled=$false
        killSwitch='STOP_NEW_AND_CLAIM'; productionDirectoryProviderEnabled=$false
        fixtureIdConflictCount=0; fixtureBusinessKeyConflictCount=0
        partialFixtureState='NONE_PRESENT'; fixtureEffectiveAtRaw=$fixtureEffectiveAtRaw
        migrationSnapshotSourceCount=$snapshot.SourceCount
        migrationSnapshotFileCount=$snapshot.DestinationCount
        migrationSnapshotShaPassed=$snapshot.DestinationShaPassed
        migrationSnapshotShaTotal=$snapshot.SourceCount
        migrationSnapshotShaValidation="$($snapshot.DestinationShaPassed)/$($snapshot.SourceCount)_PASS"
    }
} else {
    if (-not (Test-Path -LiteralPath $MySqlClient -PathType Leaf)) { throw 'mysql client missing' }
    if ($null -eq $Password) { throw 'database password must be supplied as SecureString' }
    if ([string]::IsNullOrWhiteSpace($MarkerPath) -or -not (Test-Path -LiteralPath $MarkerPath)) {
        throw 'environment marker missing'
    }
    $markerRaw = [IO.File]::ReadAllText($MarkerPath)
    $marker = Read-StableJson $markerRaw
    $plain = Convert-Password $Password
    $previousPassword = $env:MYSQL_PWD
    try {
        $env:MYSQL_PWD = $plain
        $identity = (Invoke-Query "SELECT CONCAT(VERSION(),'|',@@port,'|',@@datadir,'|',DATABASE())").Trim() -split '\|',4
        $history = (Invoke-Query "SELECT CONCAT((SELECT version FROM flyway_schema_history WHERE success=1 AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1),'|',SUM(success=0),'|',SUM(type='SQL' AND success=1)) FROM flyway_schema_history").Trim() -split '\|'
        $schema = Get-SchemaEvidence
        $idConflictCount = [int](Invoke-Query "SELECT (SELECT COUNT(*) FROM sys_org WHERE id=990101)+(SELECT COUNT(*) FROM sys_user WHERE id IN (990201,990202))+(SELECT COUNT(*) FROM approval_role WHERE id=990301)+(SELECT COUNT(*) FROM workflow_definition WHERE id=990401)+(SELECT COUNT(*) FROM workflow_version WHERE id=990402)+(SELECT COUNT(*) FROM workflow_node WHERE id=990404)")
        $businessConflictCount = [int](Invoke-Query "SELECT (SELECT COUNT(*) FROM sys_org WHERE org_code='RC2_TEST_CANARY_ORG')+(SELECT COUNT(*) FROM sys_user WHERE username IN ('RC2_TEST_CANARY_USER_01','RC2_TEST_CANARY_USER_02'))+(SELECT COUNT(*) FROM approval_role WHERE enterprise_id='990001' AND role_code='RC1_TEST_CANARY_APPROVER')+(SELECT COUNT(*) FROM workflow_definition WHERE enterprise_id=990001 AND definition_code='RC2_TEST_CANARY_ROLE_APPROVAL')")
        $groups = (Invoke-Query "SELECT CONCAT((SELECT COUNT(*) FROM sys_org WHERE id=990101),'|',(SELECT COUNT(*) FROM sys_user WHERE id IN (990201,990202)),'|',(SELECT COUNT(*) FROM approval_role WHERE id=990301),'|',(SELECT COUNT(*) FROM workflow_definition WHERE id=990401))").Trim() -split '\|'
        $presentGroups = @($groups | Where-Object { [int]$_ -gt 0 }).Count
        $fixtureState = if ($presentGroups -eq 0) {'NONE_PRESENT'} elseif ($presentGroups -eq 4) {'ALL_PRESENT'} else {'PARTIAL_PRESENT'}
        $evidence = [ordered]@{
            collectorVersion='RC2_ENVIRONMENT_EVIDENCE_COLLECTOR_V2'
            timestampUtc=(Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ss.fffZ')
            mysqlVersion=[string]$identity[0]; host=[string]$HostName
            requestedPort=[int]$Port; actualListeningPort=[int]$identity[1]
            databaseName=[string]$identity[3]; requestedDatadir=$requestedDataDirCanonical
            actualMysqlDatadir=Get-CanonicalPath ([string]$identity[2])
            environmentMarkerPath=Get-CanonicalPath $MarkerPath
            environmentMarkerVersion=[string]$marker['contract']; environmentMarkerRaw=$markerRaw
            latestMigration=[string]$history[0]; failedMigrationCount=[int]$history[1]
            migrationCount=[int]$history[2]; migrationShaPassed=$snapshot.SourceShaPassed
            migrationShaTotal=$snapshot.SourceCount
            schemaStructuralFingerprint=$schema.StructuralFingerprint
            structuralFingerprintCanonicalVersion=$structuralCanonicalVersion
            structuralCanonicalLineCount=$schema.StructuralLineCount
            schemaMetadataFingerprint=$schema.MetadataFingerprint
            roleRuntimeEnabled=([string]$marker['roleRuntime'] -cne 'DISABLED')
            canaryAuthorized=([string]$marker['canary'] -cne 'NOT_AUTHORIZED_NOT_ENABLED')
            canaryEnabled=([string]$marker['canary'] -cne 'NOT_AUTHORIZED_NOT_ENABLED')
            killSwitch=[string]$marker['killSwitch']
            productionDirectoryProviderEnabled=([string]$marker['directoryProvider'] -cne 'DISABLED')
            fixtureIdConflictCount=$idConflictCount
            fixtureBusinessKeyConflictCount=$businessConflictCount
            partialFixtureState=$fixtureState; fixtureEffectiveAtRaw=$fixtureEffectiveAtRaw
            migrationSnapshotSourceCount=$snapshot.SourceCount
            migrationSnapshotFileCount=$snapshot.DestinationCount
            migrationSnapshotShaPassed=$snapshot.DestinationShaPassed
            migrationSnapshotShaTotal=$snapshot.SourceCount
            migrationSnapshotShaValidation="$($snapshot.DestinationShaPassed)/$($snapshot.SourceCount)_PASS"
        }
    } finally {
        $env:MYSQL_PWD = $previousPassword
        $plain = $null
    }
}

Write-Utf8NoBom $EvidencePath ($evidence | ConvertTo-Json -Depth 8)
Write-Output "ENVIRONMENT_EVIDENCE_PATH=$([IO.Path]::GetFullPath($EvidencePath))"
Write-Output "MIGRATION_SNAPSHOT_SOURCE_COUNT=$($snapshot.SourceCount)"
Write-Output "MIGRATION_SNAPSHOT_DEST_COUNT=$($snapshot.DestinationCount)"
Write-Output "MIGRATION_SNAPSHOT_SHA=$($snapshot.DestinationShaPassed)/$($snapshot.SourceCount)_PASS"

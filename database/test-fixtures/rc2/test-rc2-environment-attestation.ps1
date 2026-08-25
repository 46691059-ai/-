[CmdletBinding()]
param()

$ErrorActionPreference='Stop'
$root=Join-Path ([IO.Path]::GetTempPath()) ('rc2-attestation-contract-' + [guid]::NewGuid())
$collector=Join-Path $PSScriptRoot 'collect-rc2-environment-evidence.ps1'
$verifier=Join-Path $PSScriptRoot 'verify-rc2-environment-attestation.ps1'

function Read-StableJsonText([string]$Text) {
    if($PSVersionTable.PSVersion.Major-le5){
        Add-Type -AssemblyName System.Web.Extensions
        $serializer=New-Object System.Web.Script.Serialization.JavaScriptSerializer
        $serializer.MaxJsonLength=[int]::MaxValue
        return $serializer.DeserializeObject($Text)
    }
    function Convert-JsonElement($Element){
        switch([string]$Element.ValueKind){
            'Object'{$result=@{};foreach($property in $Element.EnumerateObject()){$result[$property.Name]=Convert-JsonElement $property.Value};return $result}
            'Array'{return @($Element.EnumerateArray()|ForEach-Object{Convert-JsonElement $_})}
            'String'{return [string]$Element.GetString()}
            'Number'{[long]$integer=0;if($Element.TryGetInt64([ref]$integer)){return $integer};return [decimal]$Element.GetDecimal()}
            'True'{return $true};'False'{return $false};'Null'{return $null}
        }
    }
    $document=[Text.Json.JsonDocument]::Parse($Text)
    try{return Convert-JsonElement $document.RootElement}finally{$document.Dispose()}
}
function Write-Utf8NoBom([string]$Path,[string]$Content) {
    [IO.File]::WriteAllText($Path,$Content,(New-Object Text.UTF8Encoding($false)))
}

try {
    New-Item -ItemType Directory -Path $root -Force|Out-Null
    $baseEvidencePath=Join-Path $root 'environment-evidence.json'
    $snapshot=Join-Path $root 'migration-snapshot'
    & $collector -Action Mock -EvidencePath $baseEvidencePath -MigrationSnapshotDirectory $snapshot|Out-Null
    $base=Read-StableJsonText ([IO.File]::ReadAllText($baseEvidencePath))
    $script:passed=0;$script:total=0

    function Run-Case([string]$Name,[scriptblock]$Mutate,[string]$Expected,[string[]]$ExpectedAssertions=@(),[string[]]$ExpectedWarnings=@()) {
        $script:total++
        $evidence=Read-StableJsonText ($base|ConvertTo-Json -Depth 10)
        & $Mutate $evidence
        $caseEvidence=Join-Path $root "$Name-evidence.json"
        $caseResult=Join-Path $root "$Name-result.json"
        Write-Utf8NoBom $caseEvidence ($evidence|ConvertTo-Json -Depth 10)
        $output=@(& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $verifier -EvidencePath $caseEvidence -ResultPath $caseResult 2>&1)
        $exitCode=$LASTEXITCODE
        $result=if(Test-Path -LiteralPath $caseResult){Read-StableJsonText ([IO.File]::ReadAllText($caseResult))}else{$null}
        $ok = $null -ne $result -and [string]$result['result'] -ceq $Expected
        if ($Expected -ceq 'PASS') { $ok = $ok -and $exitCode -eq 0 }
        else { $ok = $ok -and $exitCode -eq 2 }
        foreach($assertion in $ExpectedAssertions){$ok=$ok-and(@($result['failedAssertions'])-contains$assertion)}
        foreach($warning in $ExpectedWarnings){$ok=$ok-and(@($result['warnings'])-contains$warning)}
        if($ok){$script:passed++}
        Write-Output "ATTESTATION_CASE_$Name=$(if($ok){'PASS'}else{'FAIL'})"
    }

    Run-Case 'VALID_EVIDENCE' {param($e)} 'PASS'
    Run-Case 'ALL_ASSERTIONS_REPORTED' {param($e)$e['mysqlVersion']='5.7';$e['host']='0.0.0.0';$e['requestedPort']=34061;$e['actualListeningPort']=9999;$e['requestedDatadir']='D:\mysql-rc1\data';$e['actualMysqlDatadir']='D:\other\data';$e['databaseName']='wrong';$e['latestMigration']='2.6.22';$e['failedMigrationCount']=1;$e['migrationCount']=0;$e['migrationShaPassed']=0;$e['schemaStructuralFingerprint']='0'*64;$e['roleRuntimeEnabled']=$true;$e['canaryAuthorized']=$true;$e['canaryEnabled']=$true;$e['killSwitch']='RUN';$e['productionDirectoryProviderEnabled']=$true;$e['fixtureIdConflictCount']=1;$e['fixtureBusinessKeyConflictCount']=1;$e['partialFixtureState']='PARTIAL_PRESENT';$e['migrationSnapshotFileCount']=0;$e['migrationSnapshotShaPassed']=0;$e['environmentMarkerRaw']='{'} 'FAIL' @('MYSQL_VERSION','PORT_IDENTITY','STRUCTURAL_FINGERPRINT','MARKER_RAW_JSON')
    Run-Case 'WRONG_MYSQL' {param($e)$e['mysqlVersion']='8.0.0'} 'FAIL' @('MYSQL_VERSION')
    Run-Case 'WRONG_PORT' {param($e)$e['actualListeningPort']=38651} 'FAIL' @('PORT_IDENTITY')
    Run-Case 'RC1_PORT' {param($e)$e['requestedPort']=34061;$e['actualListeningPort']=34061} 'FAIL' @('RC1_PORT_FORBIDDEN')
    Run-Case 'RC1_DATADIR' {param($e)$e['requestedDatadir']='D:\mysql-rc1\data';$e['actualMysqlDatadir']='D:\mysql-rc1\data'} 'FAIL' @('RC1_DATADIR_FORBIDDEN')
    Run-Case 'DATADIR_MISMATCH' {param($e)$e['actualMysqlDatadir']='D:\codex-rc2-attestation-other\mysql\data'} 'FAIL' @('DATADIR_IDENTITY')
    Run-Case 'WRONG_MIGRATION' {param($e)$e['latestMigration']='2.6.22'} 'FAIL' @('LATEST_MIGRATION')
    Run-Case 'FAILED_MIGRATION' {param($e)$e['failedMigrationCount']=1} 'FAIL' @('FAILED_MIGRATIONS')
    Run-Case 'MIGRATION_COUNT_MISMATCH' {param($e)$e['migrationCount']=44} 'FAIL' @('MIGRATION_COUNT')
    Run-Case 'MIGRATION_SHA_MISMATCH' {param($e)$e['migrationShaPassed']=44} 'FAIL' @('MIGRATION_SHA')
    Run-Case 'STRUCTURAL_FINGERPRINT_MISMATCH' {param($e)$e['schemaStructuralFingerprint']='0'*64} 'FAIL' @('STRUCTURAL_FINGERPRINT')
    Run-Case 'STRUCTURAL_COLUMN_REMOVED' {param($e)$e['schemaStructuralFingerprint']='1'*64} 'FAIL' @('STRUCTURAL_FINGERPRINT')
    Run-Case 'STRUCTURAL_COLUMN_TYPE_CHANGED' {param($e)$e['schemaStructuralFingerprint']='2'*64} 'FAIL' @('STRUCTURAL_FINGERPRINT')
    Run-Case 'STRUCTURAL_FK_REMOVED' {param($e)$e['schemaStructuralFingerprint']='3'*64} 'FAIL' @('STRUCTURAL_FINGERPRINT')
    Run-Case 'STRUCTURAL_TRIGGER_REMOVED' {param($e)$e['schemaStructuralFingerprint']='4'*64} 'FAIL' @('STRUCTURAL_FINGERPRINT')
    Run-Case 'STRUCTURAL_CHECK_CHANGED' {param($e)$e['schemaStructuralFingerprint']='5'*64} 'FAIL' @('STRUCTURAL_FINGERPRINT')
    Run-Case 'STRUCTURAL_CANONICAL_VERSION_CHANGED' {param($e)$e['structuralFingerprintCanonicalVersion']='RC2_SCHEMA_STRUCTURAL_CANONICAL_V1'} 'FAIL' @('STRUCTURAL_CANONICAL_VERSION')
    Run-Case -Name 'METADATA_FINGERPRINT_WARNING' `
        -Mutate {param($e)$e['schemaMetadataFingerprint']='0'*64} -Expected 'PASS' `
        -ExpectedWarnings @('METADATA_FINGERPRINT_DRIFT')
    Run-Case 'RUNTIME_ENABLED' {param($e)$e['roleRuntimeEnabled']=$true} 'FAIL' @('ROLE_RUNTIME_DISABLED')
    Run-Case 'CANARY_AUTHORIZED' {param($e)$e['canaryAuthorized']=$true} 'FAIL' @('CANARY_NOT_AUTHORIZED')
    Run-Case 'UNSAFE_KILL_SWITCH' {param($e)$e['killSwitch']='RUN'} 'FAIL' @('KILL_SWITCH')
    Run-Case 'PROVIDER_ENABLED' {param($e)$e['productionDirectoryProviderEnabled']=$true} 'FAIL' @('PRODUCTION_DIRECTORY_PROVIDER')
    Run-Case 'FIXTURE_ID_CONFLICT' {param($e)$e['fixtureIdConflictCount']=1} 'FAIL' @('FIXTURE_ID_CONFLICT')
    Run-Case 'PARTIAL_FIXTURE' {param($e)$e['partialFixtureState']='PARTIAL_PRESENT'} 'FAIL' @('PARTIAL_FIXTURE')
    Run-Case 'MARKER_INVALID' {param($e)$e['environmentMarkerRaw']='{'} 'FAIL' @('MARKER_RAW_JSON')
    Run-Case 'SNAPSHOT_COUNT_ZERO' {param($e)$e['migrationSnapshotFileCount']=0;$e['migrationSnapshotShaPassed']=0;$e['migrationSnapshotShaValidation']='0/45_FAIL'} 'FAIL' @('MIGRATION_SNAPSHOT_FILE_COUNT','MIGRATION_SNAPSHOT_SHA')

    $collectorText=[IO.File]::ReadAllText($collector)
    $wildcardSafe=$collectorText-notmatch 'Copy-Item\s+-LiteralPath\s+[^\r\n]*\*'
    $snapshotSafe=@(Get-ChildItem -LiteralPath $snapshot -File).Count-eq45
    Run-Case 'WILDCARD_LITERAL_COPY_REGRESSION' {param($e)if(-not($wildcardSafe-and$snapshotSafe)){$e['migrationSnapshotFileCount']=0}} 'PASS'

    $typeProbe=Join-Path $root 'type-probe.ps1'
    Write-Utf8NoBom $typeProbe @'
param([string]$Path)
if($PSVersionTable.PSVersion.Major-le5){
  Add-Type -AssemblyName System.Web.Extensions
  $s=New-Object System.Web.Script.Serialization.JavaScriptSerializer
  $e=$s.DeserializeObject([IO.File]::ReadAllText($Path))
}else{
  $d=[Text.Json.JsonDocument]::Parse([IO.File]::ReadAllText($Path))
  try{$value=$d.RootElement.GetProperty('fixtureEffectiveAtRaw').GetString()}finally{$d.Dispose()}
  $e=@{fixtureEffectiveAtRaw=$value}
}
Write-Output ($e['fixtureEffectiveAtRaw'].GetType().FullName+'|'+$e['fixtureEffectiveAtRaw'])
'@
    $ps51Probe=(& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $typeProbe -Path $baseEvidencePath|Select-Object -Last 1)
    Run-Case 'ISO_STRING_PS51' {param($e)if($ps51Probe -cne 'System.String|2026-01-01T00:00:00Z'){$e['fixtureEffectiveAtRaw']='INVALID'}} 'PASS'
    $pwsh=(Get-Command pwsh.exe -ErrorAction SilentlyContinue).Source
    $ps7Probe=if($pwsh){(& $pwsh -NoProfile -File $typeProbe -Path $baseEvidencePath|Select-Object -Last 1)}else{'MISSING'}
    Run-Case 'ISO_STRING_PS7' {param($e)if($ps7Probe -cne 'System.String|2026-01-01T00:00:00Z'){$e['fixtureEffectiveAtRaw']='INVALID'}} 'PASS'

    $chineseMetadata = -join @([char]0x4e2d,[char]0x6587,[char]0x6ce8,
        [char]0x91ca,[char]0x53d8,[char]0x5316)
    Run-Case -Name 'CHINESE_METADATA_NON_STRUCTURAL' `
        -Mutate {param($e)$e['schemaMetadataFingerprint']=$chineseMetadata} `
        -Expected 'PASS' -ExpectedWarnings @('METADATA_FINGERPRINT_DRIFT')
    Run-Case 'FAILED_ASSERTIONS_COMPLETE_SET' {param($e)$e['mysqlVersion']='5.7';$e['requestedPort']=34061;$e['actualListeningPort']=1;$e['schemaStructuralFingerprint']='bad';$e['roleRuntimeEnabled']=$true;$e['canaryEnabled']=$true;$e['migrationSnapshotFileCount']=0} 'FAIL' @('MYSQL_VERSION','PORT_IDENTITY','RC1_PORT_FORBIDDEN','STRUCTURAL_FINGERPRINT','ROLE_RUNTIME_DISABLED','CANARY_DISABLED','MIGRATION_SNAPSHOT_FILE_COUNT')

    Write-Output "ENVIRONMENT_ATTESTATION_CONTRACT_TESTS=$script:total"
    Write-Output "ENVIRONMENT_ATTESTATION_CONTRACT_TESTS_PASS=$script:passed"
    Write-Output "ENVIRONMENT_ATTESTATION_CONTRACT_TESTS_FAIL=$($script:total-$script:passed)"
    Write-Output "ISO_JSON_STRING_PRESERVATION_PS51=$(if($ps51Probe -ceq 'System.String|2026-01-01T00:00:00Z'){'PASS'}else{'FAIL'})"
    Write-Output "ISO_JSON_STRING_PRESERVATION_PS7=$(if($ps7Probe -ceq 'System.String|2026-01-01T00:00:00Z'){'PASS'}else{'FAIL'})"
    Write-Output "WILDCARD_LITERAL_COPY_REGRESSION=$(if($wildcardSafe -and $snapshotSafe){'PASS'}else{'FAIL'})"
    if($script:passed -ne $script:total){exit 2}
} finally {
    if(Test-Path -LiteralPath $root){[IO.Directory]::Delete($root,$true)}
}

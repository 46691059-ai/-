[CmdletBinding()]
param(
    [string]$OutputRoot = "D:\codex-rc2-v2622-$((Get-Date).ToString('yyyyMMdd-HHmmss'))",
    [int]$FreshPort = 38231,
    [int]$UpgradePort = 38232,
    [string]$MySqlHome = 'C:\Program Files\MySQL\MySQL Server 8.4',
    [string]$FlywayPath = $env:FLYWAY_COMMAND
)

$ErrorActionPreference = 'Stop'
$workspace = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$baselineSource = Join-Path $workspace 'database\mysql'
$migrationSource = Join-Path $workspace 'database\migration\mysql'
$inventoryPath = Join-Path $workspace 'database\flyway\migration-inventory.yml'
$shaManifestPath = Join-Path $migrationSource 'SHA256SUMS'
$outputFullPath = [IO.Path]::GetFullPath($OutputRoot)
$allowedRootPattern = '^D:\\codex-rc2-v2622-[A-Za-z0-9._-]+$'
$expectedCandidateSha = '658b4939a7e92295527ef3a5116d20814b0d17e4c34e46f3955e4c21c67dcc05'
$rc1TagCommit = 'bf70c588752f8b32c27f3112e2ddb5e8803b4db6'
$servers = [Collections.Generic.List[object]]::new()
$failures = [Collections.Generic.List[string]]::new()
$checks = [ordered]@{}

$baselineFiles = @(
    '01_database.sql',
    '02_sys.sql',
    '03_hr.sql',
    '04_party.sql',
    '05_project.sql',
    '06_operation.sql',
    '07_investment.sql',
    '08_data_asset.sql',
    '09_risk.sql',
    '10_init_data.sql',
    '11_sprint_1_user_permissions.sql',
    '12_sprint_1_org_permissions.sql',
    '13_sprint_1_role_permissions.sql',
    '14_sprint_1_rbac_acceptance.sql',
    '15_sprint_1_menu_center.sql',
    '16_sprint_1_log_center.sql',
    'V1.1.0__investment_data_risk_bi.sql'
)

function Stop-Preflight([string]$Message) {
    [Console]::Error.WriteLine($Message)
    Write-Output 'RC2_S5_1=FAIL'
    exit 2
}

if ($outputFullPath -notmatch $allowedRootPattern) {
    Stop-Preflight "Unsafe OutputRoot: $outputFullPath"
}
if (Test-Path -LiteralPath $outputFullPath) {
    Stop-Preflight "Disposable OutputRoot already exists: $outputFullPath"
}
if ($FreshPort -eq $UpgradePort -or $FreshPort -eq 34061 -or $UpgradePort -eq 34061) {
    Stop-Preflight 'Fresh/Upgrade ports must be distinct and must not use RC1 port 34061'
}
foreach ($port in @($FreshPort, $UpgradePort)) {
    if ($port -lt 1024 -or $port -gt 65535) {
        Stop-Preflight "Unsafe validation port: $port"
    }
    if (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue) {
        Stop-Preflight "Validation port is already in use: $port"
    }
}

$mysqld = Join-Path $MySqlHome 'bin\mysqld.exe'
$mysql = Join-Path $MySqlHome 'bin\mysql.exe'
$mysqladmin = Join-Path $MySqlHome 'bin\mysqladmin.exe'
foreach ($tool in @($mysqld, $mysql, $mysqladmin, $FlywayPath)) {
    if (-not (Test-Path -LiteralPath $tool -PathType Leaf)) {
        Stop-Preflight "Required tool is missing: $tool"
    }
}

$baselineCopy = Join-Path $outputFullPath 'baseline'
$migrationCopy = Join-Path $outputFullPath 'migration'
$migrationRc1Copy = Join-Path $outputFullPath 'migration-v2621'
$evidenceRoot = Join-Path $outputFullPath 'evidence'
New-Item -ItemType Directory -Path $baselineCopy, $migrationCopy, $migrationRc1Copy, $evidenceRoot -Force | Out-Null

function Write-Utf8NoBom([string]$Path, [string]$Content) {
    [IO.File]::WriteAllText($Path, $Content, [Text.UTF8Encoding]::new($false))
}

function Set-Check([string]$Name, [bool]$Passed, [string]$Proof = '') {
    $script:checks[$Name] = $Passed
    if (-not $Passed) {
        $script:failures.Add("$Name :: $Proof")
    }
}

function Invoke-Native {
    param(
        [Parameter(Mandatory)][string]$Executable,
        [Parameter(Mandatory)][string[]]$Arguments
    )
    $oldPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = @(& $Executable @Arguments 2>&1)
        $code = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $oldPreference
    }
    return [pscustomobject]@{
        ExitCode = $code
        Output = ($output -join "`n")
    }
}

function Invoke-Sql {
    param(
        [Parameter(Mandatory)]$Server,
        [Parameter(Mandatory)][string]$Sql,
        [switch]$NoHeaders,
        [switch]$WithoutDatabase
    )
    $arguments = @(
        '--protocol=TCP',
        '--host=127.0.0.1',
        "--port=$($Server.Port)",
        '--user=root',
        '--default-character-set=utf8mb4',
        '--batch',
        '--raw'
    )
    if ($NoHeaders) {
        $arguments += '--skip-column-names'
    }
    if (-not $WithoutDatabase) {
        $arguments += '--database=enterprise_platform'
    }
    $arguments += "--execute=$Sql"
    return Invoke-Native -Executable $mysql -Arguments $arguments
}

function Invoke-SqlFile {
    param(
        [Parameter(Mandatory)]$Server,
        [Parameter(Mandatory)][string]$Path,
        [switch]$WithoutDatabase
    )
    $arguments = @(
        '--protocol=TCP',
        '--host=127.0.0.1',
        "--port=$($Server.Port)",
        '--user=root',
        '--default-character-set=utf8mb4'
    )
    if (-not $WithoutDatabase) {
        $arguments += '--database=enterprise_platform'
    }
    $arguments += "--execute=source $($Path.Replace('\', '/'))"
    return Invoke-Native -Executable $mysql -Arguments $arguments
}

function Invoke-Rc2Flyway {
    param(
        [Parameter(Mandatory)]$Server,
        [Parameter(Mandatory)][ValidateSet('baseline', 'info', 'migrate', 'validate')][string]$Command,
        [Parameter(Mandatory)][string]$EvidenceName,
        [string]$Target = '',
        [string]$Location = $migrationCopy
    )
    $arguments = @(
        "-url=jdbc:mysql://127.0.0.1:$($Server.Port)/enterprise_platform",
        '-user=root',
        '-password=',
        "-locations=filesystem:$($Location.Replace('\', '/'))",
        '-table=flyway_schema_history',
        '-baselineVersion=2.0.0',
        '-baselineDescription=RC2 authoritative baseline',
        '-baselineOnMigrate=false',
        '-validateMigrationNaming=true',
        '-validateOnMigrate=true',
        '-cleanDisabled=true',
        '-outOfOrder=false',
        '-mixed=false',
        '-connectRetries=10'
    )
    if (-not [string]::IsNullOrWhiteSpace($Target)) {
        $arguments += "-target=$Target"
    }
    $arguments += $Command
    $result = Invoke-Native -Executable $FlywayPath -Arguments $arguments
    Write-Utf8NoBom -Path (Join-Path $evidenceRoot $EvidenceName) -Content ($result.Output + "`n")
    return $result
}

function Assert-RepositoryAssets {
    $rootSql = @(Get-ChildItem -LiteralPath $baselineSource -File -Filter '*.sql' |
        Sort-Object Name | Select-Object -ExpandProperty Name)
    $expectedRootSql = @($baselineFiles | Sort-Object)
    if (($rootSql -join "`n") -cne ($expectedRootSql -join "`n")) {
        throw 'Authoritative 17-file Baseline drift detected'
    }
    foreach ($name in $baselineFiles) {
        Copy-Item -LiteralPath (Join-Path $baselineSource $name) -Destination (Join-Path $baselineCopy $name)
    }

    $assets = @()
    foreach ($file in Get-ChildItem -LiteralPath $migrationSource -File -Filter 'V*.sql') {
        if ($file.Name -notmatch '^V(\d+\.\d+\.\d+)__(.+)\.sql$') {
            throw "Invalid Migration filename: $($file.Name)"
        }
        $assets += [pscustomobject]@{
            Name = $file.Name
            FullName = $file.FullName
            VersionText = $Matches[1]
            Version = [version]$Matches[1]
        }
    }
    $assets = @($assets | Sort-Object Version)
    $duplicates = @($assets | Group-Object VersionText | Where-Object Count -gt 1)
    if ($duplicates.Count -gt 0) {
        throw "Duplicate Migration version: $($duplicates.Name -join ', ')"
    }
    if ($assets[-1].VersionText -cne '2.6.22' -or $assets.Count -ne 44) {
        throw "Expected 44 Migrations ending at 2.6.22; actual=$($assets.Count)/$($assets[-1].VersionText)"
    }

    $inventory = Get-Content -LiteralPath $inventoryPath -Raw
    $manifestLines = @(Get-Content -LiteralPath $shaManifestPath |
        Where-Object { $_ -match '^[0-9a-f]{64}\s{2}\S+$' })
    if ($manifestLines.Count -ne $assets.Count) {
        throw "SHA manifest count mismatch: $($manifestLines.Count)/$($assets.Count)"
    }
    $manifestHashes = @{}
    foreach ($line in $manifestLines) {
        $parts = $line -split '\s{2}', 2
        $manifestHashes[$parts[1]] = $parts[0]
    }
    foreach ($asset in $assets) {
        $relative = "database/migration/mysql/$($asset.Name)"
        if (-not $inventory.Contains($relative)) {
            throw "Inventory missing Migration: $relative"
        }
        $actual = (Get-FileHash -LiteralPath $asset.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        if (-not $manifestHashes.ContainsKey($asset.Name) -or $manifestHashes[$asset.Name] -cne $actual) {
            throw "Migration SHA drift: $($asset.Name)"
        }
        Copy-Item -LiteralPath $asset.FullName -Destination (Join-Path $migrationCopy $asset.Name)
        if ($asset.Version -le [version]'2.6.21') {
            Copy-Item -LiteralPath $asset.FullName -Destination (Join-Path $migrationRc1Copy $asset.Name)
        }
    }
    $candidateSha = (Get-FileHash -LiteralPath (Join-Path $migrationCopy 'V2.6.22__extend_workflow_instance_role_resolver_binding.sql') -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($candidateSha -cne $expectedCandidateSha) {
        throw "V2.6.22 candidate SHA mismatch: $candidateSha"
    }
    return $assets
}

function Start-TemporaryDatabase([string]$Name, [int]$Port) {
    $serverRoot = Join-Path $outputFullPath $Name
    $dataRoot = Join-Path $serverRoot 'data'
    New-Item -ItemType Directory -Path $dataRoot -Force | Out-Null
    $iniPath = Join-Path $serverRoot 'my.ini'
    $errorLog = Join-Path $serverRoot 'mysql.err'
    $ini = @"
[mysqld]
basedir=$($MySqlHome.Replace('\', '/'))
datadir=$($dataRoot.Replace('\', '/'))
port=$Port
bind-address=127.0.0.1
mysqlx=0
skip-log-bin
character-set-server=utf8mb4
collation-server=utf8mb4_general_ci
log-error=$($errorLog.Replace('\', '/'))
pid-file=$((Join-Path $serverRoot 'mysql.pid').Replace('\', '/'))
secure-file-priv=""
"@
    Write-Utf8NoBom -Path $iniPath -Content $ini
    $initialize = Invoke-Native -Executable $mysqld -Arguments @("--defaults-file=$iniPath", '--initialize-insecure')
    if ($initialize.ExitCode -ne 0) {
        throw "MySQL initialize failed for ${Name}: $($initialize.Output)"
    }
    $process = Start-Process $mysqld -ArgumentList "--defaults-file=$iniPath" -WindowStyle Hidden -PassThru
    $server = [pscustomobject]@{
        Name = $Name
        Port = $Port
        Root = $serverRoot
        DataRoot = $dataRoot
        ErrorLog = $errorLog
        Process = $process
        Version = $null
    }
    $servers.Add($server)
    for ($attempt = 0; $attempt -lt 120; $attempt++) {
        $ping = Invoke-Native -Executable $mysqladmin -Arguments @(
            '--protocol=TCP', '--host=127.0.0.1', "--port=$Port", '--user=root', 'ping')
        if ($ping.ExitCode -eq 0) {
            $identity = Invoke-Sql -Server $server -Sql "SELECT CONCAT(@@port,'|',@@datadir,'|',VERSION())" -NoHeaders -WithoutDatabase
            if ($identity.ExitCode -ne 0) {
                throw "Temporary MySQL identity query failed: $($identity.Output)"
            }
            $parts = $identity.Output.Trim() -split '\|', 3
            $reportedData = [IO.Path]::GetFullPath(($parts[1] -replace '/', '\')).TrimEnd('\')
            if ([int]$parts[0] -ne $Port -or $reportedData -cne $dataRoot.TrimEnd('\')) {
                throw "Temporary MySQL identity mismatch: $($identity.Output)"
            }
            if ($parts[2] -notmatch '^8\.4\.') {
                throw "MySQL 8.4.x required, actual: $($parts[2])"
            }
            $server.Version = $parts[2]
            return $server
        }
        Start-Sleep -Milliseconds 250
    }
    throw "Temporary MySQL did not become ready: $Name"
}

function Initialize-Baseline($Server) {
    $log = [Text.StringBuilder]::new()
    foreach ($name in $baselineFiles) {
        $path = Join-Path $baselineCopy $name
        $result = Invoke-SqlFile -Server $Server -Path $path -WithoutDatabase:($name -eq '01_database.sql')
        [void]$log.AppendLine("===== $name =====")
        [void]$log.AppendLine($result.Output)
        [void]$log.AppendLine("exit_code=$($result.ExitCode)")
        if ($result.ExitCode -ne 0) {
            Write-Utf8NoBom -Path (Join-Path $evidenceRoot "$($Server.Name)-baseline.log") -Content $log.ToString()
            throw "Baseline failed on $($Server.Name): $name"
        }
    }
    Write-Utf8NoBom -Path (Join-Path $evidenceRoot "$($Server.Name)-baseline.log") -Content $log.ToString()
}

function Get-HistoryState($Server) {
    $query = @"
SELECT
  IFNULL((
    SELECT version
    FROM flyway_schema_history
    WHERE success=1 AND version IS NOT NULL
    ORDER BY installed_rank DESC
    LIMIT 1
  ),'<NULL>'),
  SUM(CASE WHEN success=0 THEN 1 ELSE 0 END),
  SUM(CASE WHEN success=1 AND version='2.6.22' THEN 1 ELSE 0 END),
  COUNT(*)
FROM flyway_schema_history
"@
    $result = Invoke-Sql -Server $Server -Sql $query -NoHeaders
    if ($result.ExitCode -ne 0) {
        throw "History state query failed: $($result.Output)"
    }
    $parts = $result.Output.Trim() -split "`t"
    return [pscustomobject]@{
        Latest = $parts[0]
        Failed = [int]$parts[1]
        CandidateCount = [int]$parts[2]
        Rows = [int]$parts[3]
    }
}

function Write-HistoryEvidence($Server) {
    $result = Invoke-Sql -Server $Server -Sql @"
SELECT installed_rank,version,description,type,script,checksum,success
FROM flyway_schema_history
ORDER BY installed_rank
"@
    if ($result.ExitCode -ne 0) {
        throw "History evidence query failed: $($result.Output)"
    }
    Write-Utf8NoBom -Path (Join-Path $evidenceRoot "$($Server.Name)-flyway-history.txt") -Content ($result.Output + "`n")
}

function Get-SchemaFingerprint($Server) {
    $queries = [ordered]@{
        TABLES = "SELECT CONCAT(table_name,'|',engine,'|',table_collation,'|',IFNULL(table_comment,'')) FROM information_schema.tables WHERE table_schema='enterprise_platform' AND table_type='BASE TABLE' ORDER BY table_name"
        COLUMNS = "SELECT CONCAT(table_name,'|',ordinal_position,'|',column_name,'|',column_type,'|',is_nullable,'|',IFNULL(column_default,'<NULL>'),'|',IFNULL(collation_name,'<NULL>'),'|',extra,'|',IFNULL(column_comment,'')) FROM information_schema.columns WHERE table_schema='enterprise_platform' ORDER BY table_name,ordinal_position"
        INDEXES = "SELECT CONCAT(table_name,'|',index_name,'|',non_unique,'|',seq_in_index,'|',IFNULL(column_name,'<NULL>'),'|',IFNULL(expression,'<NULL>')) FROM information_schema.statistics WHERE table_schema='enterprise_platform' ORDER BY table_name,index_name,seq_in_index"
        TRIGGERS = "SELECT CONCAT(trigger_name,'|',event_manipulation,'|',event_object_table,'|',action_timing,'|',action_statement) FROM information_schema.triggers WHERE trigger_schema='enterprise_platform' ORDER BY trigger_name"
        FOREIGN_KEYS = "SELECT CONCAT(constraint_name,'|',table_name,'|',referenced_table_name,'|',unique_constraint_name,'|',update_rule,'|',delete_rule) FROM information_schema.referential_constraints WHERE constraint_schema='enterprise_platform' ORDER BY table_name,constraint_name"
        CHECKS = "SELECT CONCAT(tc.table_name,'|',tc.constraint_name,'|',cc.check_clause) FROM information_schema.table_constraints tc JOIN information_schema.check_constraints cc ON cc.constraint_schema=tc.constraint_schema AND cc.constraint_name=tc.constraint_name WHERE tc.constraint_schema='enterprise_platform' AND tc.constraint_type='CHECK' ORDER BY tc.table_name,tc.constraint_name"
    }
    $text = [Text.StringBuilder]::new()
    foreach ($entry in $queries.GetEnumerator()) {
        $result = Invoke-Sql -Server $Server -Sql $entry.Value -NoHeaders
        if ($result.ExitCode -ne 0) {
            throw "Schema metadata query failed for $($entry.Key): $($result.Output)"
        }
        [void]$text.AppendLine("[$($entry.Key)]")
        [void]$text.AppendLine($result.Output)
    }
    $path = Join-Path $evidenceRoot "$($Server.Name)-schema.txt"
    Write-Utf8NoBom -Path $path -Content $text.ToString()
    return (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Assert-V2622Schema($Server) {
    $tableResult = Invoke-Sql -Server $Server -Sql @"
SELECT COUNT(*)
FROM information_schema.tables
WHERE table_schema='enterprise_platform'
  AND table_name IN (
    'workflow_version_node_resolver_binding',
    'workflow_version_resolver_binding_manifest'
  )
"@ -NoHeaders
    Set-Check "$($Server.Name)_NEW_TABLES" ($tableResult.ExitCode -eq 0 -and $tableResult.Output.Trim() -eq '2') $tableResult.Output

    $columnResult = Invoke-Sql -Server $Server -Sql @"
SELECT table_name,column_name,column_type,is_nullable,IFNULL(column_default,'<NULL>'),IFNULL(collation_name,'<NULL>'),extra,column_comment
FROM information_schema.columns
WHERE table_schema='enterprise_platform'
  AND table_name IN ('workflow_version','workflow_version_release')
  AND column_name IN (
    'resolver_binding_model','resolver_binding_manifest_hash',
    'resolver_binding_count','resolver_binding_canonical_version'
  )
ORDER BY table_name,ordinal_position
"@
    Write-Utf8NoBom -Path (Join-Path $evidenceRoot "$($Server.Name)-v2622-columns.txt") -Content ($columnResult.Output + "`n")
    $columnLines = @($columnResult.Output -split "`r?`n" | Where-Object { $_ -match '^workflow_version' })
    $columnContract = $columnResult.ExitCode -eq 0 -and $columnLines.Count -eq 8
    $columnContract = $columnContract -and @($columnLines | Where-Object {
        $_ -match "resolver_binding_model\tvarchar\(48\)\tNO\tLEGACY_USER_ONLY\tascii_bin"
    }).Count -eq 2
    $columnContract = $columnContract -and @($columnLines | Where-Object {
        $_ -match "resolver_binding_manifest_hash\tvarchar\(64\)\tYES\t<NULL>\tascii_bin"
    }).Count -eq 2
    $columnContract = $columnContract -and @($columnLines | Where-Object {
        $_ -match "resolver_binding_count\tint\tNO\t0\t<NULL>"
    }).Count -eq 2
    $columnContract = $columnContract -and @($columnLines | Where-Object {
        $_ -match "resolver_binding_canonical_version\tvarchar\(64\)\tYES\t<NULL>\tascii_bin"
    }).Count -eq 2
    Set-Check "$($Server.Name)_VERSION_RELEASE_COLUMNS" $columnContract $columnResult.Output

    $constraintResult = Invoke-Sql -Server $Server -Sql @"
SELECT constraint_type,COUNT(*)
FROM information_schema.table_constraints
WHERE constraint_schema='enterprise_platform'
  AND (
    table_name IN ('workflow_version_node_resolver_binding','workflow_version_resolver_binding_manifest')
    OR (table_name IN ('workflow_version','workflow_version_release') AND constraint_name LIKE '%resolver_binding%')
  )
GROUP BY constraint_type
ORDER BY constraint_type
"@
    Write-Utf8NoBom -Path (Join-Path $evidenceRoot "$($Server.Name)-v2622-constraints.txt") -Content ($constraintResult.Output + "`n")
    Set-Check "$($Server.Name)_CONSTRAINT_METADATA" ($constraintResult.ExitCode -eq 0 -and $constraintResult.Output -match 'CHECK' -and $constraintResult.Output -match 'FOREIGN KEY' -and $constraintResult.Output -match 'UNIQUE') $constraintResult.Output
}

function Invoke-Rejection {
    param(
        [Parameter(Mandatory)]$Server,
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][string]$Sql,
        [string]$Expected = ''
    )
    $result = Invoke-Sql -Server $Server -Sql "START TRANSACTION; $Sql; ROLLBACK;"
    $passed = $result.ExitCode -ne 0
    if (-not [string]::IsNullOrWhiteSpace($Expected)) {
        $passed = $passed -and $result.Output.Contains($Expected)
    }
    Set-Check $Name $passed $result.Output
    Write-Utf8NoBom -Path (Join-Path $evidenceRoot "$Name.txt") -Content ($result.Output + "`nexit_code=$($result.ExitCode)`n")
}

function Invoke-Acceptance {
    param(
        [Parameter(Mandatory)]$Server,
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][string]$Sql
    )
    $result = Invoke-Sql -Server $Server -Sql $Sql
    Set-Check $Name ($result.ExitCode -eq 0) $result.Output
    Write-Utf8NoBom -Path (Join-Path $evidenceRoot "$Name.txt") -Content ($result.Output + "`nexit_code=$($result.ExitCode)`n")
    return $result.ExitCode -eq 0
}

function Initialize-ConstraintFixtures($Server) {
    $sql = @"
INSERT INTO workflow_definition(id,definition_code,definition_name,business_type,enterprise_id,status)
VALUES
  (910001,'RC2_BINDING_D1','RC2 Binding Definition 1','RC2_TEST',900001,'DRAFT'),
  (910002,'RC2_BINDING_D2','RC2 Binding Definition 2','RC2_TEST',900002,'DRAFT');

INSERT INTO workflow_version(id,definition_id,version_no,status,schema_version)
VALUES
  (920001,910001,1,'DRAFT','NODE_V1'),
  (920002,910001,2,'DRAFT','NODE_V1'),
  (920003,910002,1,'DRAFT','NODE_V1');

INSERT INTO workflow_node(
  id,version_id,node_code,node_name,node_type,node_order,
  assignment_rule_type,assignment_rule_config
)
VALUES
  (930001,920001,'ROLE_A','Role Node A','APPROVAL',1,'USER','{}'),
  (930002,920002,'ROLE_B','Role Node B','APPROVAL',1,'USER','{}'),
  (930003,920003,'ROLE_C','Role Node C','APPROVAL',1,'USER','{}');

UPDATE workflow_version
SET resolver_binding_model='VERSION_RESOLVER_BINDING_CAPABLE'
WHERE id=920001;
"@
    if (-not (Invoke-Acceptance -Server $Server -Name 'FIXTURE_CREATE' -Sql $sql)) {
        throw 'Unable to create V2.6.22 constraint fixtures'
    }
}

function New-BindingSql {
    param(
        [long]$Id,
        [long]$DefinitionId,
        [long]$VersionId,
        [long]$NodeId,
        [int]$Order,
        [string]$Scope = 'FIXED_ORG',
        [string]$Organization = '900101',
        [string]$RoleCode = 'ROLE_APPROVER'
    )
    $organizationValue = if ($Organization -eq 'NULL') { 'NULL' } else { $Organization }
    return @"
INSERT INTO workflow_version_node_resolver_binding(
  id,definition_id,definition_version_id,node_id,binding_order,
  resolver_code,resolver_version,resolver_contract_hash,
  strategy_type,resolver_mode,target_type,role_code,
  organization_scope_type,organization_id,effective_time_policy,
  binding_schema_version,binding_hash
) VALUES(
  $Id,$DefinitionId,$VersionId,$NodeId,$Order,
  'ROLE_DIRECTORY','ROLE_DIRECTORY_V1',REPEAT('a',64),
  'ROLE','CANDIDATE_POOL','ROLE','$RoleCode',
  '$Scope',$organizationValue,'NODE_ACTIVATED_AT',
  'VERSION_NODE_RESOLVER_BINDING_V1',REPEAT('b',64)
)
"@
}

function Invoke-ConstraintAndTriggerMatrix($Server) {
    Initialize-ConstraintFixtures -Server $Server

    Invoke-Rejection $Server 'NEGATIVE_CROSS_DEFINITION_VERSION' (New-BindingSql 940001 910002 920001 930001 1)
    Invoke-Rejection $Server 'NEGATIVE_CROSS_VERSION_NODE' (New-BindingSql 940002 910001 920001 930002 1)
    Invoke-Rejection $Server 'NEGATIVE_BINDING_ORDER_ZERO' (New-BindingSql 940003 910001 920001 930001 0)
    Invoke-Rejection $Server 'NEGATIVE_FIXED_ORG_NULL' (New-BindingSql 940004 910001 920001 930001 1 'FIXED_ORG' 'NULL')
    Invoke-Rejection $Server 'NEGATIVE_INSTANCE_ORG_WITH_VALUE' (New-BindingSql 940005 910001 920001 930001 1 'INSTANCE_BUSINESS_ORG' '900101')
    Invoke-Rejection $Server 'NEGATIVE_LEGACY_COUNT' "UPDATE workflow_version SET resolver_binding_count=1 WHERE id=920002"
    Invoke-Rejection $Server 'NEGATIVE_LEGACY_MANIFEST_HASH' "UPDATE workflow_version SET resolver_binding_manifest_hash=REPEAT('c',64) WHERE id=920002"

    [void](Invoke-Acceptance $Server 'DRAFT_BINDING_INSERT' (New-BindingSql 940010 910001 920001 930001 1))
    Invoke-Rejection $Server 'NEGATIVE_DUPLICATE_BINDING_ORDER' (New-BindingSql 940011 910001 920001 930001 1)
    [void](Invoke-Acceptance $Server 'DRAFT_BINDING_UPDATE' "UPDATE workflow_version_node_resolver_binding SET role_code='ROLE_REVIEWER',updated_time=NOW(3),version=version+1 WHERE id=940010")
    [void](Invoke-Acceptance $Server 'DRAFT_BINDING_LOGICAL_DELETE' "UPDATE workflow_version_node_resolver_binding SET deleted=1,delete_token=id,version=version+1 WHERE id=940010")
    [void](Invoke-Acceptance $Server 'DRAFT_BINDING_RESTORE' "UPDATE workflow_version_node_resolver_binding SET deleted=0,delete_token=0,version=version+1 WHERE id=940010")
    [void](Invoke-Acceptance $Server 'DRAFT_SECOND_BINDING_INSERT' (New-BindingSql 940012 910001 920001 930001 2 'FIXED_ORG' '900101' 'ROLE_BACKUP'))
    [void](Invoke-Acceptance $Server 'DRAFT_SECOND_BINDING_DELETE' "UPDATE workflow_version_node_resolver_binding SET deleted=1,delete_token=id,version=version+1 WHERE id=940012")

    $prepareManifest = @"
UPDATE workflow_version
SET resolver_binding_manifest_hash=REPEAT('c',64),
    resolver_binding_count=1,
    resolver_binding_canonical_version='VERSION_RESOLVER_BINDING_MANIFEST_V1'
WHERE id=920001;

INSERT INTO workflow_version_resolver_binding_manifest(
  id,definition_id,definition_version_id,canonical_version,binding_count,
  manifest_hash,released_by,released_time
) VALUES(
  950001,910001,920001,'VERSION_RESOLVER_BINDING_MANIFEST_V1',1,
  REPEAT('c',64),700001,NOW(3)
);
"@
    [void](Invoke-Acceptance $Server 'MANIFEST_INSERT_WHILE_DRAFT' $prepareManifest)
    Invoke-Rejection $Server 'NEGATIVE_SECOND_MANIFEST' @"
INSERT INTO workflow_version_resolver_binding_manifest(
  id,definition_id,definition_version_id,canonical_version,binding_count,
  manifest_hash,released_by,released_time
) VALUES(
  950002,910001,920001,'VERSION_RESOLVER_BINDING_MANIFEST_V1',1,
  REPEAT('c',64),700001,NOW(3)
)
"@

    $publish = @"
START TRANSACTION;
UPDATE workflow_version
SET status='PUBLISHED',content_hash=REPEAT('d',64),published_by=700001,
    published_time=NOW(3),effective_from=NOW(3)
WHERE id=920001;
INSERT INTO workflow_version_release(
  id,definition_id,previous_version_id,published_version_id,published_version_no,
  content_hash,operator_user_id,operator_org_id,published_time,
  resolver_binding_model,resolver_binding_manifest_hash,resolver_binding_count,
  resolver_binding_canonical_version
) VALUES(
  960001,910001,NULL,920001,1,REPEAT('d',64),700001,900001,NOW(3),
  'VERSION_RESOLVER_BINDING_CAPABLE',REPEAT('c',64),1,
  'VERSION_RESOLVER_BINDING_MANIFEST_V1'
);
COMMIT;
"@
    [void](Invoke-Acceptance $Server 'PUBLISH_SEQUENCE_DRAFT_MANIFEST_PUBLISH_RELEASE' $publish)

    Invoke-Rejection $Server 'PUBLISHED_BINDING_INSERT_REJECTED' (New-BindingSql 940020 910001 920001 930001 3) 'WORKFLOW_VERSION_BINDING_REQUIRES_DRAFT_VERSION'
    Invoke-Rejection $Server 'PUBLISHED_BINDING_UPDATE_REJECTED' "UPDATE workflow_version_node_resolver_binding SET role_code='ROLE_CHANGED' WHERE id=940010" 'WORKFLOW_VERSION_BINDING_IMMUTABLE'
    Invoke-Rejection $Server 'PUBLISHED_BINDING_DELETE_REJECTED' "DELETE FROM workflow_version_node_resolver_binding WHERE id=940010" 'WORKFLOW_VERSION_BINDING_IMMUTABLE'
    Invoke-Rejection $Server 'PUBLISHED_BINDING_LOGICAL_DELETE_REJECTED' "UPDATE workflow_version_node_resolver_binding SET deleted=1,delete_token=id WHERE id=940010" 'WORKFLOW_VERSION_BINDING_IMMUTABLE'
    Invoke-Rejection $Server 'PUBLISHED_BINDING_RESTORE_REJECTED' "UPDATE workflow_version_node_resolver_binding SET deleted=0,delete_token=0 WHERE id=940012" 'WORKFLOW_VERSION_BINDING_IMMUTABLE'
    Invoke-Rejection $Server 'MANIFEST_UPDATE_REJECTED' "UPDATE workflow_version_resolver_binding_manifest SET remark='forbidden' WHERE id=950001" 'WORKFLOW_VERSION_BINDING_MANIFEST_APPEND_ONLY'
    Invoke-Rejection $Server 'MANIFEST_DELETE_REJECTED' "DELETE FROM workflow_version_resolver_binding_manifest WHERE id=950001" 'WORKFLOW_VERSION_BINDING_MANIFEST_APPEND_ONLY'
    [void](Invoke-Acceptance $Server 'VERSION_RETIRED' "UPDATE workflow_version SET status='RETIRED',effective_to=DATE_ADD(NOW(3),INTERVAL 1 DAY) WHERE id=920001")
    Invoke-Rejection $Server 'RETIRED_BINDING_INSERT_REJECTED' (New-BindingSql 940021 910001 920001 930001 3) 'WORKFLOW_VERSION_BINDING_REQUIRES_DRAFT_VERSION'
}

function Create-UpgradeLegacyRows($Server) {
    $sql = @"
INSERT INTO workflow_definition(id,definition_code,definition_name,business_type,enterprise_id,status)
VALUES (970001,'RC2_LEGACY','RC2 Legacy Definition','RC2_TEST',900001,'ACTIVE');
INSERT INTO workflow_version(
  id,definition_id,version_no,status,schema_version,content_hash,
  effective_from,published_by,published_time
) VALUES (
  970002,970001,1,'PUBLISHED','NODE_V1',REPEAT('e',64),NOW(3),700001,NOW(3)
);
INSERT INTO workflow_version_release(
  id,definition_id,previous_version_id,published_version_id,published_version_no,
  content_hash,operator_user_id,operator_org_id,published_time
) VALUES (
  970003,970001,NULL,970002,1,REPEAT('e',64),700001,900001,NOW(3)
);
"@
    if (-not (Invoke-Acceptance $Server 'UPGRADE_LEGACY_FIXTURE_BEFORE_V2622' $sql)) {
        throw 'Unable to create V2.6.20 legacy fixture'
    }
}

function Assert-UpgradeLegacyCompatibility($Server) {
    $sql = @"
SELECT CONCAT(
  v.resolver_binding_model,'|',v.resolver_binding_count,'|',
  IFNULL(v.resolver_binding_manifest_hash,'<NULL>'),'|',
  IFNULL(v.resolver_binding_canonical_version,'<NULL>'),'|',
  r.resolver_binding_model,'|',r.resolver_binding_count,'|',
  IFNULL(r.resolver_binding_manifest_hash,'<NULL>'),'|',
  IFNULL(r.resolver_binding_canonical_version,'<NULL>')
)
FROM workflow_version v
JOIN workflow_version_release r ON r.published_version_id=v.id
WHERE v.id=970002
"@
    $result = Invoke-Sql -Server $Server -Sql $sql -NoHeaders
    $expected = 'LEGACY_USER_ONLY|0|<NULL>|<NULL>|LEGACY_USER_ONLY|0|<NULL>|<NULL>'
    Set-Check 'LEGACY_COMPATIBILITY' ($result.ExitCode -eq 0 -and $result.Output.Trim() -ceq $expected) $result.Output
    Write-Utf8NoBom -Path (Join-Path $evidenceRoot 'upgrade-legacy-compatibility.txt') -Content ($result.Output + "`n")
}

function Assert-V2622InstanceSchema($Server) {
    $columns = Invoke-Sql -Server $Server -Sql @"
SELECT column_name,column_type,is_nullable,IFNULL(column_default,'<NULL>'),
       IFNULL(collation_name,'<NULL>'),extra,column_comment
FROM information_schema.columns
WHERE table_schema='enterprise_platform'
  AND table_name='workflow_node_resolver_binding_snapshot'
  AND column_name IN (
    'version_binding_id','version_binding_order','version_binding_slot',
    'version_binding_hash','resolver_contract_hash_snapshot','role_code',
    'organization_scope_type','resolved_organization_id',
    'effective_time_policy','binding_schema_version'
  )
ORDER BY ordinal_position
"@
    Write-Utf8NoBom -Path (Join-Path $evidenceRoot "$($Server.Name)-v2622-columns.txt") -Content ($columns.Output + "`n")
    $columnLines = @($columns.Output -split "`r?`n" | Where-Object { $_ -match '^version_binding_|^resolver_contract_|^role_code|^organization_scope_|^resolved_organization_|^effective_time_|^binding_schema_' })
    $columnContract = $columns.ExitCode -eq 0 -and $columnLines.Count -eq 10
    $columnContract = $columnContract -and $columns.Output -match "version_binding_slot\tint\tYES\t<NULL>\t<NULL>\tSTORED GENERATED"
    $columnContract = $columnContract -and $columns.Output -match "version_binding_hash\tvarchar\(64\)\tYES\t<NULL>\tascii_bin"
    $columnContract = $columnContract -and $columns.Output -match "resolver_contract_hash_snapshot\tvarchar\(64\)\tYES\t<NULL>\tascii_bin"
    $columnContract = $columnContract -and $columns.Output -match "role_code\tvarchar\(100\)\tYES\t<NULL>\tascii_bin"
    $columnContract = $columnContract -and $columns.Output -match "organization_scope_type\tvarchar\(40\)\tYES\t<NULL>\tascii_bin"
    $columnContract = $columnContract -and $columns.Output -match "resolved_organization_id\tbigint\tYES\t<NULL>\t<NULL>"
    $columnContract = $columnContract -and $columns.Output -match "effective_time_policy\tvarchar\(40\)\tYES\t<NULL>\tascii_bin"
    $columnContract = $columnContract -and $columns.Output -match "binding_schema_version\tvarchar\(64\)\tYES\t<NULL>\tascii_bin"
    Set-Check "$($Server.Name)_V2622_COLUMN_CONTRACT" $columnContract $columns.Output

    $manifest = Invoke-Sql -Server $Server -Sql @"
SELECT column_type,is_nullable,IFNULL(column_default,'<NULL>'),column_comment
FROM information_schema.columns
WHERE table_schema='enterprise_platform'
  AND table_name='workflow_instance_resolver_binding_set'
  AND column_name='manifest_version'
"@ -NoHeaders
    Set-Check "$($Server.Name)_MANIFEST_VERSION_64" ($manifest.ExitCode -eq 0 -and $manifest.Output -match '^varchar\(64\)\tNO\t<NULL>') $manifest.Output

    $indexes = Invoke-Sql -Server $Server -Sql @"
SELECT index_name,non_unique,GROUP_CONCAT(column_name ORDER BY seq_in_index)
FROM information_schema.statistics
WHERE table_schema='enterprise_platform'
  AND table_name='workflow_node_resolver_binding_snapshot'
  AND index_name IN (
    'uk_workflow_node_resolver_binding_order',
    'uk_workflow_node_resolver_version_binding',
    'idx_workflow_node_resolver_role_scope'
  )
GROUP BY index_name,non_unique
ORDER BY index_name
"@
    Write-Utf8NoBom -Path (Join-Path $evidenceRoot "$($Server.Name)-v2622-indexes.txt") -Content ($indexes.Output + "`n")
    $indexLines = @($indexes.Output -split "`r?`n" | Where-Object {
        $_ -match '^(uk_workflow_node_resolver_binding_order|uk_workflow_node_resolver_version_binding|idx_workflow_node_resolver_role_scope)\t'
    })
    Set-Check "$($Server.Name)_V2622_INDEXES" (
        $indexes.ExitCode -eq 0 -and $indexLines.Count -eq 3
    ) $indexes.Output

    $constraints = Invoke-Sql -Server $Server -Sql @"
SELECT constraint_name,constraint_type
FROM information_schema.table_constraints
WHERE constraint_schema='enterprise_platform'
  AND table_name='workflow_node_resolver_binding_snapshot'
  AND constraint_name IN (
    'fk_workflow_node_resolver_version_binding',
    'chk_workflow_node_resolver_version_source'
  )
ORDER BY constraint_name
"@
    Set-Check "$($Server.Name)_V2622_CONSTRAINTS" (
        $constraints.ExitCode -eq 0 -and
        $constraints.Output -match 'fk_workflow_node_resolver_version_binding' -and
        $constraints.Output -match 'chk_workflow_node_resolver_version_source'
    ) $constraints.Output

    $triggers = Invoke-Sql -Server $Server -Sql @"
SELECT trigger_name,event_manipulation,action_timing,action_statement
FROM information_schema.triggers
WHERE trigger_schema='enterprise_platform'
  AND event_object_table='workflow_node_resolver_binding_snapshot'
ORDER BY trigger_name
"@
    Write-Utf8NoBom -Path (Join-Path $evidenceRoot "$($Server.Name)-v2622-triggers.txt") -Content ($triggers.Output + "`n")
    $triggerLines = @($triggers.Output -split "`r?`n" | Where-Object {
        $_ -match '^trg_workflow_node_resolver_snapshot_no_(update|delete)\t'
    })
    Set-Check "$($Server.Name)_V2622_TRIGGERS" (
        $triggers.ExitCode -eq 0 -and $triggerLines.Count -eq 2 -and
        $triggers.Output -match 'trg_workflow_node_resolver_snapshot_no_update' -and
        $triggers.Output -match 'trg_workflow_node_resolver_snapshot_no_delete' -and
        $triggers.Output -match 'WORKFLOW_NODE_RESOLVER_SNAPSHOT_IMMUTABLE'
    ) $triggers.Output
}

function Initialize-V2622Fixtures($Server) {
    $sql = @"
INSERT INTO workflow_definition(id,definition_code,definition_name,business_type,enterprise_id,status)
VALUES (980001,'RC2_V2622','RC2 V2622 Definition','RC2_TEST',900001,'DRAFT');

INSERT INTO workflow_version(
  id,definition_id,version_no,status,schema_version,
  resolver_binding_model,resolver_binding_manifest_hash,
  resolver_binding_count,resolver_binding_canonical_version
) VALUES (
  980002,980001,1,'DRAFT','NODE_V1',
  'VERSION_RESOLVER_BINDING_CAPABLE',REPEAT('1',64),2,
  'VERSION_RESOLVER_BINDING_MANIFEST_V1'
);

INSERT INTO workflow_node(
  id,version_id,node_code,node_name,node_type,node_order,
  assignment_rule_type,assignment_rule_config
) VALUES (980003,980002,'ROLE_NODE','Role Node','APPROVAL',1,'USER','{}');

INSERT INTO workflow_version_node_resolver_binding(
  id,definition_id,definition_version_id,node_id,binding_order,
  resolver_code,resolver_version,resolver_contract_hash,
  strategy_type,resolver_mode,target_type,role_code,
  organization_scope_type,organization_id,effective_time_policy,
  binding_schema_version,binding_hash
) VALUES
  (980010,980001,980002,980003,1,'ROLE_DIRECTORY','ROLE_DIRECTORY_V1',REPEAT('2',64),
   'ROLE','CANDIDATE_POOL','ROLE','ROLE_APPROVER','FIXED_ORG',900101,'NODE_ACTIVATED_AT',
   'VERSION_NODE_RESOLVER_BINDING_V1',REPEAT('3',64)),
  (980011,980001,980002,980003,2,'ROLE_DIRECTORY','ROLE_DIRECTORY_V1',REPEAT('2',64),
   'ROLE','CANDIDATE_POOL','ROLE','ROLE_REVIEWER','FIXED_ORG',900101,'NODE_ACTIVATED_AT',
   'VERSION_NODE_RESOLVER_BINDING_V1',REPEAT('4',64));

INSERT INTO workflow_instance(
  id,instance_no,definition_id,version_id,definition_code_snapshot,
  definition_version_no,definition_content_hash_snapshot,business_type,
  business_id,business_key,enterprise_id,attempt_no,initiator_user_id,
  initiator_org_id,status,idempotency_key,request_hash
) VALUES
  (980020,'RC2-V2622-ROLE',980001,980002,'RC2_V2622',1,REPEAT('5',64),'RC2_TEST',
   'ROLE-1','RC2:ROLE-1',900001,1,700001,900101,'CREATED','RC2-V2622-ROLE',REPEAT('6',64)),
  (980021,'RC2-V2622-USER',980001,980002,'RC2_V2622',1,REPEAT('5',64),'RC2_TEST',
   'USER-1','RC2:USER-1',900001,1,700001,900101,'CREATED','RC2-V2622-USER',REPEAT('7',64));

INSERT INTO workflow_instance_resolver_binding_set(
  id,instance_id,definition_id,definition_version_id,manifest_version,
  manifest_hash,binding_count,binding_status,frozen_time,audit_info
) VALUES
  (980030,980020,980001,980002,'VERSION_RESOLVER_BINDING_MANIFEST_V1',REPEAT('1',64),2,'FROZEN',NOW(3),'RC2 V2622 ROLE'),
  (980031,980021,980001,980002,'LEGACY_USER_V1',REPEAT('8',64),1,'FROZEN',NOW(3),'RC2 V2622 USER');

INSERT INTO workflow_instance_resolver_binding(
  id,binding_set_id,instance_id,definition_version_id,resolver_code,resolver_version,
  strategy_type,resolver_mode,contract_hash,rule_hash,binding_status,frozen_time,audit_info
) VALUES
  (980040,980030,980020,980002,'ROLE_DIRECTORY','ROLE_DIRECTORY_V1','ROLE','CANDIDATE_POOL',REPEAT('2',64),REPEAT('9',64),'FROZEN',NOW(3),'RC2 ROLE'),
  (980041,980031,980021,980002,'EXPLICIT_USER','EXPLICIT_USER_V1','USER','DIRECT',REPEAT('a',64),REPEAT('b',64),'FROZEN',NOW(3),'RC2 USER');
"@
    if (-not (Invoke-Acceptance $Server 'V2622_FIXTURE_CREATE' $sql)) {
        throw 'Unable to create V2.6.22 fixtures'
    }
}

function New-V2622SnapshotSql {
    param(
        [long]$Id,
        [long]$InstanceId = 980020,
        [long]$BindingSetId = 980030,
        [long]$ResolverBindingId = 980040,
        [string]$Strategy = 'ROLE',
        [string]$Target = 'ROLE',
        [string]$Mode = 'CANDIDATE_POOL',
        [string]$VersionBindingId = '980010',
        [string]$BindingOrder = '1',
        [string]$BindingHash = "REPEAT('3',64)",
        [string]$ContractHash = "REPEAT('2',64)",
        [string]$RoleCode = "'ROLE_APPROVER'",
        [string]$Scope = "'FIXED_ORG'",
        [string]$Organization = '900101',
        [string]$EffectivePolicy = "'NODE_ACTIVATED_AT'",
        [string]$SchemaVersion = "'VERSION_NODE_RESOLVER_BINDING_V1'"
    )
    return @"
INSERT INTO workflow_node_resolver_binding_snapshot(
  id,binding_set_id,resolver_binding_id,instance_id,definition_version_id,
  node_id,node_code_snapshot,version_binding_id,version_binding_order,
  version_binding_hash,resolver_contract_hash_snapshot,role_code,
  organization_scope_type,resolved_organization_id,effective_time_policy,
  binding_schema_version,strategy_type,resolver_mode,target_type,
  target_value_snapshot,rule_version,rule_snapshot,rule_hash,node_binding_hash,
  binding_status,frozen_time,audit_info
) VALUES(
  $Id,$BindingSetId,$ResolverBindingId,$InstanceId,980002,
  980003,'ROLE_NODE',$VersionBindingId,$BindingOrder,
  $BindingHash,$ContractHash,$RoleCode,$Scope,$Organization,$EffectivePolicy,
  $SchemaVersion,'$Strategy','$Mode','$Target',
  'RC2_TARGET','RULE_V1','{}',REPEAT('c',64),REPEAT('d',64),
  'FROZEN',NOW(3),'RC2 V2622 snapshot'
)
"@
}

function Invoke-V2622Matrix($Server) {
    Initialize-V2622Fixtures -Server $Server

    [void](Invoke-Acceptance $Server 'ROLE_ROLE_CANDIDATE_POOL_PASS' (New-V2622SnapshotSql -Id 980050))
    [void](Invoke-Acceptance $Server 'MULTI_BINDING_ORDER_2_PASS' (New-V2622SnapshotSql -Id 980051 -VersionBindingId 980011 -BindingOrder 2 -BindingHash "REPEAT('4',64)" -RoleCode "'ROLE_REVIEWER'"))

    $legacy = New-V2622SnapshotSql -Id 980052 -InstanceId 980021 -BindingSetId 980031 -ResolverBindingId 980041 `
        -Strategy USER -Target USER -Mode DIRECT -VersionBindingId NULL -BindingOrder NULL `
        -BindingHash NULL -ContractHash NULL -RoleCode NULL -Scope NULL -Organization NULL `
        -EffectivePolicy NULL -SchemaVersion NULL
    [void](Invoke-Acceptance $Server 'USER_USER_DIRECT_PASS' $legacy)

    Invoke-Rejection $Server 'ROLE_USER_REJECTED' (New-V2622SnapshotSql -Id 980060 -Target USER)
    Invoke-Rejection $Server 'USER_ROLE_REJECTED' (New-V2622SnapshotSql -Id 980061 -Strategy USER -Target ROLE)
    Invoke-Rejection $Server 'ROLE_DIRECT_REJECTED' (New-V2622SnapshotSql -Id 980062 -Mode DIRECT)
    Invoke-Rejection $Server 'USER_CANDIDATE_POOL_REJECTED' (New-V2622SnapshotSql -Id 980063 -Strategy USER -Target USER)
    Invoke-Rejection $Server 'ILLEGAL_RESOLVER_MODE_REJECTED' (New-V2622SnapshotSql -Id 980064 -Mode INVALID)
    Invoke-Rejection $Server 'ILLEGAL_TARGET_STRATEGY_REJECTED' (New-V2622SnapshotSql -Id 980065 -Target ORG)
    Invoke-Rejection $Server 'FIXED_ORG_NULL_REJECTED' (New-V2622SnapshotSql -Id 980066 -Organization NULL)
    Invoke-Rejection $Server 'ILLEGAL_SCOPE_REJECTED' (New-V2622SnapshotSql -Id 980067 -Scope "'INSTANCE_BUSINESS_ORG'")
    Invoke-Rejection $Server 'DUPLICATE_NODE_ORDER_REJECTED' (New-V2622SnapshotSql -Id 980068)

    Invoke-Rejection $Server 'SNAPSHOT_UPDATE_REJECTED' "UPDATE workflow_node_resolver_binding_snapshot SET remark='forbidden' WHERE id=980050" 'WORKFLOW_NODE_RESOLVER_SNAPSHOT_IMMUTABLE'
    Invoke-Rejection $Server 'SNAPSHOT_LOGICAL_DELETE_REJECTED' "UPDATE workflow_node_resolver_binding_snapshot SET deleted=1,delete_token=id WHERE id=980050" 'WORKFLOW_NODE_RESOLVER_SNAPSHOT_IMMUTABLE'
    Invoke-Rejection $Server 'SNAPSHOT_DELETE_REJECTED' "DELETE FROM workflow_node_resolver_binding_snapshot WHERE id=980050" 'WORKFLOW_NODE_RESOLVER_SNAPSHOT_IMMUTABLE'

    $residue = Invoke-Sql -Server $Server -Sql "SELECT COUNT(*) FROM workflow_node_resolver_binding_snapshot WHERE id BETWEEN 980060 AND 980068" -NoHeaders
    Set-Check 'NEGATIVE_MATRIX_NO_RESIDUE' ($residue.ExitCode -eq 0 -and $residue.Output.Trim() -eq '0') $residue.Output

    $legacyRead = Invoke-Sql -Server $Server -Sql @"
SELECT CONCAT(strategy_type,'|',target_type,'|',resolver_mode,'|',
  IFNULL(version_binding_id,'<NULL>'),'|',IFNULL(role_code,'<NULL>'),'|',
  IFNULL(organization_scope_type,'<NULL>'),'|',IFNULL(resolved_organization_id,'<NULL>'))
FROM workflow_node_resolver_binding_snapshot WHERE id=980052
"@ -NoHeaders
    Set-Check 'LEGACY_USER_MYSQL_COMPATIBILITY' (
        $legacyRead.ExitCode -eq 0 -and
        $legacyRead.Output.Trim() -ceq 'USER|USER|DIRECT|<NULL>|<NULL>|<NULL>|<NULL>'
    ) $legacyRead.Output
}

function Stop-TemporaryDatabases {
    foreach ($server in $servers) {
        $shutdown = Invoke-Native -Executable $mysqladmin -Arguments @(
            '--protocol=TCP', '--host=127.0.0.1', "--port=$($server.Port)", '--user=root', 'shutdown')
        if (-not $server.Process.HasExited) {
            $server.Process.WaitForExit(10000) | Out-Null
        }
        if (-not $server.Process.HasExited) {
            Stop-Process -Id $server.Process.Id -Force -ErrorAction SilentlyContinue
            $failures.Add("Forced MySQL termination: $($server.Name); $($shutdown.Output)")
        }
        if (Test-Path -LiteralPath $server.ErrorLog) {
            Copy-Item -LiteralPath $server.ErrorLog -Destination (Join-Path $evidenceRoot "$($server.Name)-mysql.err")
        }
    }
}

function Remove-TemporaryDatadirs {
    foreach ($server in $servers) {
        $serverRootFull = [IO.Path]::GetFullPath($server.Root).TrimEnd('\')
        $expectedPrefix = $outputFullPath.TrimEnd('\') + '\'
        if (-not $serverRootFull.StartsWith($expectedPrefix, [StringComparison]::OrdinalIgnoreCase)) {
            $failures.Add("Refused unsafe cleanup target: $serverRootFull")
            continue
        }
        if ((Split-Path -Leaf $serverRootFull) -notin @('fresh', 'upgrade')) {
            $failures.Add("Refused unexpected cleanup leaf: $serverRootFull")
            continue
        }
        if (Test-Path -LiteralPath $serverRootFull) {
            Remove-Item -LiteralPath $serverRootFull -Recurse -Force
        }
    }
}

$fresh = $null
$upgrade = $null
$freshState = $null
$upgradeState = $null
$freshFingerprint = $null
$upgradeFingerprint = $null
$freshNoopState = 'FAIL'
$upgradeNoopState = 'FAIL'
$flywayChecksum = $null
$candidateSha = $null

try {
    $assets = @(Assert-RepositoryAssets)
    $candidateSha = (Get-FileHash -LiteralPath (Join-Path $migrationSource 'V2.6.22__extend_workflow_instance_role_resolver_binding.sql') -Algorithm SHA256).Hash.ToLowerInvariant()
    Set-Check 'MIGRATION_SHA_44_OF_44' $true 'Repository asset preflight passed'

    $tagResult = Invoke-Native -Executable 'git' -Arguments @('rev-list', '-n', '1', 'workflow-v1.0.0-rc1')
    Set-Check 'RC1_TAG_UNCHANGED' ($tagResult.ExitCode -eq 0 -and $tagResult.Output.Trim() -ceq $rc1TagCommit) $tagResult.Output
    if (-not $checks.RC1_TAG_UNCHANGED) {
        throw 'RC1 Tag drift detected'
    }

    $flywayVersion = Invoke-Native -Executable $FlywayPath -Arguments @('-v')
    if ($flywayVersion.ExitCode -ne 0 -or $flywayVersion.Output -notmatch '13\.0\.0') {
        throw "Flyway 13.0.0 required: $($flywayVersion.Output)"
    }

    $fresh = Start-TemporaryDatabase -Name 'fresh' -Port $FreshPort
    $upgrade = Start-TemporaryDatabase -Name 'upgrade' -Port $UpgradePort
    Initialize-Baseline -Server $fresh
    Initialize-Baseline -Server $upgrade
    Set-Check 'FRESH_BASELINE_17' $true '17 Baseline files'
    Set-Check 'UPGRADE_BASELINE_17' $true '17 Baseline files'

    $freshBaseline = Invoke-Rc2Flyway $fresh baseline 'fresh-flyway-baseline.log'
    if ($freshBaseline.ExitCode -ne 0) { throw "Fresh baseline failed: $($freshBaseline.Output)" }
    $freshMigrate = Invoke-Rc2Flyway $fresh migrate 'fresh-flyway-migrate.log'
    if ($freshMigrate.ExitCode -ne 0) { throw "Fresh migrate failed: $($freshMigrate.Output)" }
    $freshValidate = Invoke-Rc2Flyway $fresh validate 'fresh-flyway-validate.log'
    Set-Check 'FRESH_STRICT_VALIDATE' ($freshValidate.ExitCode -eq 0 -and $freshValidate.Output -match '(?i)successfully validated|validation successful') $freshValidate.Output
    $freshNoop = Invoke-Rc2Flyway $fresh migrate 'fresh-flyway-noop.log'
    $freshNoopState = if ($freshNoop.ExitCode -eq 0 -and $freshNoop.Output -match '(?i)No migration necessary|schema.*up to date') { 'NO_OP' } else { 'FAIL' }
    Set-Check 'FRESH_SECOND_MIGRATE_NOOP' ($freshNoopState -eq 'NO_OP') $freshNoop.Output
    $freshState = Get-HistoryState -Server $fresh
    Set-Check 'FRESH_HISTORY' ($freshState.Latest -ceq '2.6.22' -and $freshState.Failed -eq 0 -and $freshState.CandidateCount -eq 1) ($freshState | ConvertTo-Json -Compress)
    Write-HistoryEvidence -Server $fresh
    Assert-V2622Schema -Server $fresh
    Assert-V2622InstanceSchema -Server $fresh

    $upgradeBaseline = Invoke-Rc2Flyway -Server $upgrade -Command baseline -EvidenceName 'upgrade-flyway-baseline.log' -Location $migrationRc1Copy
    if ($upgradeBaseline.ExitCode -ne 0) { throw "Upgrade baseline failed: $($upgradeBaseline.Output)" }
    $upgradeRc1 = Invoke-Rc2Flyway -Server $upgrade -Command migrate -EvidenceName 'upgrade-flyway-to-v2620.log' -Location $migrationRc1Copy
    if ($upgradeRc1.ExitCode -ne 0) { throw "Upgrade source migrate failed: $($upgradeRc1.Output)" }
    $upgradeSourceValidate = Invoke-Rc2Flyway -Server $upgrade -Command validate -EvidenceName 'upgrade-flyway-v2620-validate.log' -Location $migrationRc1Copy
    Set-Check 'UPGRADE_SOURCE_VALIDATE' ($upgradeSourceValidate.ExitCode -eq 0) $upgradeSourceValidate.Output
    $sourceState = Get-HistoryState -Server $upgrade
    Set-Check 'UPGRADE_SOURCE_VERSION' ($sourceState.Latest -ceq '2.6.21' -and $sourceState.CandidateCount -eq 0) ($sourceState | ConvertTo-Json -Compress)
    Create-UpgradeLegacyRows -Server $upgrade
    $beforeUpgradeRows = $sourceState.Rows

    $upgradeMigrate = Invoke-Rc2Flyway $upgrade migrate 'upgrade-flyway-v2622-migrate.log'
    if ($upgradeMigrate.ExitCode -ne 0) { throw "Upgrade V2.6.22 migrate failed: $($upgradeMigrate.Output)" }
    $upgradeValidate = Invoke-Rc2Flyway $upgrade validate 'upgrade-flyway-v2622-validate.log'
    Set-Check 'UPGRADE_STRICT_VALIDATE' ($upgradeValidate.ExitCode -eq 0 -and $upgradeValidate.Output -match '(?i)successfully validated|validation successful') $upgradeValidate.Output
    $upgradeNoop = Invoke-Rc2Flyway $upgrade migrate 'upgrade-flyway-noop.log'
    $upgradeNoopState = if ($upgradeNoop.ExitCode -eq 0 -and $upgradeNoop.Output -match '(?i)No migration necessary|schema.*up to date') { 'NO_OP' } else { 'FAIL' }
    Set-Check 'UPGRADE_SECOND_MIGRATE_NOOP' ($upgradeNoopState -eq 'NO_OP') $upgradeNoop.Output
    $upgradeState = Get-HistoryState -Server $upgrade
    Set-Check 'UPGRADE_HISTORY' ($upgradeState.Latest -ceq '2.6.22' -and $upgradeState.Failed -eq 0 -and $upgradeState.CandidateCount -eq 1 -and $upgradeState.Rows -eq ($beforeUpgradeRows + 1)) ($upgradeState | ConvertTo-Json -Compress)
    Write-HistoryEvidence -Server $upgrade
    Assert-V2622Schema -Server $upgrade
    Assert-V2622InstanceSchema -Server $upgrade
    Assert-UpgradeLegacyCompatibility -Server $upgrade

    $checksumResult = Invoke-Sql -Server $fresh -Sql "SELECT checksum FROM flyway_schema_history WHERE version='2.6.22' AND success=1" -NoHeaders
    if ($checksumResult.ExitCode -ne 0 -or $checksumResult.Output.Trim() -notmatch '^-?\d+$') {
        throw "Unable to read V2.6.22 Flyway checksum: $($checksumResult.Output)"
    }
    $flywayChecksum = [int]$checksumResult.Output.Trim()

    Invoke-ConstraintAndTriggerMatrix -Server $fresh
    Invoke-V2622Matrix -Server $fresh

    $freshFingerprint = Get-SchemaFingerprint -Server $fresh
    $upgradeFingerprint = Get-SchemaFingerprint -Server $upgrade
    Set-Check 'FRESH_UPGRADE_SCHEMA_MATCH' ($freshFingerprint -ceq $upgradeFingerprint) "$freshFingerprint/$upgradeFingerprint"
}
catch {
    $failures.Add($_.Exception.Message)
}
finally {
    Stop-TemporaryDatabases
    Remove-TemporaryDatadirs

    $result = if ($failures.Count -eq 0 -and @($checks.Values | Where-Object { -not $_ }).Count -eq 0) { 'PASS' } else { 'FAIL' }
    $summary = [ordered]@{
        result = $result
        output_root = $outputFullPath
        fresh_port = $FreshPort
        upgrade_port = $UpgradePort
        fresh_mysql_version = if ($fresh) { $fresh.Version } else { $null }
        upgrade_mysql_version = if ($upgrade) { $upgrade.Version } else { $null }
        fresh_latest_migration = if ($freshState) { $freshState.Latest } else { $null }
        fresh_failed_migrations = if ($freshState) { $freshState.Failed } else { $null }
        fresh_second_migrate = $freshNoopState
        fresh_schema_fingerprint = $freshFingerprint
        upgrade_source_version = '2.6.21'
        upgrade_target_version = if ($upgradeState) { $upgradeState.Latest } else { $null }
        upgrade_failed_migrations = if ($upgradeState) { $upgradeState.Failed } else { $null }
        upgrade_second_migrate = $upgradeNoopState
        upgrade_schema_fingerprint = $upgradeFingerprint
        v2622_sha256 = $candidateSha
        v2622_flyway_checksum = $flywayChecksum
        role_runtime = 'DISABLED'
        canary = 'NOT_AUTHORIZED_NOT_ENABLED'
        kill_switch = 'STOP_NEW_AND_CLAIM'
        rc1_test_database_touched = $false
        temporary_datadirs_removed = $true
        checks = $checks
        failures = @($failures)
    }
    Write-Utf8NoBom -Path (Join-Path $evidenceRoot 'summary.json') -Content (($summary | ConvertTo-Json -Depth 8) + "`n")

    foreach ($entry in $checks.GetEnumerator()) {
        Write-Output "$($entry.Key)=$(if ($entry.Value) { 'PASS' } else { 'FAIL' })"
    }
    Write-Output "V2622_MIGRATION_SHA256=$candidateSha"
    Write-Output "FRESH_MYSQL_VERSION=$(if ($fresh) { $fresh.Version } else { '' })"
    Write-Output "FRESH_LATEST_MIGRATION=$(if ($freshState) { $freshState.Latest } else { '' })"
    Write-Output "FRESH_FAILED_MIGRATIONS=$(if ($freshState) { $freshState.Failed } else { '' })"
    Write-Output "FRESH_SECOND_MIGRATE=$freshNoopState"
    Write-Output "FRESH_SCHEMA_FINGERPRINT=$freshFingerprint"
    Write-Output "UPGRADE_MYSQL_VERSION=$(if ($upgrade) { $upgrade.Version } else { '' })"
    Write-Output 'UPGRADE_SOURCE_VERSION=2.6.21'
    Write-Output "UPGRADE_TARGET_VERSION=$(if ($upgradeState) { $upgradeState.Latest } else { '' })"
    Write-Output "UPGRADE_FAILED_MIGRATIONS=$(if ($upgradeState) { $upgradeState.Failed } else { '' })"
    Write-Output "UPGRADE_SECOND_MIGRATE=$upgradeNoopState"
    Write-Output "UPGRADE_SCHEMA_FINGERPRINT=$upgradeFingerprint"
    Write-Output "V2622_FLYWAY_CHECKSUM=$flywayChecksum"
    Write-Output "EVIDENCE_ROOT=$evidenceRoot"
    Write-Output "RC2_S5_1=$result"
    if ($result -ne 'PASS') {
        $failures | ForEach-Object { Write-Error $_ }
        exit 2
    }
    exit 0
}

param(
    [string]$OutputRoot = "$env:LOCALAPPDATA\Temp\enterprise-v264-validation-$((Get-Date).ToString('yyyyMMdd-HHmmss'))"
)

$ErrorActionPreference = 'Stop'
$workspace = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$mysqlHome = 'C:\Program Files\MySQL\MySQL Server 8.4'
$mysqld = Join-Path $mysqlHome 'bin\mysqld.exe'
$mysql = Join-Path $mysqlHome 'bin\mysql.exe'
$mysqladmin = Join-Path $mysqlHome 'bin\mysqladmin.exe'
$flyway = 'C:\Users\WUKONG\AppData\Local\Temp\enterprise-v246-acceptance-68bf692b16d34ee191946695ae0bef9b\flyway-fresh\flyway-13.0.0\flyway.cmd'
$repoMigrationSource = Join-Path $workspace 'database\migration\mysql'
$fingerprintSql = Join-Path $workspace 'database\mysql\verification\schema_fingerprint.sql'
$baselineFiles = @(
    '01_database.sql','02_sys.sql','03_hr.sql','04_party.sql','05_project.sql',
    '06_operation.sql','07_investment.sql','08_data_asset.sql','09_risk.sql',
    '10_init_data.sql','11_sprint_1_user_permissions.sql','12_sprint_1_org_permissions.sql',
    '13_sprint_1_role_permissions.sql','14_sprint_1_rbac_acceptance.sql',
    '15_sprint_1_menu_center.sql','16_sprint_1_log_center.sql','V1.1.0__investment_data_risk_bi.sql'
)

foreach ($required in @($mysqld,$mysql,$mysqladmin,$flyway,$fingerprintSql)) {
    if (-not (Test-Path -LiteralPath $required)) { throw "Required tool/file missing: $required" }
}

New-Item -ItemType Directory -Path $OutputRoot -Force | Out-Null
$evidence = Join-Path $OutputRoot 'evidence'
$migrationSource = Join-Path $OutputRoot 'migrations'
$baselineSource = Join-Path $OutputRoot 'baseline'
New-Item -ItemType Directory -Path $evidence,$migrationSource,$baselineSource -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $repoMigrationSource 'V*.sql') -Destination $migrationSource -ErrorAction SilentlyContinue
if (-not (Get-ChildItem -LiteralPath $migrationSource -File -Filter 'V*.sql')) {
    Get-ChildItem -LiteralPath $repoMigrationSource -File -Filter 'V*.sql' | Copy-Item -Destination $migrationSource
}
foreach ($name in $baselineFiles) {
    Copy-Item -LiteralPath (Join-Path $workspace "database\mysql\$name") -Destination (Join-Path $baselineSource $name)
}
$serverProcesses = @()

function Invoke-Checked([string]$Label, [scriptblock]$Action) {
    & $Action
    if ($LASTEXITCODE -ne 0) { throw "$Label failed with exit code $LASTEXITCODE" }
}

function New-IsolatedMysql([string]$Name, [int]$Port) {
    $root = Join-Path $OutputRoot $Name
    $data = Join-Path $root 'data'
    $logs = Join-Path $root 'logs'
    New-Item -ItemType Directory -Path $data,$logs -Force | Out-Null
    $ini = Join-Path $root 'my.ini'
    $iniBody = @"
[mysqld]
basedir=C:/Program Files/MySQL/MySQL Server 8.4
datadir=$($data.Replace('\','/'))
port=$Port
bind-address=127.0.0.1
mysqlx=0
skip-log-bin
character-set-server=utf8mb4
collation-server=utf8mb4_general_ci
log-error=$((Join-Path $logs 'mysql.err').Replace('\','/'))
pid-file=$((Join-Path $root 'mysql.pid').Replace('\','/'))
secure-file-priv=""
"@
    [IO.File]::WriteAllText($ini, $iniBody, [Text.UTF8Encoding]::new($false))
    Invoke-Checked "initialize $Name" { & $mysqld "--defaults-file=$ini" --initialize-insecure }
    $process = Start-Process -FilePath $mysqld -ArgumentList "--defaults-file=$ini" -WindowStyle Hidden -PassThru
    $script:serverProcesses += $process
    $ready = $false
    foreach ($attempt in 1..60) {
        $previousPreference = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        & $mysqladmin --protocol=TCP --host=127.0.0.1 --port=$Port --user=root ping 2>$null | Out-Null
        $ErrorActionPreference = $previousPreference
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Milliseconds 500
    }
    if (-not $ready) { throw "$Name MySQL did not become ready" }
    return @{ Name=$Name; Port=$Port; Root=$root }
}

function Invoke-MysqlFile($Server, [string]$File, [string]$Database = '') {
    $args = @('--protocol=TCP','--host=127.0.0.1',"--port=$($Server.Port)",'--user=root','--default-character-set=utf8mb4')
    if ($Database) { $args += "--database=$Database" }
    $sourcePath = (Resolve-Path -LiteralPath $File).Path.Replace('\','/')
    & $mysql @args "--execute=source $sourcePath"
    if ($LASTEXITCODE -ne 0) { throw "mysql file failed: $File" }
}

function Invoke-MysqlQuery($Server, [string]$Sql, [switch]$Raw) {
    $args = @('--protocol=TCP','--host=127.0.0.1',"--port=$($Server.Port)",'--user=root','--database=enterprise_platform')
    if ($Raw) { $args += @('--batch','--raw','--skip-column-names') }
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $result = & $mysql @args --execute=$Sql 2>&1
    $exit = $LASTEXITCODE
    $ErrorActionPreference = $previousPreference
    return @{ ExitCode=$exit; Output=($result -join "`n") }
}

function Initialize-Baseline($Server) {
    foreach ($name in $baselineFiles) {
        $database = if ($name -eq '01_database.sql') { '' } else { 'enterprise_platform' }
        Invoke-MysqlFile $Server (Join-Path $baselineSource $name) $database
    }
}

function Invoke-Flyway($Server, [string]$Command, [string]$Target = '') {
    $args = @(
        "-url=jdbc:mysql://127.0.0.1:$($Server.Port)/enterprise_platform",
        '-user=root','-password=',"-locations=filesystem:$($migrationSource.Replace('\','/'))",
        '-baselineOnMigrate=true','-baselineVersion=2.0.0','-validateMigrationNaming=true',
        '-cleanDisabled=true','-connectRetries=10'
    )
    if ($Target) { $args += "-target=$Target" }
    $args += $Command
    $log = Join-Path $evidence "$($Server.Name)-flyway-$Command$(if($Target){"-$Target"}).log"
    & $flyway @args 2>&1 | Tee-Object -FilePath $log
    if ($LASTEXITCODE -ne 0) { throw "Flyway $Command failed for $($Server.Name)" }
}

function Write-Fingerprint($Server, [string]$Suffix) {
    $allFile = Join-Path $evidence "$($Server.Name)-schema-$Suffix.txt"
    $workflowFile = Join-Path $evidence "$($Server.Name)-workflow-schema-$Suffix.txt"
    $args = @('--protocol=TCP','--host=127.0.0.1',"--port=$($Server.Port)",'--user=root','--database=enterprise_platform','--batch','--raw','--skip-column-names')
    $all = Get-Content -LiteralPath $fingerprintSql -Raw | & $mysql @args
    if ($LASTEXITCODE -ne 0) { throw 'full schema fingerprint query failed' }
    $allText = ($all -join "`n") + "`n"
    [IO.File]::WriteAllText($allFile,$allText,[Text.UTF8Encoding]::new($false))
    $workflow = ($all | Where-Object { $_ -match '^\w+\|workflow_' })
    $workflowText = ($workflow -join "`n") + "`n"
    [IO.File]::WriteAllText($workflowFile,$workflowText,[Text.UTF8Encoding]::new($false))
    return @{
        Full=(Get-FileHash $allFile -Algorithm SHA256).Hash.ToLowerInvariant()
        Workflow=(Get-FileHash $workflowFile -Algorithm SHA256).Hash.ToLowerInvariant()
    }
}

try {
    $fresh = New-IsolatedMysql 'fresh' 34764
    $upgrade = New-IsolatedMysql 'upgrade-from-v262' 34765
    $failedV263 = New-IsolatedMysql 'failed-v263-repair' 34766

    Initialize-Baseline $fresh
    Initialize-Baseline $upgrade
    Initialize-Baseline $failedV263

    Invoke-Flyway $fresh 'migrate' '2.6.4'
    Invoke-Flyway $fresh 'validate'
    Invoke-Flyway $fresh 'migrate' '2.6.4'

    Invoke-Flyway $upgrade 'migrate' '2.6.2'
    Invoke-Flyway $failedV263 'migrate' '2.6.2'

    $fixture = @"
START TRANSACTION;
INSERT INTO workflow_definition (id,definition_code,definition_name,business_type,enterprise_id,owner_org_id,status,current_version_id,created_by,updated_by,deleted,delete_token,version)
VALUES (92630000,'V263_ACCEPTANCE','V2.6.3 resolver acceptance','ACCEPTANCE',9263,9263,'DRAFT',NULL,'acceptance','acceptance',0,0,0);
INSERT INTO workflow_version (id,definition_id,version_no,status,schema_version,engine_mode,content_hash_algorithm,content_hash,effective_from,published_by,published_time,created_by,updated_by,deleted,delete_token,version)
VALUES (92630001,92630000,1,'PUBLISHED','2.0','MULTI_NODE_LINEAR_V1','GRAPH_V2_SHA256',REPEAT('a',64),CURRENT_TIMESTAMP(3),926300,CURRENT_TIMESTAMP(3),'acceptance','acceptance',0,0,0);
UPDATE workflow_definition SET status='ACTIVE',current_version_id=92630001,version=1 WHERE id=92630000;
INSERT INTO workflow_instance (id,instance_no,definition_id,version_id,definition_code_snapshot,definition_version_no,definition_content_hash_snapshot,business_type,business_id,business_key,enterprise_id,attempt_no,initiator_user_id,initiator_org_id,current_node_id,engine_mode,content_hash_algorithm_snapshot,current_node_execution_id,status,idempotency_key,request_hash,event_sequence,trace_id,started_time,created_by,updated_by,deleted,delete_token,version)
VALUES
(92630030,'WFI-V263-LINEAR',92630000,92630001,'V263_ACCEPTANCE',1,REPEAT('a',64),'ACCEPTANCE','LINEAR-1','ACCEPTANCE:LINEAR-1',9263,1,926300,9263,NULL,'MULTI_NODE_LINEAR_V1','GRAPH_V2_SHA256',NULL,'RUNNING','v263-linear',REPEAT('b',64),0,'v263-trace',CURRENT_TIMESTAMP(3),'acceptance','acceptance',0,0,0),
(92630031,'WFI-V263-LEGACY',92630000,92630001,'V263_ACCEPTANCE',1,REPEAT('a',64),'ACCEPTANCE','LEGACY-1','ACCEPTANCE:LEGACY-1',9263,1,926300,9263,NULL,'SINGLE_NODE_LEGACY','NODE_V1_SHA256',NULL,'RUNNING','v263-legacy',REPEAT('c',64),0,'v263-trace',CURRENT_TIMESTAMP(3),'acceptance','acceptance',0,0,0);
COMMIT;
"@
    $fixtureFile = Join-Path $evidence 'pre-v263-fixture.sql'
    [IO.File]::WriteAllText($fixtureFile,$fixture,[Text.UTF8Encoding]::new($false))
    Invoke-MysqlFile $upgrade $fixtureFile 'enterprise_platform'
    Invoke-MysqlFile $failedV263 $fixtureFile 'enterprise_platform'

    Invoke-Flyway $upgrade 'migrate' '2.6.4'
    Invoke-Flyway $upgrade 'validate'
    Invoke-Flyway $upgrade 'migrate' '2.6.4'

    Invoke-Flyway $failedV263 'migrate' '2.6.3'
    $uppercaseSeed = Invoke-MysqlQuery $failedV263 "UPDATE workflow_instance SET resolver_contract_hash=REPEAT('A',64) WHERE id=92630030"
    if ($uppercaseSeed.ExitCode -ne 0) { throw 'V2.6.3 failure fixture was not accepted as expected' }
    Invoke-Flyway $failedV263 'migrate' '2.6.4'
    Invoke-Flyway $failedV263 'validate'
    Invoke-Flyway $failedV263 'migrate' '2.6.4'

    $historySql = "SELECT installed_rank,version,description,checksum,success FROM flyway_schema_history ORDER BY installed_rank"
    foreach ($server in @($fresh,$upgrade,$failedV263)) {
        (Invoke-MysqlQuery $server $historySql -Raw).Output | Set-Content (Join-Path $evidence "$($server.Name)-history.tsv") -Encoding utf8
        $schemaSql = @"
SELECT ordinal_position,column_name,column_type,is_nullable,COALESCE(column_default,'<NULL>'),extra
FROM information_schema.columns WHERE table_schema='enterprise_platform' AND table_name='workflow_instance' ORDER BY ordinal_position;
SELECT index_name,non_unique,GROUP_CONCAT(column_name ORDER BY seq_in_index) columns_list
FROM information_schema.statistics WHERE table_schema='enterprise_platform' AND table_name='workflow_instance' GROUP BY index_name,non_unique ORDER BY index_name;
SELECT tc.constraint_name,tc.constraint_type,COALESCE(cc.check_clause,'')
FROM information_schema.table_constraints tc LEFT JOIN information_schema.check_constraints cc ON cc.constraint_schema=tc.constraint_schema AND cc.constraint_name=tc.constraint_name
WHERE tc.table_schema='enterprise_platform' AND tc.table_name='workflow_instance' ORDER BY tc.constraint_type,tc.constraint_name;
"@
        (Invoke-MysqlQuery $server $schemaSql -Raw).Output | Set-Content (Join-Path $evidence "$($server.Name)-workflow-instance-structure.tsv") -Encoding utf8
    }

    $binding = Invoke-MysqlQuery $upgrade "SELECT instance_no,engine_mode,COALESCE(resolver_code,'NULL'),COALESCE(resolver_version,'NULL'),COALESCE(resolver_contract_hash,'NULL') FROM workflow_instance WHERE id IN (92630030,92630031) ORDER BY id" -Raw
    $binding.Output | Set-Content (Join-Path $evidence 'upgrade-binding-results.tsv') -Encoding utf8
    if ($binding.Output -notmatch 'WFI-V263-LINEAR.*EXPLICIT_USER.*EXPLICIT_USER_V1.*65873eb742d0a20b68f1a5e69cbc8e002eb6de26cfc7b516cbad494aa5ff5b6d') { throw 'linear resolver backfill mismatch' }
    if ($binding.Output -notmatch 'WFI-V263-LEGACY.*NULL.*NULL.*NULL') { throw 'legacy resolver compatibility mismatch' }

    $validLowercase = Invoke-MysqlQuery $upgrade "UPDATE workflow_instance SET resolver_contract_hash='65873eb742d0a20b68f1a5e69cbc8e002eb6de26cfc7b516cbad494aa5ff5b6d' WHERE id=92630030"
    if ($validLowercase.ExitCode -ne 0) { throw 'valid lowercase SHA-256 was rejected' }
    $negativeCases = @(
        @{Name='uppercase_hash'; Sql="UPDATE workflow_instance SET resolver_contract_hash=REPEAT('A',64) WHERE id=92630030"},
        @{Name='mixed_case_hash'; Sql="UPDATE workflow_instance SET resolver_contract_hash=CONCAT('A',REPEAT('a',63)) WHERE id=92630030"},
        @{Name='invalid_length'; Sql="UPDATE workflow_instance SET resolver_contract_hash='abc' WHERE id=92630030"},
        @{Name='non_hex_character'; Sql="UPDATE workflow_instance SET resolver_contract_hash=CONCAT(REPEAT('a',63),'g') WHERE id=92630030"}
    )
    $negativeLog = @()
    foreach ($case in $negativeCases) {
        $result = Invoke-MysqlQuery $upgrade $case.Sql
        $passed = $result.ExitCode -ne 0
        $negativeLog += "$($case.Name)`t$(if($passed){'PASS'}else{'FAIL'})`t$($result.Output.Replace("`n",' '))"
        if (-not $passed) { throw "negative test did not reject: $($case.Name)" }
    }
    $negativeLog | Set-Content (Join-Path $evidence 'negative-tests.tsv') -Encoding utf8

    $repaired = Invoke-MysqlQuery $failedV263 "SELECT resolver_contract_hash FROM workflow_instance WHERE id=92630030" -Raw
    if ($repaired.Output.Trim() -ne 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa') { throw 'V2.6.3 uppercase repair normalization failed' }

    $freshHash = Write-Fingerprint $fresh 'final'
    $upgradeHash = Write-Fingerprint $upgrade 'final'
    $repairHash = Write-Fingerprint $failedV263 'final'
    if ($freshHash.Full -ne $upgradeHash.Full -or $freshHash.Full -ne $repairHash.Full -or
        $freshHash.Workflow -ne $upgradeHash.Workflow -or $freshHash.Workflow -ne $repairHash.Workflow) {
        throw 'Fresh/Upgrade/Repair schema fingerprint mismatch'
    }

    $checksum = (Invoke-MysqlQuery $fresh "SELECT checksum FROM flyway_schema_history WHERE version='2.6.4' AND success=1" -Raw).Output.Trim()
    $summary = [ordered]@{
        output_root=$OutputRoot
        mysql_version=((& $mysqld --version) -join ' ')
        flyway_version=((& $flyway -v 2>$null | Select-Object -First 1) -join ' ')
        fresh_port=$fresh.Port
        upgrade_port=$upgrade.Port
        repair_port=$failedV263.Port
        v264_sha256=(Get-FileHash (Join-Path $repoMigrationSource 'V2.6.4__fix_resolver_contract_hash_collation.sql') -Algorithm SHA256).Hash.ToLowerInvariant()
        v264_flyway_checksum=$checksum
        full_schema_fingerprint=$freshHash.Full
        workflow_schema_fingerprint=$freshHash.Workflow
        fresh_history_success=[int](Invoke-MysqlQuery $fresh "SELECT COUNT(*) FROM flyway_schema_history WHERE success=1" -Raw).Output.Trim()
        upgrade_history_success=[int](Invoke-MysqlQuery $upgrade "SELECT COUNT(*) FROM flyway_schema_history WHERE success=1" -Raw).Output.Trim()
        repair_history_success=[int](Invoke-MysqlQuery $failedV263 "SELECT COUNT(*) FROM flyway_schema_history WHERE success=1" -Raw).Output.Trim()
        failed_history=[int](Invoke-MysqlQuery $fresh "SELECT COUNT(*) FROM flyway_schema_history WHERE success=0" -Raw).Output.Trim() + [int](Invoke-MysqlQuery $upgrade "SELECT COUNT(*) FROM flyway_schema_history WHERE success=0" -Raw).Output.Trim() + [int](Invoke-MysqlQuery $failedV263 "SELECT COUNT(*) FROM flyway_schema_history WHERE success=0" -Raw).Output.Trim()
        workflow_instance_columns=[int](Invoke-MysqlQuery $fresh "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='enterprise_platform' AND table_name='workflow_instance'" -Raw).Output.Trim()
        workflow_instance_indexes=[int](Invoke-MysqlQuery $fresh "SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics WHERE table_schema='enterprise_platform' AND table_name='workflow_instance'" -Raw).Output.Trim()
        workflow_instance_checks=[int](Invoke-MysqlQuery $fresh "SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema='enterprise_platform' AND table_name='workflow_instance' AND constraint_type='CHECK'" -Raw).Output.Trim()
        result='PASS'
    }
    $summary | ConvertTo-Json | Set-Content (Join-Path $evidence 'summary.json') -Encoding utf8
    $summary | ConvertTo-Json
}
finally {
    foreach ($process in $script:serverProcesses) {
        if ($process -and -not $process.HasExited) { Stop-Process -Id $process.Id -Force }
    }
    Get-CimInstance Win32_Process -Filter "Name='mysqld.exe'" |
        Where-Object { $_.CommandLine -like "*$OutputRoot*" } |
        ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
}

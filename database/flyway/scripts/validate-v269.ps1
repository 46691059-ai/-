param(
    [string]$OutputRoot = "D:\codex-validation-v269-$((Get-Date).ToString('yyyyMMdd-HHmmss'))",
    [int]$FreshPort = 35101,
    [int]$UpgradePort = 35102
)

$ErrorActionPreference = 'Stop'
$workspace = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$mysqlHome = 'C:\Program Files\MySQL\MySQL Server 8.4'
$mysqld = Join-Path $mysqlHome 'bin\mysqld.exe'
$mysql = Join-Path $mysqlHome 'bin\mysql.exe'
$mysqladmin = Join-Path $mysqlHome 'bin\mysqladmin.exe'
$flyway = 'C:\Users\WUKONG\AppData\Local\Temp\enterprise-v246-acceptance-68bf692b16d34ee191946695ae0bef9b\flyway-fresh\flyway-13.0.0\flyway.cmd'
$repoMigrations = Join-Path $workspace 'database\migration\mysql'
$fingerprintSql = Join-Path $workspace 'database\mysql\verification\schema_fingerprint.sql'
$baselineFiles = @(
    '01_database.sql','02_sys.sql','03_hr.sql','04_party.sql','05_project.sql',
    '06_operation.sql','07_investment.sql','08_data_asset.sql','09_risk.sql',
    '10_init_data.sql','11_sprint_1_user_permissions.sql','12_sprint_1_org_permissions.sql',
    '13_sprint_1_role_permissions.sql','14_sprint_1_rbac_acceptance.sql',
    '15_sprint_1_menu_center.sql','16_sprint_1_log_center.sql','V1.1.0__investment_data_risk_bi.sql'
)

foreach ($asset in @($mysqld,$mysql,$mysqladmin,$flyway,$fingerprintSql)) {
    if (-not (Test-Path -LiteralPath $asset)) { throw "Missing validation asset: $asset" }
}
if (Test-Path -LiteralPath $OutputRoot) { throw "Output root exists: $OutputRoot" }

$evidence = Join-Path $OutputRoot 'evidence'
$fullMigrations = Join-Path $OutputRoot 'migrations-full'
$v268Migrations = Join-Path $OutputRoot 'migrations-v268'
$baselineSource = Join-Path $OutputRoot 'baseline'
New-Item -ItemType Directory -Path $evidence,$fullMigrations,$v268Migrations,$baselineSource -Force | Out-Null
Get-ChildItem $repoMigrations -Filter 'V*.sql' -File | Copy-Item -Destination $fullMigrations
Get-ChildItem $repoMigrations -Filter 'V*.sql' -File |
    Where-Object Name -ne 'V2.6.9__create_role_runtime_snapshot.sql' |
    Copy-Item -Destination $v268Migrations
foreach ($name in $baselineFiles) {
    Copy-Item (Join-Path $workspace "database\mysql\$name") (Join-Path $baselineSource $name)
}

$servers = @()
$failures = @()

function New-IsolatedMysql([string]$Name,[int]$Port) {
    if (Get-NetTCPConnection -LocalPort $Port -ErrorAction SilentlyContinue) {
        throw "Port in use: $Port"
    }
    $root = Join-Path $OutputRoot $Name
    $data = Join-Path $root 'data'
    $logs = Join-Path $root 'logs'
    New-Item -ItemType Directory -Path $data,$logs -Force | Out-Null
    $ini = Join-Path $root 'my.ini'
    $body = @"
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
    [IO.File]::WriteAllText($ini,$body,[Text.UTF8Encoding]::new($false))
    & $mysqld "--defaults-file=$ini" --initialize-insecure
    if ($LASTEXITCODE -ne 0) { throw "Initialize failed: $Name" }
    $process = Start-Process $mysqld -ArgumentList "--defaults-file=$ini" -WindowStyle Hidden -PassThru
    $script:servers += $process
    foreach ($attempt in 1..100) {
        $old = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
        & $mysqladmin --protocol=TCP --host=127.0.0.1 --port=$Port --user=root ping 2>$null | Out-Null
        $code = $LASTEXITCODE; $ErrorActionPreference = $old
        if ($code -eq 0) { return @{Name=$Name;Port=$Port;Root=$root} }
        Start-Sleep -Milliseconds 300
    }
    throw "$Name did not become ready"
}

function Invoke-Mysql($Server,[string]$Sql,[switch]$Raw,[string]$Database='enterprise_platform') {
    $args = @('--protocol=TCP','--host=127.0.0.1',"--port=$($Server.Port)",'--user=root')
    if ($Database) { $args += "--database=$Database" }
    if ($Raw) { $args += @('--batch','--raw','--skip-column-names') }
    $old = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
    $output = & $mysql @args "--execute=$Sql" 2>&1
    $code = $LASTEXITCODE; $ErrorActionPreference = $old
    return @{ExitCode=$code;Output=($output -join "`n")}
}

function Invoke-MysqlFile($Server,[string]$File,[string]$Database='') {
    $args = @('--protocol=TCP','--host=127.0.0.1',"--port=$($Server.Port)",'--user=root','--default-character-set=utf8mb4')
    if ($Database) { $args += "--database=$Database" }
    & $mysql @args "--execute=source $((Resolve-Path $File).Path.Replace('\','/'))"
    if ($LASTEXITCODE -ne 0) { throw "mysql source failed: $File" }
}

function Initialize-Baseline($Server) {
    foreach ($name in $baselineFiles) {
        Invoke-MysqlFile $Server (Join-Path $baselineSource $name) $(if($name -eq '01_database.sql'){''}else{'enterprise_platform'})
    }
}

function Invoke-Flyway($Server,[string]$Location,[string]$Command,[string]$Target='') {
    $args = @(
        "-url=jdbc:mysql://127.0.0.1:$($Server.Port)/enterprise_platform",
        '-user=root','-password=',"-locations=filesystem:$($Location.Replace('\','/'))",
        '-baselineOnMigrate=true','-baselineVersion=2.0.0','-validateMigrationNaming=true',
        '-cleanDisabled=true','-connectRetries=10'
    )
    if ($Target) { $args += "-target=$Target" }
    $args += $Command
    $log = Join-Path $evidence "$($Server.Name)-$Command$(if($Target){"-$Target"})-$([DateTime]::UtcNow.Ticks).log"
    $sw = [Diagnostics.Stopwatch]::StartNew()
    $old = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
    $output = & $flyway @args 2>&1
    $code = $LASTEXITCODE; $ErrorActionPreference = $old; $sw.Stop()
    $output | Set-Content $log -Encoding utf8
    "elapsed_ms=$($sw.Elapsed.TotalMilliseconds)" | Add-Content $log
    if ($code -ne 0) { throw "Flyway $Command failed for $($Server.Name): $($output -join ' ')" }
    return @{Output=($output -join "`n");ElapsedMs=$sw.Elapsed.TotalMilliseconds}
}

function Write-Fingerprint($Server) {
    $args = @('--protocol=TCP','--host=127.0.0.1',"--port=$($Server.Port)",'--user=root',
        '--database=enterprise_platform','--batch','--raw','--skip-column-names')
    $rows = Get-Content $fingerprintSql -Raw | & $mysql @args
    if ($LASTEXITCODE -ne 0) { throw 'base fingerprint failed' }
    $triggerSql = "SELECT CONCAT('TRIGGER|',trigger_name,'|',event_manipulation,'|',event_object_table,'|',action_timing,'|',REPLACE(REPLACE(REPLACE(action_statement,'|','\\|'),CHAR(13),' '),CHAR(10),' ')) FROM information_schema.triggers WHERE trigger_schema=DATABASE() ORDER BY trigger_name"
    $triggers = & $mysql @args "--execute=$triggerSql"
    if ($LASTEXITCODE -ne 0) { throw 'trigger fingerprint failed' }
    $all = @($rows) + @($triggers)
    $workflow = $all | Where-Object { $_ -match '^\w+\|workflow_' -or $_ -match '^\w+\|role_runtime_' -or $_ -match '^TRIGGER\|.*\|(workflow_|role_runtime_)' }
    $role = $all | Where-Object { $_ -match '^\w+\|role_runtime_binding_approval\|' -or $_ -match '^\w+\|workflow_role_runtime_binding_snapshot\|' -or $_ -match '^TRIGGER\|.*\|(role_runtime_binding_approval|workflow_role_runtime_binding_snapshot)' }
    $result = @{}
    foreach ($pair in @(@('full',$all),@('workflow',$workflow),@('role',$role))) {
        $file = Join-Path $evidence "$($Server.Name)-$($pair[0])-schema.txt"
        [IO.File]::WriteAllText($file,(($pair[1] -join "`n")+"`n"),[Text.UTF8Encoding]::new($false))
        $result[$pair[0]] = (Get-FileHash $file -Algorithm SHA256).Hash.ToLowerInvariant()
    }
    return $result
}

function Add-WorkflowFixture($Server,[long]$Base,[string]$HashDigit,[switch]$InsertSnapshot) {
    $d=$Base; $v=$Base+1; $n=$Base+2; $i=$Base+3; $set=$Base+4
    $binding=$Base+5; $nodeBinding=$Base+6; $approval=$Base+7; $snapshot=$Base+8
    $proposal="REPEAT('$HashDigit',64)"
    $bindingHash="REPEAT('9',64)"
    $sql=@"
INSERT INTO workflow_definition(id,definition_code,definition_name,business_type,enterprise_id,owner_org_id,status,current_version_id,created_by,updated_by,deleted,delete_token,version)
VALUES($d,'WF_ROLE_$Base','ROLE persistence validation','ACCEPTANCE',269,269,'DRAFT',NULL,'acceptance','acceptance',0,0,0);
INSERT INTO workflow_version(id,definition_id,version_no,status,schema_version,engine_mode,content_hash_algorithm,content_hash,effective_from,published_by,published_time,created_by,updated_by,deleted,delete_token,version)
VALUES($v,$d,1,'PUBLISHED','2.0','MULTI_NODE_LINEAR_V1','GRAPH_V2_SHA256',REPEAT('1',64),NOW(3),269,NOW(3),'acceptance','acceptance',0,0,0);
INSERT INTO workflow_node(id,version_id,node_code,node_name,node_type,node_order,governance_node_type,approval_mode,assignment_rule_type,assignment_rule_config,withdraw_allowed,enabled,created_by,updated_by,deleted,delete_token,version)
VALUES($n,$v,'ROLE_NODE_$Base','ROLE Node','APPROVAL',1,'GENERAL_APPROVAL','SINGLE','USER','{"userId":269}',0,1,'acceptance','acceptance',0,0,0);
UPDATE workflow_definition SET status='ACTIVE',current_version_id=$v,version=1 WHERE id=$d;
INSERT INTO workflow_instance(id,instance_no,definition_id,version_id,definition_code_snapshot,definition_version_no,definition_content_hash_snapshot,resolver_code,resolver_version,resolver_contract_hash,business_type,business_id,business_key,enterprise_id,attempt_no,initiator_user_id,initiator_org_id,current_node_id,engine_mode,content_hash_algorithm_snapshot,current_node_execution_id,status,idempotency_key,request_hash,event_sequence,trace_id,started_time,created_by,updated_by,deleted,delete_token,version)
VALUES($i,'WFI-$Base',$d,$v,'WF_ROLE_$Base',1,REPEAT('1',64),'EXPLICIT_USER','EXPLICIT_USER_V1','3166103d7697a6f2e46932214b0447310c69d6b624f6d92a282e9ef3ab496853','ACCEPTANCE','$i','ACCEPTANCE:$i',269,1,269,269,$n,'MULTI_NODE_LINEAR_V1','GRAPH_V2_SHA256',NULL,'RUNNING','fixture-$Base',REPEAT('2',64),0,'trace-$Base',NOW(3),'acceptance','acceptance',0,0,0);
INSERT INTO workflow_instance_resolver_binding_set(id,instance_id,definition_id,definition_version_id,manifest_version,manifest_hash,binding_count,binding_status,frozen_time,audit_info,created_by,updated_by,deleted,delete_token,version)
VALUES($set,$i,$d,$v,'MULTI_RESOLVER_V1',REPEAT('3',64),1,'FROZEN',NOW(3),'acceptance','acceptance','acceptance',0,0,0);
INSERT INTO workflow_instance_resolver_binding(id,binding_set_id,instance_id,definition_version_id,resolver_code,resolver_version,strategy_type,resolver_mode,contract_hash,rule_hash,binding_status,frozen_time,audit_info,created_by,updated_by,deleted,delete_token,version)
VALUES($binding,$set,$i,$v,'ROLE_DIRECTORY','ROLE_DIRECTORY_V1','ROLE','CANDIDATE_POOL',REPEAT('a',64),REPEAT('b',64),'FROZEN',NOW(3),'acceptance','acceptance','acceptance',0,0,0);
INSERT INTO workflow_node_resolver_binding_snapshot(id,binding_set_id,resolver_binding_id,instance_id,definition_version_id,node_id,node_code_snapshot,strategy_type,resolver_mode,target_type,target_value_snapshot,rule_version,rule_snapshot,rule_hash,node_binding_hash,binding_status,frozen_time,audit_info,created_by,updated_by,deleted,delete_token,version)
VALUES($nodeBinding,$set,$binding,$i,$v,$n,'ROLE_NODE_$Base','ROLE','CANDIDATE_POOL','ROLE','{"roleCode":"INVESTMENT_REVIEWER"}','RULE_V1','{"roleCode":"INVESTMENT_REVIEWER"}',REPEAT('b',64),REPEAT('c',64),'FROZEN',NOW(3),'acceptance','acceptance','acceptance',0,0,0);
INSERT INTO role_runtime_binding_approval(id,proposal_hash,eligibility_hash,resolver_code,resolver_version,contract_hash,status,approved_by,approved_at,reject_reason,audit_info,created_by,updated_by,deleted,delete_token,version)
VALUES($approval,$proposal,REPEAT('e',64),'ROLE_DIRECTORY','ROLE_DIRECTORY_V1',REPEAT('a',64),'APPROVED','governance-admin',NOW(3),NULL,'acceptance','acceptance','acceptance',0,0,0);
"@
    if ($InsertSnapshot) {
        $sql += @"
INSERT INTO workflow_role_runtime_binding_snapshot(id,approval_id,binding_set_id,resolver_binding_id,node_resolver_binding_id,instance_id,definition_version_id,node_id,resolver_code,resolver_version,contract_hash,role_code,organization_id,directory_revision,effective_at,directory_hash,role_rule_hash,candidate_rule_hash,source_evidence_hash,binding_hash,status,audit_info,created_by,updated_by,deleted,delete_token,version)
VALUES($snapshot,$approval,$set,$binding,$nodeBinding,$i,$v,$n,'ROLE_DIRECTORY','ROLE_DIRECTORY_V1',REPEAT('a',64),'INVESTMENT_REVIEWER','ORG-001',12,NOW(3),REPEAT('b',64),REPEAT('c',64),REPEAT('d',64),REPEAT('e',64),$bindingHash,'FROZEN','acceptance','acceptance','acceptance',0,0,0);
"@
    }
    $result=Invoke-Mysql $Server $sql
    if($result.ExitCode -ne 0){throw "Fixture failed: $($result.Output)"}
    return @{Definition=$d;Version=$v;Node=$n;Instance=$i;Set=$set;Binding=$binding;NodeBinding=$nodeBinding;Approval=$approval;Snapshot=$snapshot}
}

function Expect-Rejected($Server,[string]$Name,[string]$Sql,[ref]$Log,[string]$Expected) {
    $result=Invoke-Mysql $Server "START TRANSACTION; $Sql; ROLLBACK;"
    $pass=$result.ExitCode -ne 0
    $code='';$state='';if($result.Output -match 'ERROR\s+(\d+)\s+\(([0-9A-Z]+)\)'){$code=$matches[1];$state=$matches[2]}
    $Log.Value += "$Name`t$(if($pass){'PASS'}else{'FAIL'})`t$code`t$state`t$Expected`t$($result.Output.Replace("`n",' '))"
    if(-not $pass){$script:failures += "Database accepted forbidden operation: $Name"}
}

try {
    $fresh=New-IsolatedMysql 'fresh' $FreshPort
    $upgrade=New-IsolatedMysql 'upgrade' $UpgradePort
    foreach($server in @($fresh,$upgrade)){Initialize-Baseline $server}

    $freshMigrate=Invoke-Flyway $fresh $fullMigrations migrate '2.6.9'
    Invoke-Flyway $fresh $fullMigrations info | Out-Null
    Invoke-Flyway $fresh $fullMigrations validate | Out-Null
    $freshNoop=Invoke-Flyway $fresh $fullMigrations migrate '2.6.9'

    Invoke-Flyway $upgrade $v268Migrations migrate '2.6.8' | Out-Null
    $beforeCount=[int](Invoke-Mysql $upgrade "SELECT COUNT(*) FROM flyway_schema_history WHERE success=1" -Raw).Output.Trim()
    $upgradeMigrate=Invoke-Flyway $upgrade $fullMigrations migrate '2.6.9'
    Invoke-Flyway $upgrade $fullMigrations info | Out-Null
    Invoke-Flyway $upgrade $fullMigrations validate | Out-Null
    $upgradeNoop=Invoke-Flyway $upgrade $fullMigrations migrate '2.6.9'
    $afterCount=[int](Invoke-Mysql $upgrade "SELECT COUNT(*) FROM flyway_schema_history WHERE success=1" -Raw).Output.Trim()
    if($afterCount-$beforeCount -ne 1){$failures += "Upgrade applied migration count was $($afterCount-$beforeCount), expected 1"}

    $historyFresh=(Invoke-Mysql $fresh "SELECT COUNT(*),SUM(success=0),SUM(version='2.6.9') FROM flyway_schema_history" -Raw).Output.Trim()
    $historyUpgrade=(Invoke-Mysql $upgrade "SELECT COUNT(*),SUM(success=0),SUM(version='2.6.9') FROM flyway_schema_history" -Raw).Output.Trim()
    $checksum=[int](Invoke-Mysql $fresh "SELECT checksum FROM flyway_schema_history WHERE version='2.6.9' AND success=1" -Raw).Output.Trim()
    if($historyFresh -notmatch "`t0`t1$" -or $historyUpgrade -notmatch "`t0`t1$"){$failures += 'Flyway history count/failure/V2.6.9 occurrence mismatch'}
    if($freshNoop.Output -notmatch 'No migration necessary' -or $upgradeNoop.Output -notmatch 'No migration necessary'){$failures += 'Second migrate was not no-op'}

    $fpFresh=Write-Fingerprint $fresh
    $fpUpgrade=Write-Fingerprint $upgrade
    foreach($scope in @('full','workflow','role')){if($fpFresh[$scope]-ne$fpUpgrade[$scope]){$failures += "Schema fingerprint mismatch: $scope"}}

    $structureSql=@"
SELECT table_name,COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name IN ('role_runtime_binding_approval','workflow_role_runtime_binding_snapshot') GROUP BY table_name ORDER BY table_name;
SELECT table_name,COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name IN ('role_runtime_binding_approval','workflow_role_runtime_binding_snapshot') GROUP BY table_name ORDER BY table_name;
SELECT table_name,COUNT(*) FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE() AND table_name IN ('role_runtime_binding_approval','workflow_role_runtime_binding_snapshot') GROUP BY table_name ORDER BY table_name;
SELECT table_name,COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND constraint_type='CHECK' AND table_name IN ('role_runtime_binding_approval','workflow_role_runtime_binding_snapshot') GROUP BY table_name ORDER BY table_name;
"@
    $structure=(Invoke-Mysql $fresh $structureSql -Raw).Output
    $structure | Set-Content (Join-Path $evidence 'structure.txt') -Encoding utf8

    $fixture=Add-WorkflowFixture $fresh 269000 '1' -InsertSnapshot
    $negative=@("name`tresult`tmysql_code`tsqlstate`texpected`tdetail")
    Expect-Rejected $fresh '01_illegal_approval_status' "INSERT INTO role_runtime_binding_approval(id,proposal_hash,eligibility_hash,resolver_code,resolver_version,contract_hash,status,audit_info,deleted,delete_token,version) VALUES(269100,REPEAT('2',64),REPEAT('3',64),'ROLE_DIRECTORY','ROLE_DIRECTORY_V1',REPEAT('a',64),'UNKNOWN','x',0,0,0)" ([ref]$negative) 'status CHECK'
    Expect-Rejected $fresh '02_illegal_hash_format' "INSERT INTO role_runtime_binding_approval(id,proposal_hash,eligibility_hash,resolver_code,resolver_version,contract_hash,status,audit_info,deleted,delete_token,version) VALUES(269101,'BAD',REPEAT('3',64),'ROLE_DIRECTORY','ROLE_DIRECTORY_V1',REPEAT('a',64),'PENDING','x',0,0,0)" ([ref]$negative) 'hash CHECK'
    Expect-Rejected $fresh '03_duplicate_proposal_hash' "INSERT INTO role_runtime_binding_approval(id,proposal_hash,eligibility_hash,resolver_code,resolver_version,contract_hash,status,approved_by,approved_at,audit_info,deleted,delete_token,version) SELECT 269102,proposal_hash,eligibility_hash,resolver_code,resolver_version,contract_hash,status,approved_by,approved_at,audit_info,0,0,0 FROM role_runtime_binding_approval WHERE id=$($fixture.Approval)" ([ref]$negative) 'uk_role_runtime_approval_hash'
    $fixtureTwo=Add-WorkflowFixture $fresh 269200 '2'
    Expect-Rejected $fresh '04_duplicate_binding_hash' "INSERT INTO workflow_role_runtime_binding_snapshot(id,approval_id,binding_set_id,resolver_binding_id,node_resolver_binding_id,instance_id,definition_version_id,node_id,resolver_code,resolver_version,contract_hash,role_code,organization_id,directory_revision,effective_at,directory_hash,role_rule_hash,candidate_rule_hash,source_evidence_hash,binding_hash,status,audit_info,deleted,delete_token,version) VALUES($($fixtureTwo.Snapshot),$($fixtureTwo.Approval),$($fixtureTwo.Set),$($fixtureTwo.Binding),$($fixtureTwo.NodeBinding),$($fixtureTwo.Instance),$($fixtureTwo.Version),$($fixtureTwo.Node),'ROLE_DIRECTORY','ROLE_DIRECTORY_V1',REPEAT('a',64),'INVESTMENT_REVIEWER','ORG-001',12,NOW(3),REPEAT('b',64),REPEAT('c',64),REPEAT('d',64),REPEAT('e',64),REPEAT('9',64),'FROZEN','x',0,0,0)" ([ref]$negative) 'uk_role_runtime_snapshot_binding_hash'
    Expect-Rejected $fresh '05_illegal_resolver_version' "INSERT INTO role_runtime_binding_approval(id,proposal_hash,eligibility_hash,resolver_code,resolver_version,contract_hash,status,audit_info,deleted,delete_token,version) VALUES(269105,REPEAT('4',64),REPEAT('5',64),'ROLE_DIRECTORY','',REPEAT('a',64),'PENDING','x',0,0,0)" ([ref]$negative) 'nonblank resolver version'
    Expect-Rejected $fresh '06_illegal_contract_hash' "INSERT INTO role_runtime_binding_approval(id,proposal_hash,eligibility_hash,resolver_code,resolver_version,contract_hash,status,audit_info,deleted,delete_token,version) VALUES(269106,REPEAT('4',64),REPEAT('5',64),'ROLE_DIRECTORY','ROLE_DIRECTORY_V1',REPEAT('A',64),'PENDING','x',0,0,0)" ([ref]$negative) 'lowercase contract hash'
    Expect-Rejected $fresh '07_illegal_organization' "UPDATE workflow_role_runtime_binding_snapshot SET organization_id='' WHERE id=$($fixture.Snapshot)" ([ref]$negative) 'business key CHECK'
    Expect-Rejected $fresh '08_modify_immutable_snapshot' "UPDATE workflow_role_runtime_binding_snapshot SET role_code='CHANGED' WHERE id=$($fixture.Snapshot)" ([ref]$negative) 'append-only UPDATE guard'
    Expect-Rejected $fresh '09_delete_immutable_snapshot' "DELETE FROM workflow_role_runtime_binding_snapshot WHERE id=$($fixture.Snapshot)" ([ref]$negative) 'append-only DELETE guard'
    Expect-Rejected $fresh '10_illegal_delete_token' "INSERT INTO role_runtime_binding_approval(id,proposal_hash,eligibility_hash,resolver_code,resolver_version,contract_hash,status,audit_info,deleted,delete_token,version) VALUES(269110,REPEAT('6',64),REPEAT('7',64),'ROLE_DIRECTORY','ROLE_DIRECTORY_V1',REPEAT('a',64),'PENDING','x',0,269110,0)" ([ref]$negative) 'delete_token CHECK'
    Expect-Rejected $fresh '11_illegal_approval_update' "UPDATE role_runtime_binding_approval SET status='APPROVED' WHERE id=$($fixture.Approval)" ([ref]$negative) 'approval decision CHECK'
    Expect-Rejected $fresh '12_pending_approval_snapshot' "UPDATE role_runtime_binding_approval SET status='PENDING',approved_by=NULL,approved_at=NULL WHERE id=$($fixtureTwo.Approval); INSERT INTO workflow_role_runtime_binding_snapshot(id,approval_id,binding_set_id,resolver_binding_id,node_resolver_binding_id,instance_id,definition_version_id,node_id,resolver_code,resolver_version,contract_hash,role_code,organization_id,directory_revision,effective_at,directory_hash,role_rule_hash,candidate_rule_hash,source_evidence_hash,binding_hash,status,audit_info,deleted,delete_token,version) VALUES($($fixtureTwo.Snapshot),$($fixtureTwo.Approval),$($fixtureTwo.Set),$($fixtureTwo.Binding),$($fixtureTwo.NodeBinding),$($fixtureTwo.Instance),$($fixtureTwo.Version),$($fixtureTwo.Node),'ROLE_DIRECTORY','ROLE_DIRECTORY_V1',REPEAT('a',64),'INVESTMENT_REVIEWER','ORG-001',12,NOW(3),REPEAT('b',64),REPEAT('c',64),REPEAT('d',64),REPEAT('e',64),REPEAT('8',64),'FROZEN','x',0,0,0)" ([ref]$negative) 'approved approval ownership'
    $negative | Set-Content (Join-Path $evidence 'negative.tsv') -Encoding utf8

    $compatibility=(Invoke-Mysql $fresh "SELECT (SELECT COUNT(*) FROM workflow_instance WHERE resolver_code='EXPLICIT_USER' AND resolver_version='EXPLICIT_USER_V1'),(SELECT COUNT(*) FROM workflow_role_runtime_binding_snapshot WHERE deleted=0),(SELECT COUNT(*) FROM workflow_task_candidate_pool p LEFT JOIN workflow_role_runtime_binding_snapshot s ON s.instance_id=p.instance_id WHERE s.id IS NOT NULL),(SELECT COUNT(*) FROM workflow_task_claim c JOIN workflow_role_runtime_binding_snapshot s ON s.instance_id=c.instance_id)" -Raw).Output.Trim()
    $sourceCount=(Invoke-Mysql $fresh "SELECT COUNT(*) FROM workflow_task_candidate_member WHERE source_ref_snapshot LIKE 'ROLE_DIRECTORY:%'" -Raw).Output.Trim()
    if($sourceCount-ne'0'){$failures += 'Candidate Pool was polluted by ROLE_DIRECTORY source'}

    $sha=(Get-FileHash (Join-Path $repoMigrations 'V2.6.9__create_role_runtime_snapshot.sql') -Algorithm SHA256).Hash.ToLowerInvariant()
    if($sha-ne'fbe41e2a354e616ee4b369c57980009057c75ced46a2eb9e48d1eb8354beb0d2'){$failures += 'V2.6.9 SHA drift'}
    $negativePass=@($negative|Select-Object -Skip 1|Where-Object{$_-match"`tPASS`t"}).Count
    $summary=[ordered]@{
        output_root=$OutputRoot
        mysql_version=((& $mysqld --version)-join ' ')
        flyway_version='13.0.0'
        fresh_history=$historyFresh
        upgrade_history=$historyUpgrade
        v269_checksum=$checksum
        v269_sha256=$sha
        full_schema_fingerprint=$fpFresh['full']
        workflow_schema_fingerprint=$fpFresh['workflow']
        role_runtime_schema_fingerprint=$fpFresh['role']
        structure=$structure
        negative_tests="$negativePass/12"
        compatibility=$compatibility
        role_candidate_source_rows=$sourceCount
        failures=$failures
        result=$(if($failures.Count-eq0-and$negativePass-eq12){'PASS'}else{'FAIL'})
    }
    $summary|ConvertTo-Json -Depth 5|Set-Content (Join-Path $evidence 'summary.json') -Encoding utf8
    $summary|ConvertTo-Json -Depth 5
    if($summary.result-ne'PASS'){throw($failures-join'; ')}
}
finally {
    foreach($process in $servers){if($process-and-not$process.HasExited){Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue}}
    Get-CimInstance Win32_Process -Filter "Name='mysqld.exe'" |
        Where-Object {$_.CommandLine -like "*$OutputRoot*"} |
        ForEach-Object {Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue}
}

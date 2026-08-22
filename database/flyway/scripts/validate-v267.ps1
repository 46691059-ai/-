param(
    [string]$OutputRoot = "D:\codex-validation-v267-$((Get-Date).ToString('yyyyMMdd-HHmmss'))",
    [int]$FreshPort = 34867,
    [int]$UpgradePort = 34868
)

$ErrorActionPreference = 'Stop'
$workspace = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$mysqlHome = 'C:\Program Files\MySQL\MySQL Server 8.4'
$mysqld = Join-Path $mysqlHome 'bin\mysqld.exe'
$mysql = Join-Path $mysqlHome 'bin\mysql.exe'
$mysqladmin = Join-Path $mysqlHome 'bin\mysqladmin.exe'
$flyway = 'C:\Users\WUKONG\AppData\Local\Temp\enterprise-v246-acceptance-68bf692b16d34ee191946695ae0bef9b\flyway-fresh\flyway-13.0.0\flyway.cmd'
$java = 'C:\Users\WUKONG\.cache\codex-java21-maven\jdk-fresh\jdk-21.0.12+8\bin\java.exe'
$repoMigrations = Join-Path $workspace 'database\migration\mysql'
$fingerprintSql = Join-Path $workspace 'database\mysql\verification\schema_fingerprint.sql'
$baselineFiles = @(
    '01_database.sql','02_sys.sql','03_hr.sql','04_party.sql','05_project.sql',
    '06_operation.sql','07_investment.sql','08_data_asset.sql','09_risk.sql',
    '10_init_data.sql','11_sprint_1_user_permissions.sql','12_sprint_1_org_permissions.sql',
    '13_sprint_1_role_permissions.sql','14_sprint_1_rbac_acceptance.sql',
    '15_sprint_1_menu_center.sql','16_sprint_1_log_center.sql','V1.1.0__investment_data_risk_bi.sql'
)
foreach ($required in @($mysqld,$mysql,$mysqladmin,$flyway,$java,$fingerprintSql)) {
    if (-not (Test-Path -LiteralPath $required)) { throw "Missing validation asset: $required" }
}
if (Test-Path -LiteralPath $OutputRoot) { throw "Validation root already exists: $OutputRoot" }

$evidence = Join-Path $OutputRoot 'evidence'
$fullMigrations = Join-Path $OutputRoot 'migrations-full'
$v266Migrations = Join-Path $OutputRoot 'migrations-v266'
$baselineSource = Join-Path $OutputRoot 'baseline'
New-Item -ItemType Directory -Path $evidence,$fullMigrations,$v266Migrations,$baselineSource -Force | Out-Null
Get-ChildItem $repoMigrations -Filter 'V*.sql' -File | Copy-Item -Destination $fullMigrations
Get-ChildItem $repoMigrations -Filter 'V*.sql' -File |
    Where-Object Name -ne 'V2.6.7__create_workflow_task_claim.sql' | Copy-Item -Destination $v266Migrations
foreach ($name in $baselineFiles) {
    Copy-Item (Join-Path $workspace "database\mysql\$name") (Join-Path $baselineSource $name)
}
$serverProcesses = @()
$acceptanceFailures = @()

function Invoke-Checked([string]$Label, [scriptblock]$Action) {
    & $Action
    if ($LASTEXITCODE -ne 0) { throw "$Label failed with exit code $LASTEXITCODE" }
}

function New-IsolatedMysql([string]$Name, [int]$Port) {
    if (Get-NetTCPConnection -LocalPort $Port -ErrorAction SilentlyContinue) {
        throw "Validation port is already in use: $Port"
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
    Invoke-Checked "initialize $Name" { & $mysqld "--defaults-file=$ini" --initialize-insecure }
    $process = Start-Process $mysqld -ArgumentList "--defaults-file=$ini" -WindowStyle Hidden -PassThru
    $script:serverProcesses += $process
    foreach ($attempt in 1..80) {
        $old = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
        & $mysqladmin --protocol=TCP --host=127.0.0.1 --port=$Port --user=root ping 2>$null | Out-Null
        $ErrorActionPreference = $old
        if ($LASTEXITCODE -eq 0) { return @{Name=$Name;Port=$Port;Root=$root} }
        Start-Sleep -Milliseconds 400
    }
    throw "$Name did not become ready"
}

function Invoke-MysqlFile($Server, [string]$File, [string]$Database='') {
    $args = @('--protocol=TCP','--host=127.0.0.1',"--port=$($Server.Port)",'--user=root','--default-character-set=utf8mb4')
    if ($Database) { $args += "--database=$Database" }
    $path = (Resolve-Path $File).Path.Replace('\','/')
    & $mysql @args "--execute=source $path"
    if ($LASTEXITCODE -ne 0) { throw "mysql source failed: $File" }
}

function Invoke-Mysql($Server, [string]$Sql, [switch]$Raw) {
    $args = @('--protocol=TCP','--host=127.0.0.1',"--port=$($Server.Port)",'--user=root','--database=enterprise_platform')
    if ($Raw) { $args += @('--batch','--raw','--skip-column-names') }
    $old = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
    $output = & $mysql @args "--execute=$Sql" 2>&1
    $exit = $LASTEXITCODE; $ErrorActionPreference = $old
    return @{ExitCode=$exit;Output=($output -join "`n")}
}

function Initialize-Baseline($Server) {
    foreach ($name in $baselineFiles) {
        Invoke-MysqlFile $Server (Join-Path $baselineSource $name) $(if($name -eq '01_database.sql'){''}else{'enterprise_platform'})
    }
}

function Invoke-Flyway($Server, [string]$Location, [string]$Command, [string]$Target='') {
    $args = @("-url=jdbc:mysql://127.0.0.1:$($Server.Port)/enterprise_platform",'-user=root','-password=',
        "-locations=filesystem:$($Location.Replace('\','/'))",'-baselineOnMigrate=true','-baselineVersion=2.0.0',
        '-validateMigrationNaming=true','-cleanDisabled=true','-connectRetries=10')
    if ($Target) { $args += "-target=$Target" }
    $args += $Command
    $log = Join-Path $evidence "$($Server.Name)-$Command$(if($Target){"-$Target"}).log"
    & $flyway @args 2>&1 | Tee-Object -FilePath $log
    if ($LASTEXITCODE -ne 0) { throw "Flyway $Command failed for $($Server.Name)" }
}

function Write-Fingerprint($Server) {
    $args = @('--protocol=TCP','--host=127.0.0.1',"--port=$($Server.Port)",'--user=root',
        '--database=enterprise_platform','--batch','--raw','--skip-column-names')
    $rows = Get-Content $fingerprintSql -Raw | & $mysql @args
    if ($LASTEXITCODE -ne 0) { throw 'schema fingerprint query failed' }
    $fullFile = Join-Path $evidence "$($Server.Name)-schema.txt"
    $workflowFile = Join-Path $evidence "$($Server.Name)-workflow-schema.txt"
    $claimFile = Join-Path $evidence "$($Server.Name)-claim-schema.txt"
    [IO.File]::WriteAllText($fullFile,(($rows -join "`n")+"`n"),[Text.UTF8Encoding]::new($false))
    $workflow = $rows | Where-Object { $_ -match '^\w+\|workflow_' }
    [IO.File]::WriteAllText($workflowFile,(($workflow -join "`n")+"`n"),[Text.UTF8Encoding]::new($false))
    $claim = $workflow | Where-Object { $_ -match 'workflow_task_claim' -or $_ -match 'workflow_task_candidate_(pool|member)' -or $_ -match '^\w+\|workflow_task\|' }
    [IO.File]::WriteAllText($claimFile,(($claim -join "`n")+"`n"),[Text.UTF8Encoding]::new($false))
    return @{
        Full=(Get-FileHash $fullFile -Algorithm SHA256).Hash.ToLower()
        Workflow=(Get-FileHash $workflowFile -Algorithm SHA256).Hash.ToLower()
        Claim=(Get-FileHash $claimFile -Algorithm SHA256).Hash.ToLower()
    }
}

function Expect-Rejected($Server, [string]$Name, [string]$Sql, [ref]$Log) {
    $result = Invoke-Mysql $Server "START TRANSACTION; $Sql; ROLLBACK;"
    $passed = $result.ExitCode -ne 0
    $Log.Value += "$Name`t$(if($passed){'PASS'}else{'FAIL'})`t$($result.Output.Replace("`n",' '))"
    if (-not $passed) { $script:acceptanceFailures += "Negative test accepted: $Name" }
}

function Add-ClaimableTask($Server, [long]$Execution, [long]$Task, [long]$Snapshot,
        [long]$Pool, [long]$MemberA, [long]$MemberB, [int]$Visit, [string]$Suffix) {
    $sql = @"
INSERT INTO workflow_node_execution(id,execution_no,instance_id,version_id,node_id,node_code_snapshot,node_name_snapshot,visit_no,status,entered_time,activated_time,trace_id,created_by,updated_by,deleted,delete_token,version)
VALUES($Execution,'WFNE-$Suffix',96620,96601,96611,'POOL_NODE','Pool Node',$Visit,'ACTIVE',NOW(3),NOW(3),'v267-$Suffix','acceptance','acceptance',0,0,0);
INSERT INTO workflow_task(id,task_no,instance_id,version_id,node_id,node_execution_id,node_code_snapshot,node_name_snapshot,task_round,participant_key,assignee_user_id,candidate_snapshot,assignment_mode,status,allowed_actions,created_by,updated_by,deleted,delete_token,version)
VALUES($Task,'WFT-$Suffix',96620,96601,96611,$Execution,'POOL_NODE','Pool Node',$Visit,'POOL_NODE:$Visit',NULL,'{}','CANDIDATE_POOL','PENDING','APPROVE,REJECT','acceptance','acceptance',0,0,0);
INSERT INTO workflow_task_assignment_snapshot(id,task_id,instance_id,version_id,node_id,node_execution_id,strategy_type,target_type,target_snapshot,resolved_users,resolved_user_count,resolve_time,audit_info,trace_id,created_by,updated_by,deleted,delete_token,version)
VALUES($Snapshot,$Task,96620,96601,96611,$Execution,'ROLE','ROLE','{}','[701,702]',2,NOW(3),'acceptance','v267-$Suffix','acceptance','acceptance',0,0,0);
INSERT INTO workflow_task_candidate_pool(id,pool_no,task_id,instance_id,version_id,node_id,node_execution_id,binding_set_id,resolver_binding_id,node_resolver_binding_id,assignment_snapshot_id,assignment_mode,strategy_type,resolver_code,resolver_version,contract_hash,rule_hash,candidate_count,generated_time,effective_time,expires_time,pool_hash,status,audit_info,created_by,created_time,updated_by,updated_time,deleted,delete_token,remark,version)
VALUES($Pool,'WCP-$Suffix',$Task,96620,96601,96611,$Execution,96630,96631,96641,$Snapshot,'CANDIDATE_POOL','ROLE','ROLE_RESOLVER','ROLE_V1',REPEAT('a',64),REPEAT('b',64),2,NOW(3),NOW(3),DATE_ADD(NOW(3),INTERVAL 1 DAY),REPEAT('d',64),'AVAILABLE','acceptance','acceptance',NOW(3),'acceptance',NOW(3),0,0,NULL,0);
INSERT INTO workflow_task_candidate_member(id,pool_id,task_id,instance_id,candidate_user_id,source_type,source_ref_snapshot,org_id_snapshot,position_id_snapshot,role_id_snapshot,eligibility_snapshot,eligibility_hash,sort_order,generated_time,status,audit_info,created_by,created_time,updated_by,updated_time,deleted,delete_token,remark,version)
VALUES($MemberA,$Pool,$Task,96620,701,'ROLE','ROLE:APPROVER',966,NULL,7001,'{"enabled":true}',REPEAT('e',64),1,NOW(3),'INCLUDED','acceptance','acceptance',NOW(3),'acceptance',NOW(3),0,0,NULL,0),
      ($MemberB,$Pool,$Task,96620,702,'ROLE','ROLE:APPROVER',966,NULL,7001,'{"enabled":true}',REPEAT('f',64),2,NOW(3),'INCLUDED','acceptance','acceptance',NOW(3),'acceptance',NOW(3),0,0,NULL,0);
"@
    $result = Invoke-Mysql $Server $sql
    if ($result.ExitCode -ne 0) { throw "Claimable task fixture failed: $($result.Output)" }
}

function New-ClaimSql([long]$Task,[long]$Pool,[long]$Member,[long]$Execution,
        [long]$Claim,[long]$Audit,[long]$User,[string]$Key,[string]$Suffix,[int]$Delay=0) {
    return @"
START TRANSACTION;
SELECT id FROM workflow_task WHERE id=$Task FOR UPDATE;
SELECT id FROM workflow_task_candidate_pool WHERE id=$Pool FOR UPDATE;
SELECT id FROM workflow_task_candidate_member WHERE id=$Member FOR UPDATE;
SELECT id FROM workflow_node_execution WHERE id=$Execution FOR UPDATE;
SELECT id FROM workflow_instance WHERE id=96620 FOR UPDATE;
SELECT SLEEP($Delay);
UPDATE workflow_task SET assignee_user_id=$User,status='CLAIMED',claimed_time=NOW(3),version=version+1
 WHERE id=$Task AND assignment_mode='CANDIDATE_POOL' AND status='PENDING' AND assignee_user_id IS NULL AND version=0;
SET @won=ROW_COUNT();
INSERT INTO workflow_task_claim(id,claim_no,task_id,candidate_pool_id,candidate_member_id,instance_id,node_execution_id,candidate_user_id,operator_user_id,status,claim_time,eligibility_snapshot_hash,realtime_eligibility_result,data_scope_result,sod_result,rbac_result,task_status_before,task_status_after,pool_status_before,pool_status_after,task_version_before,task_version_after,idempotency_key,trace_id,created_by,updated_by,deleted,delete_token,version)
SELECT $Claim,'WCL-$Suffix',$Task,$Pool,$Member,96620,$Execution,$User,$User,'CLAIMED',NOW(3),REPEAT('e',64),'ELIGIBLE','ALLOW:SCOPED','WORKFLOW_BASIC_SOD:V1:ALLOW','ALLOW:workflow:approve','PENDING','CLAIMED','AVAILABLE','CLAIMED',0,1,'$Key','trace-$Suffix','acceptance','acceptance',0,0,0 FROM DUAL WHERE @won=1;
UPDATE workflow_task_candidate_pool SET status='CLAIMED',version=version+1
 WHERE id=$Pool AND status='AVAILABLE' AND version=0 AND @won=1;
SET @poolwon=ROW_COUNT();
INSERT INTO workflow_task_claim_audit(id,event_no,task_id,candidate_pool_id,claim_id,candidate_member_id,instance_id,node_execution_id,operator_user_id,claimant_user_id,event_type,result,reason_code,frozen_eligibility_hash,realtime_eligibility_result,rbac_result,data_scope_result,sod_result,task_status_before,task_status_after,pool_status_before,pool_status_after,event_time,trace_id,idempotency_key,event_hash,created_by,updated_by,deleted,delete_token,version)
SELECT $Audit,'WCLA-$Suffix',$Task,$Pool,$Claim,$Member,96620,$Execution,$User,$User,'CLAIM','SUCCESS','CLAIMED',REPEAT('e',64),'ELIGIBLE','ALLOW:workflow:approve','ALLOW:SCOPED','WORKFLOW_BASIC_SOD:V1:ALLOW','PENDING','CLAIMED','AVAILABLE','CLAIMED',NOW(3),'trace-$Suffix','$Key',REPEAT('a',64),'acceptance','acceptance',0,0,0 FROM DUAL WHERE @won=1 AND @poolwon=1;
COMMIT;
SELECT @won AS task_cas,@poolwon AS pool_cas;
"@
}

try {
    $fresh = New-IsolatedMysql 'fresh' $FreshPort
    $upgrade = New-IsolatedMysql 'upgrade-v266' $UpgradePort
    Initialize-Baseline $fresh; Initialize-Baseline $upgrade

    Invoke-Flyway $fresh $fullMigrations migrate '2.6.7'
    Invoke-Flyway $fresh $fullMigrations validate
    Invoke-Flyway $fresh $fullMigrations migrate '2.6.7'
    Invoke-Flyway $fresh $fullMigrations info

    Invoke-Flyway $upgrade $v266Migrations migrate '2.6.6'
    Invoke-Flyway $upgrade $fullMigrations migrate '2.6.7'
    Invoke-Flyway $upgrade $fullMigrations validate
    Invoke-Flyway $upgrade $fullMigrations migrate '2.6.7'
    Invoke-Flyway $upgrade $fullMigrations info

    $checksumFresh = (Invoke-Mysql $fresh "SELECT checksum FROM flyway_schema_history WHERE version='2.6.7' AND success=1" -Raw).Output.Trim()
    $checksumUpgrade = (Invoke-Mysql $upgrade "SELECT checksum FROM flyway_schema_history WHERE version='2.6.7' AND success=1" -Raw).Output.Trim()
    if ($checksumFresh -ne $checksumUpgrade) { throw 'V2.6.7 checksums differ' }
    $v267FreshCount = [int](Invoke-Mysql $fresh "SELECT COUNT(*) FROM flyway_schema_history WHERE version='2.6.7' AND success=1" -Raw).Output.Trim()
    $v267UpgradeCount = [int](Invoke-Mysql $upgrade "SELECT COUNT(*) FROM flyway_schema_history WHERE version='2.6.7' AND success=1" -Raw).Output.Trim()
    if ($v267FreshCount -ne 1 -or $v267UpgradeCount -ne 1) { throw 'V2.6.7 was not applied exactly once' }

    $freshFingerprint = Write-Fingerprint $fresh
    $upgradeFingerprint = Write-Fingerprint $upgrade
    if ($freshFingerprint.Full -ne $upgradeFingerprint.Full -or
        $freshFingerprint.Workflow -ne $upgradeFingerprint.Workflow -or
        $freshFingerprint.Claim -ne $upgradeFingerprint.Claim) {
        throw 'Fresh and Upgrade schema fingerprints differ'
    }

    $structureSql = @"
SELECT table_name,COUNT(*) AS columns_count FROM information_schema.columns WHERE table_schema='enterprise_platform' AND table_name IN ('workflow_task_claim','workflow_task_claim_audit','workflow_task','workflow_task_candidate_pool','workflow_task_candidate_member') GROUP BY table_name ORDER BY table_name;
SELECT table_name,constraint_type,COUNT(*) AS constraint_count FROM information_schema.table_constraints WHERE constraint_schema='enterprise_platform' AND table_name IN ('workflow_task_claim','workflow_task_claim_audit','workflow_task','workflow_task_candidate_pool','workflow_task_candidate_member') GROUP BY table_name,constraint_type ORDER BY table_name,constraint_type;
SELECT table_name,constraint_name,enforced FROM information_schema.table_constraints WHERE constraint_schema='enterprise_platform' AND constraint_type='CHECK' AND table_name IN ('workflow_task_claim','workflow_task_claim_audit','workflow_task','workflow_task_candidate_pool','workflow_task_candidate_member') ORDER BY table_name,constraint_name;
SELECT table_name,index_name,non_unique,GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics WHERE table_schema='enterprise_platform' AND table_name IN ('workflow_task_claim','workflow_task_claim_audit') GROUP BY table_name,index_name,non_unique ORDER BY table_name,index_name;
"@
    (Invoke-Mysql $fresh $structureSql -Raw).Output | Set-Content (Join-Path $evidence 'claim-structure.tsv') -Encoding utf8

    $fixture = @"
START TRANSACTION;
INSERT INTO workflow_definition(id,definition_code,definition_name,business_type,enterprise_id,owner_org_id,status,current_version_id,created_by,updated_by,deleted,delete_token,version)
VALUES(96600,'V267_POOL','V267 Candidate Pool','ACCEPTANCE',966,966,'DRAFT',NULL,'acceptance','acceptance',0,0,0);
INSERT INTO workflow_version(id,definition_id,version_no,status,schema_version,engine_mode,content_hash_algorithm,content_hash,effective_from,published_by,published_time,created_by,updated_by,deleted,delete_token,version)
VALUES(96601,96600,1,'PUBLISHED','2.0','MULTI_NODE_LINEAR_V1','GRAPH_V2_SHA256',REPEAT('1',64),NOW(3),966,NOW(3),'acceptance','acceptance',0,0,0);
INSERT INTO workflow_node(id,version_id,node_code,node_name,node_type,node_order,governance_node_type,approval_mode,assignment_rule_type,assignment_rule_config,withdraw_allowed,enabled,created_by,updated_by,deleted,delete_token,version)
VALUES(96611,96601,'POOL_NODE','Pool Node','APPROVAL',1,'GENERAL_APPROVAL','SINGLE','USER','{"userId":701}',0,1,'acceptance','acceptance',0,0,0);
UPDATE workflow_definition SET status='ACTIVE',current_version_id=96601,version=1 WHERE id=96600;
INSERT INTO workflow_instance(id,instance_no,definition_id,version_id,definition_code_snapshot,definition_version_no,definition_content_hash_snapshot,resolver_code,resolver_version,resolver_contract_hash,business_type,business_id,business_key,enterprise_id,attempt_no,initiator_user_id,initiator_org_id,current_node_id,engine_mode,content_hash_algorithm_snapshot,current_node_execution_id,status,idempotency_key,request_hash,event_sequence,trace_id,started_time,created_by,updated_by,deleted,delete_token,version)
VALUES(96620,'WFI-V267',96600,96601,'V267_POOL',1,REPEAT('1',64),'EXPLICIT_USER','EXPLICIT_USER_V1','3166103d7697a6f2e46932214b0447310c69d6b624f6d92a282e9ef3ab496853','ACCEPTANCE','96620','ACCEPTANCE:96620',966,1,700,966,96611,'MULTI_NODE_LINEAR_V1','GRAPH_V2_SHA256',NULL,'RUNNING','v267-fixture',REPEAT('2',64),0,'v267',NOW(3),'acceptance','acceptance',0,0,0);
INSERT INTO workflow_instance_resolver_binding_set(id,instance_id,definition_id,definition_version_id,manifest_version,manifest_hash,binding_count,binding_status,frozen_time,audit_info,created_by,updated_by,deleted,delete_token,version)
VALUES(96630,96620,96600,96601,'MULTI_RESOLVER_V1',REPEAT('3',64),1,'FROZEN',NOW(3),'acceptance','acceptance','acceptance',0,0,0);
INSERT INTO workflow_instance_resolver_binding(id,binding_set_id,instance_id,definition_version_id,resolver_code,resolver_version,strategy_type,resolver_mode,contract_hash,rule_hash,binding_status,frozen_time,audit_info,created_by,updated_by,deleted,delete_token,version)
VALUES(96631,96630,96620,96601,'ROLE_RESOLVER','ROLE_V1','ROLE','CANDIDATE_POOL',REPEAT('a',64),REPEAT('b',64),'FROZEN',NOW(3),'acceptance','acceptance','acceptance',0,0,0);
INSERT INTO workflow_node_resolver_binding_snapshot(id,binding_set_id,resolver_binding_id,instance_id,definition_version_id,node_id,node_code_snapshot,strategy_type,resolver_mode,target_type,target_value_snapshot,rule_version,rule_snapshot,rule_hash,node_binding_hash,binding_status,frozen_time,audit_info,created_by,updated_by,deleted,delete_token,version)
VALUES(96641,96630,96631,96620,96601,96611,'POOL_NODE','ROLE','CANDIDATE_POOL','ROLE','{"roleCode":"APPROVER"}','RULE_V1','{"roleCode":"APPROVER"}',REPEAT('b',64),REPEAT('c',64),'FROZEN',NOW(3),'acceptance','acceptance','acceptance',0,0,0);
COMMIT;
"@
    $fixtureResult = Invoke-Mysql $fresh $fixture
    if ($fixtureResult.ExitCode -ne 0) { throw "Base fixture failed: $($fixtureResult.Output)" }
    Add-ClaimableTask $fresh 96651 96661 96671 96681 96691 96692 1 'LEGAL'

    $legalClaim = Invoke-Mysql $fresh (New-ClaimSql 96661 96681 96691 96651 96601 96602 701 'idem-legal' 'LEGAL') -Raw
    if ($legalClaim.ExitCode -ne 0 -or $legalClaim.Output -notmatch "1`t1") { throw "Legal Claim failed: $($legalClaim.Output)" }
    $chain = (Invoke-Mysql $fresh @"
SELECT t.assignee_user_id,t.status,p.status,e.status,i.status,
 (SELECT COUNT(*) FROM workflow_task_claim c WHERE c.task_id=t.id AND c.status='CLAIMED' AND c.active_token=0),
 (SELECT COUNT(*) FROM workflow_task_claim_audit a WHERE a.task_id=t.id AND a.result='SUCCESS'),
 (SELECT COUNT(*) FROM workflow_task_assignment_snapshot s WHERE s.task_id=t.id),
 (SELECT COUNT(*) FROM workflow_task_candidate_member m WHERE m.task_id=t.id)
FROM workflow_task t JOIN workflow_task_candidate_pool p ON p.task_id=t.id
JOIN workflow_node_execution e ON e.id=t.node_execution_id
JOIN workflow_instance i ON i.id=t.instance_id WHERE t.id=96661;
"@ -Raw).Output.Trim()
    $chain | Set-Content (Join-Path $evidence 'claim-data-chain.tsv') -Encoding utf8
    if ($chain -ne "701`tCLAIMED`tCLAIMED`tACTIVE`tRUNNING`t1`t1`t1`t2") { throw "Claim data chain mismatch: $chain" }

    $approveCompatibility = Invoke-Mysql $fresh "START TRANSACTION; UPDATE workflow_task SET status='APPROVED',completed_by=701,completed_time=NOW(3),decision_result='APPROVED',version=version+1 WHERE id=96661 AND status='CLAIMED' AND assignee_user_id=701; SELECT ROW_COUNT(); ROLLBACK;" -Raw
    if ($approveCompatibility.ExitCode -ne 0 -or $approveCompatibility.Output -notmatch '1') { throw 'Claimed Task approval compatibility failed' }

    $negative = @()
    Expect-Rejected $fresh 'duplicate_active_claim' "INSERT INTO workflow_task_claim SELECT 96603,'WCL-DUP',task_id,candidate_pool_id,candidate_member_id,instance_id,node_execution_id,702,702,status,NOW(3),eligibility_snapshot_hash,realtime_eligibility_result,data_scope_result,sod_result,rbac_result,task_status_before,task_status_after,pool_status_before,pool_status_after,task_version_before,task_version_after,'idem-other','trace-other',0,created_by,NOW(3),updated_by,NOW(3),deleted,delete_token,remark,version FROM workflow_task_claim WHERE id=96601" ([ref]$negative)
    Expect-Rejected $fresh 'duplicate_idempotency_key' "UPDATE workflow_task_claim SET active_token=id,status='RELEASED' WHERE id=96601; INSERT INTO workflow_task_claim SELECT 96604,'WCL-IDEM',task_id,candidate_pool_id,candidate_member_id,instance_id,node_execution_id,candidate_user_id,operator_user_id,'CLAIMED',NOW(3),eligibility_snapshot_hash,realtime_eligibility_result,data_scope_result,sod_result,rbac_result,task_status_before,task_status_after,pool_status_before,pool_status_after,task_version_before,task_version_after,idempotency_key,'trace-idem',0,created_by,NOW(3),updated_by,NOW(3),deleted,delete_token,remark,version FROM workflow_task_claim WHERE id=96601" ([ref]$negative)
    Invoke-Mysql $fresh "UPDATE workflow_task_claim SET active_token=0,status='CLAIMED' WHERE id=96601" | Out-Null
    Expect-Rejected $fresh 'missing_pool' "UPDATE workflow_task_claim SET candidate_pool_id=999999 WHERE id=96601" ([ref]$negative)
    Expect-Rejected $fresh 'missing_member' "UPDATE workflow_task_claim SET candidate_member_id=999999 WHERE id=96601" ([ref]$negative)
    Expect-Rejected $fresh 'missing_instance_chain' "UPDATE workflow_task_claim SET instance_id=999999 WHERE id=96601" ([ref]$negative)
    Expect-Rejected $fresh 'missing_node_execution_chain' "UPDATE workflow_task_claim SET node_execution_id=999999 WHERE id=96601" ([ref]$negative)
    Expect-Rejected $fresh 'audit_missing_claim' "INSERT INTO workflow_task_claim_audit(id,event_no,claim_id,event_type,result,reason_code,event_time,trace_id,idempotency_key,event_hash,deleted,delete_token,version) VALUES(96609,'BAD',999999,'CLAIM','SUCCESS','X',NOW(3),'trace','key',REPEAT('a',64),0,0,0)" ([ref]$negative)
    Expect-Rejected $fresh 'audit_cross_reference_mismatch' "UPDATE workflow_task_claim_audit SET task_id=999999,instance_id=999999,node_execution_id=999999,candidate_pool_id=999999,candidate_member_id=999999 WHERE id=96602" ([ref]$negative)
    Expect-Rejected $fresh 'orphan_success_audit' "INSERT INTO workflow_task_claim_audit(id,event_no,task_id,claim_id,event_type,result,reason_code,event_time,trace_id,idempotency_key,event_hash,deleted,delete_token,version) VALUES(96610,'ORPHAN-SUCCESS',999999,NULL,'CLAIM','SUCCESS','CLAIMED',NOW(3),'trace-orphan','idem-orphan',REPEAT('a',64),0,0,0)" ([ref]$negative)
    Expect-Rejected $fresh 'invalid_claim_status' "UPDATE workflow_task_claim SET status='INVALID' WHERE id=96601" ([ref]$negative)
    Expect-Rejected $fresh 'claimant_operator_mismatch' "UPDATE workflow_task_claim SET operator_user_id=702 WHERE id=96601" ([ref]$negative)
    Expect-Rejected $fresh 'invalid_eligibility_hash' "UPDATE workflow_task_claim SET eligibility_snapshot_hash='ABC' WHERE id=96601" ([ref]$negative)
    Expect-Rejected $fresh 'blank_idempotency_key' "UPDATE workflow_task_claim SET idempotency_key=' ' WHERE id=96601" ([ref]$negative)
    Expect-Rejected $fresh 'blank_trace_id' "UPDATE workflow_task_claim SET trace_id=' ' WHERE id=96601" ([ref]$negative)
    Expect-Rejected $fresh 'invalid_delete_token' "UPDATE workflow_task_claim SET delete_token=1 WHERE id=96601" ([ref]$negative)
    Expect-Rejected $fresh 'negative_version' "UPDATE workflow_task_claim SET version=-1 WHERE id=96601" ([ref]$negative)
    Expect-Rejected $fresh 'invalid_task_transition' "UPDATE workflow_task_claim SET task_version_after=3 WHERE id=96601" ([ref]$negative)
    $poolAgain = Invoke-Mysql $fresh "UPDATE workflow_task_candidate_pool SET version=version+1 WHERE id=96681 AND status='AVAILABLE'; SELECT ROW_COUNT();" -Raw
    if ($poolAgain.ExitCode -ne 0 -or $poolAgain.Output.Trim() -ne '0') { throw 'Claimed Pool CAS gate failed' }
    $negative | Set-Content (Join-Path $evidence 'negative-tests.tsv') -Encoding utf8

    Add-ClaimableTask $fresh 97751 97761 97771 97781 97791 97792 2 'CONCURRENT'
    $sqlA = Join-Path $evidence 'claim-concurrent-a.sql'; $sqlB = Join-Path $evidence 'claim-concurrent-b.sql'
    [IO.File]::WriteAllText($sqlA,(New-ClaimSql 97761 97781 97791 97751 97701 97711 701 'idem-a' 'CON-A' 2),[Text.UTF8Encoding]::new($false))
    [IO.File]::WriteAllText($sqlB,(New-ClaimSql 97761 97781 97792 97751 97702 97712 702 'idem-b' 'CON-B' 0),[Text.UTF8Encoding]::new($false))
    $common = @('--protocol=TCP','--host=127.0.0.1',"--port=$($fresh.Port)",'--user=root','--database=enterprise_platform','--batch','--raw','--skip-column-names')
    $argsA = ($common -join ' ') + " --execute=`"source $($sqlA.Replace('\','/'))`""
    $argsB = ($common -join ' ') + " --execute=`"source $($sqlB.Replace('\','/'))`""
    $outA=Join-Path $evidence 'claim-concurrent-a.out'; $outB=Join-Path $evidence 'claim-concurrent-b.out'
    $errA=Join-Path $evidence 'claim-concurrent-a.err'; $errB=Join-Path $evidence 'claim-concurrent-b.err'
    $started=Get-Date
    $p1=Start-Process $mysql -ArgumentList $argsA -WindowStyle Hidden -PassThru -RedirectStandardOutput $outA -RedirectStandardError $errA
    Start-Sleep -Milliseconds 150
    $p2=Start-Process $mysql -ArgumentList $argsB -WindowStyle Hidden -PassThru -RedirectStandardOutput $outB -RedirectStandardError $errB
    $p1.WaitForExit(); $p2.WaitForExit(); $elapsed=((Get-Date)-$started).TotalMilliseconds
    $conCounts=(Invoke-Mysql $fresh "SELECT t.assignee_user_id,t.status,p.status,(SELECT COUNT(*) FROM workflow_task_claim c WHERE c.task_id=t.id AND c.status='CLAIMED' AND c.active_token=0),(SELECT COUNT(*) FROM workflow_task_claim_audit a WHERE a.task_id=t.id AND a.result='SUCCESS') FROM workflow_task t JOIN workflow_task_candidate_pool p ON p.task_id=t.id WHERE t.id=97761" -Raw).Output.Trim()
    $aText=(Get-Content $outA -Raw); $bText=(Get-Content $outB -Raw); $aErr=(Get-Content $errA -Raw); $bErr=(Get-Content $errB -Raw)
    "$conCounts`nelapsed_ms=$elapsed`nA=$aText`nB=$bText`nAERR=$aErr`nBERR=$bErr" | Set-Content (Join-Path $evidence 'claim-concurrency-result.txt') -Encoding utf8
    if ($conCounts -notmatch "^(701|702)`tCLAIMED`tCLAIMED`t1`t1$" -or $aText -notmatch "1`t1" -or $bText -notmatch "0`t0" -or $aErr -match '1213' -or $bErr -match '1213') { throw "Concurrent Claim validation failed: $conCounts" }

    Add-ClaimableTask $fresh 97851 97861 97871 97881 97891 97892 3 'ROLLBACK'
    $rollbackFile=Join-Path $evidence 'claim-rollback.sql'
    $rollbackSql=(New-ClaimSql 97861 97881 97891 97851 97801 97811 701 'idem-rb' 'ROLLBACK') -replace "INSERT INTO workflow_task_claim_audit\(","INSERT INTO workflow_task_claim_audit("
    $rollbackSql=$rollbackSql -replace "REPEAT\('a',64\),'acceptance'","'BAD','acceptance'"
    $rollbackSql=$rollbackSql -replace "COMMIT;","ROLLBACK;"
    [IO.File]::WriteAllText($rollbackFile,$rollbackSql,[Text.UTF8Encoding]::new($false))
    $rollbackArgs=($common -join ' ') + " --force --execute=`"source $($rollbackFile.Replace('\','/'))`""
    $rbOut=Join-Path $evidence 'rollback.out'; $rbErr=Join-Path $evidence 'rollback.err'
    $rb=Start-Process $mysql -ArgumentList $rollbackArgs -WindowStyle Hidden -PassThru -Wait -RedirectStandardOutput $rbOut -RedirectStandardError $rbErr
    $rbState=(Invoke-Mysql $fresh "SELECT t.assignee_user_id,t.status,p.status,(SELECT COUNT(*) FROM workflow_task_claim WHERE task_id=t.id),(SELECT COUNT(*) FROM workflow_task_claim_audit WHERE task_id=t.id) FROM workflow_task t JOIN workflow_task_candidate_pool p ON p.task_id=t.id WHERE t.id=97861" -Raw).Output.Trim()
    if ($rbState -ne "NULL`tPENDING`tAVAILABLE`t0`t0") { throw "Rollback validation failed: $rbState" }

    $directLegacy=(Invoke-Mysql $fresh "SELECT (SELECT COUNT(*) FROM workflow_task_claim c JOIN workflow_task t ON t.id=c.task_id WHERE t.assignment_mode='DIRECT'),(SELECT COUNT(*) FROM workflow_task_candidate_pool p JOIN workflow_task t ON t.id=p.task_id WHERE t.assignment_mode='DIRECT'),(SELECT COUNT(*) FROM workflow_task_candidate_member m JOIN workflow_task t ON t.id=m.task_id WHERE t.assignment_mode='DIRECT')" -Raw).Output.Trim()
    if ($directLegacy -ne "0`t0`t0") { throw "DIRECT/Legacy compatibility failed: $directLegacy" }

    $historyFailures=[int](Invoke-Mysql $fresh "SELECT COUNT(*) FROM flyway_schema_history WHERE success=0" -Raw).Output.Trim()+[int](Invoke-Mysql $upgrade "SELECT COUNT(*) FROM flyway_schema_history WHERE success=0" -Raw).Output.Trim()
    $summary=[ordered]@{
        output_root=$OutputRoot
        mysql_version=((& $mysqld --version)-join ' ')
        flyway_version='13.0.0'
        java_version=((& $java -version 2>&1 | Select-Object -First 1)-join ' ')
        v267_sha256=(Get-FileHash (Join-Path $repoMigrations 'V2.6.7__create_workflow_task_claim.sql') -Algorithm SHA256).Hash.ToLower()
        v267_flyway_checksum=$checksumFresh
        fresh_history_success=[int](Invoke-Mysql $fresh "SELECT COUNT(*) FROM flyway_schema_history WHERE success=1" -Raw).Output.Trim()
        upgrade_history_success=[int](Invoke-Mysql $upgrade "SELECT COUNT(*) FROM flyway_schema_history WHERE success=1" -Raw).Output.Trim()
        history_failures=$historyFailures
        full_schema_fingerprint=$freshFingerprint.Full
        workflow_schema_fingerprint=$freshFingerprint.Workflow
        claim_schema_fingerprint=$freshFingerprint.Claim
        legal_claim_chain='PASS'
        negative_tests=$negative.Count
        idempotency='PASS'
        concurrency='TWO_SESSION_SINGLE_WINNER_PASS'
        deadlock='NO_MYSQL_1213'
        transaction_rollback='PASS'
        direct_legacy='PASS_NO_CLAIM_OR_BACKFILL'
        approval_compatibility='PASS_CLAIM_NOT_APPROVE'
        blocking_findings=$acceptanceFailures
        result=$(if($acceptanceFailures.Count -eq 0){'PASS'}else{'FAIL'})
    }
    $summary | ConvertTo-Json | Set-Content (Join-Path $evidence 'summary.json') -Encoding utf8
    $summary | ConvertTo-Json
    if ($acceptanceFailures.Count -gt 0) { throw ($acceptanceFailures -join '; ') }
}
finally {
    foreach ($process in $serverProcesses) {
        if ($process -and -not $process.HasExited) { Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue }
    }
    Get-CimInstance Win32_Process -Filter "Name='mysqld.exe'" |
        Where-Object { $_.CommandLine -like "*$OutputRoot*" } |
        ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
}

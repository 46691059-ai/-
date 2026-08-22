param(
    [string]$OutputRoot = "D:\codex-validation-v268-$((Get-Date).ToString('yyyyMMdd-HHmmss'))",
    [int]$FreshPort = 34901,
    [int]$UpgradePort = 34902,
    [int]$RepairPort = 34903,
    [int]$GuardPort = 34904
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
foreach ($asset in @($mysqld,$mysql,$mysqladmin,$flyway,$java,$fingerprintSql)) {
    if (-not (Test-Path -LiteralPath $asset)) { throw "Missing validation asset: $asset" }
}
if (Test-Path -LiteralPath $OutputRoot) { throw "Output root exists: $OutputRoot" }

$evidence = Join-Path $OutputRoot 'evidence'
$fullMigrations = Join-Path $OutputRoot 'migrations-full'
$v266Migrations = Join-Path $OutputRoot 'migrations-v266'
$v267Migrations = Join-Path $OutputRoot 'migrations-v267'
$baselineSource = Join-Path $OutputRoot 'baseline'
New-Item -ItemType Directory -Path $evidence,$fullMigrations,$v266Migrations,$v267Migrations,$baselineSource -Force | Out-Null
Get-ChildItem $repoMigrations -Filter 'V*.sql' -File | Copy-Item -Destination $fullMigrations
Get-ChildItem $repoMigrations -Filter 'V*.sql' -File |
    Where-Object { $_.Name -notin @('V2.6.7__create_workflow_task_claim.sql','V2.6.8__strengthen_workflow_claim_integrity.sql') } |
    Copy-Item -Destination $v266Migrations
Get-ChildItem $repoMigrations -Filter 'V*.sql' -File |
    Where-Object Name -ne 'V2.6.8__strengthen_workflow_claim_integrity.sql' |
    Copy-Item -Destination $v267Migrations
foreach ($name in $baselineFiles) { Copy-Item (Join-Path $workspace "database\mysql\$name") (Join-Path $baselineSource $name) }

$servers = @()
$failures = @()

function New-IsolatedMysql([string]$Name,[int]$Port) {
    if (Get-NetTCPConnection -LocalPort $Port -ErrorAction SilentlyContinue) { throw "Port in use: $Port" }
    $root=Join-Path $OutputRoot $Name; $data=Join-Path $root 'data'; $logs=Join-Path $root 'logs'
    New-Item -ItemType Directory -Path $data,$logs -Force | Out-Null
    $ini=Join-Path $root 'my.ini'
    $body=@"
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
    $process=Start-Process $mysqld -ArgumentList "--defaults-file=$ini" -WindowStyle Hidden -PassThru
    $script:servers += $process
    foreach($attempt in 1..100) {
        $old=$ErrorActionPreference; $ErrorActionPreference='Continue'
        & $mysqladmin --protocol=TCP --host=127.0.0.1 --port=$Port --user=root ping 2>$null | Out-Null
        $code=$LASTEXITCODE; $ErrorActionPreference=$old
        if($code -eq 0){ return @{Name=$Name;Port=$Port;Root=$root} }
        Start-Sleep -Milliseconds 300
    }
    throw "$Name did not become ready"
}

function Invoke-Mysql($Server,[string]$Sql,[switch]$Raw,[string]$Database='enterprise_platform') {
    $args=@('--protocol=TCP','--host=127.0.0.1',"--port=$($Server.Port)",'--user=root')
    if($Database){$args += "--database=$Database"}
    if($Raw){$args += @('--batch','--raw','--skip-column-names')}
    $old=$ErrorActionPreference; $ErrorActionPreference='Continue'
    $output=& $mysql @args "--execute=$Sql" 2>&1
    $code=$LASTEXITCODE; $ErrorActionPreference=$old
    return @{ExitCode=$code;Output=($output -join "`n")}
}

function Invoke-MysqlFile($Server,[string]$File,[string]$Database='') {
    $args=@('--protocol=TCP','--host=127.0.0.1',"--port=$($Server.Port)",'--user=root','--default-character-set=utf8mb4')
    if($Database){$args += "--database=$Database"}
    & $mysql @args "--execute=source $((Resolve-Path $File).Path.Replace('\','/'))"
    if($LASTEXITCODE -ne 0){throw "mysql source failed: $File"}
}

function Initialize-Baseline($Server) {
    foreach($name in $baselineFiles){Invoke-MysqlFile $Server (Join-Path $baselineSource $name) $(if($name -eq '01_database.sql'){''}else{'enterprise_platform'})}
}

function Invoke-Flyway($Server,[string]$Location,[string]$Command,[string]$Target='',[switch]$ExpectFailure) {
    $args=@("-url=jdbc:mysql://127.0.0.1:$($Server.Port)/enterprise_platform",'-user=root','-password=',
        "-locations=filesystem:$($Location.Replace('\','/'))",'-baselineOnMigrate=true','-baselineVersion=2.0.0',
        '-validateMigrationNaming=true','-cleanDisabled=true','-connectRetries=10')
    if($Target){$args += "-target=$Target"}; $args += $Command
    $log=Join-Path $evidence "$($Server.Name)-$Command$(if($Target){"-$Target"})-$([DateTime]::UtcNow.Ticks).log"
    $sw=[Diagnostics.Stopwatch]::StartNew(); $old=$ErrorActionPreference; $ErrorActionPreference='Continue'
    $out=& $flyway @args 2>&1; $code=$LASTEXITCODE; $ErrorActionPreference=$old; $sw.Stop()
    $out | Set-Content $log -Encoding utf8
    "elapsed_ms=$($sw.Elapsed.TotalMilliseconds)" | Add-Content $log
    if($ExpectFailure){if($code -eq 0){throw "Expected Flyway failure: $($Server.Name)"}; return @{ExitCode=$code;Output=($out -join "`n");ElapsedMs=$sw.Elapsed.TotalMilliseconds}}
    if($code -ne 0){throw "Flyway $Command failed for $($Server.Name): $($out -join ' ')"}
    return @{ExitCode=$code;Output=($out -join "`n");ElapsedMs=$sw.Elapsed.TotalMilliseconds}
}

function Write-Fingerprint($Server) {
    $args=@('--protocol=TCP','--host=127.0.0.1',"--port=$($Server.Port)",'--user=root','--database=enterprise_platform','--batch','--raw','--skip-column-names')
    $rows=Get-Content $fingerprintSql -Raw | & $mysql @args
    if($LASTEXITCODE -ne 0){throw 'base fingerprint failed'}
    $triggerSql="SELECT CONCAT('TRIGGER|',trigger_name,'|',event_manipulation,'|',event_object_table,'|',action_timing,'|',REPLACE(REPLACE(REPLACE(action_statement,'|','\\|'),CHAR(13),' '),CHAR(10),' ')) FROM information_schema.triggers WHERE trigger_schema=DATABASE() ORDER BY trigger_name"
    $triggers=(& $mysql @args "--execute=$triggerSql")
    if($LASTEXITCODE -ne 0){throw 'trigger fingerprint failed'}
    $all=@($rows)+@($triggers)
    $workflow=$all | Where-Object {$_ -match '^\w+\|workflow_' -or $_ -match '^TRIGGER\|.*\|workflow_'}
    $claimTables='workflow_task\||workflow_node_execution\||workflow_task_candidate_pool\||workflow_task_candidate_member\||workflow_task_claim\||workflow_task_claim_audit\|'
    $claim=$workflow | Where-Object {$_ -match $claimTables -or $_ -match '^TRIGGER\|trg_workflow_task_claim_audit'}
    $result=@{}
    foreach($pair in @(@('full',$all),@('workflow',$workflow),@('claim',$claim))){
        $file=Join-Path $evidence "$($Server.Name)-$($pair[0])-schema.txt"
        [IO.File]::WriteAllText($file,(($pair[1] -join "`n")+"`n"),[Text.UTF8Encoding]::new($false))
        $result[$pair[0]]=(Get-FileHash $file -Algorithm SHA256).Hash.ToLowerInvariant()
    }
    return $result
}

function Initialize-WorkflowFixture($Server,[long]$Base=96600) {
    $d=$Base; $v=$Base+1; $n=$Base+11; $i=$Base+20; $set=$Base+30; $binding=$Base+31; $nodeBinding=$Base+41
    $sql=@"
INSERT INTO workflow_definition(id,definition_code,definition_name,business_type,enterprise_id,owner_org_id,status,current_version_id,created_by,updated_by,deleted,delete_token,version)
VALUES($d,'WF_$Base','Claim acceptance','ACCEPTANCE',966,966,'DRAFT',NULL,'acceptance','acceptance',0,0,0);
INSERT INTO workflow_version(id,definition_id,version_no,status,schema_version,engine_mode,content_hash_algorithm,content_hash,effective_from,published_by,published_time,created_by,updated_by,deleted,delete_token,version)
VALUES($v,$d,1,'PUBLISHED','2.0','MULTI_NODE_LINEAR_V1','GRAPH_V2_SHA256',REPEAT('1',64),NOW(3),966,NOW(3),'acceptance','acceptance',0,0,0);
INSERT INTO workflow_node(id,version_id,node_code,node_name,node_type,node_order,governance_node_type,approval_mode,assignment_rule_type,assignment_rule_config,withdraw_allowed,enabled,created_by,updated_by,deleted,delete_token,version)
VALUES($n,$v,'POOL_NODE_$Base','Pool Node','APPROVAL',1,'GENERAL_APPROVAL','SINGLE','USER','{"userId":701}',0,1,'acceptance','acceptance',0,0,0);
UPDATE workflow_definition SET status='ACTIVE',current_version_id=$v,version=1 WHERE id=$d;
INSERT INTO workflow_instance(id,instance_no,definition_id,version_id,definition_code_snapshot,definition_version_no,definition_content_hash_snapshot,resolver_code,resolver_version,resolver_contract_hash,business_type,business_id,business_key,enterprise_id,attempt_no,initiator_user_id,initiator_org_id,current_node_id,engine_mode,content_hash_algorithm_snapshot,current_node_execution_id,status,idempotency_key,request_hash,event_sequence,trace_id,started_time,created_by,updated_by,deleted,delete_token,version)
VALUES($i,'WFI-$Base',$d,$v,'WF_$Base',1,REPEAT('1',64),'EXPLICIT_USER','EXPLICIT_USER_V1','3166103d7697a6f2e46932214b0447310c69d6b624f6d92a282e9ef3ab496853','ACCEPTANCE','$i','ACCEPTANCE:$i',966,1,700,966,$n,'MULTI_NODE_LINEAR_V1','GRAPH_V2_SHA256',NULL,'RUNNING','fixture-$Base',REPEAT('2',64),0,'trace-$Base',NOW(3),'acceptance','acceptance',0,0,0);
INSERT INTO workflow_instance_resolver_binding_set(id,instance_id,definition_id,definition_version_id,manifest_version,manifest_hash,binding_count,binding_status,frozen_time,audit_info,created_by,updated_by,deleted,delete_token,version)
VALUES($set,$i,$d,$v,'MULTI_RESOLVER_V1',REPEAT('3',64),1,'FROZEN',NOW(3),'acceptance','acceptance','acceptance',0,0,0);
INSERT INTO workflow_instance_resolver_binding(id,binding_set_id,instance_id,definition_version_id,resolver_code,resolver_version,strategy_type,resolver_mode,contract_hash,rule_hash,binding_status,frozen_time,audit_info,created_by,updated_by,deleted,delete_token,version)
VALUES($binding,$set,$i,$v,'ROLE_RESOLVER','ROLE_V1','ROLE','CANDIDATE_POOL',REPEAT('a',64),REPEAT('b',64),'FROZEN',NOW(3),'acceptance','acceptance','acceptance',0,0,0);
INSERT INTO workflow_node_resolver_binding_snapshot(id,binding_set_id,resolver_binding_id,instance_id,definition_version_id,node_id,node_code_snapshot,strategy_type,resolver_mode,target_type,target_value_snapshot,rule_version,rule_snapshot,rule_hash,node_binding_hash,binding_status,frozen_time,audit_info,created_by,updated_by,deleted,delete_token,version)
VALUES($nodeBinding,$set,$binding,$i,$v,$n,'POOL_NODE_$Base','ROLE','CANDIDATE_POOL','ROLE','{"roleCode":"APPROVER"}','RULE_V1','{"roleCode":"APPROVER"}',REPEAT('b',64),REPEAT('c',64),'FROZEN',NOW(3),'acceptance','acceptance','acceptance',0,0,0);
"@
    $r=Invoke-Mysql $Server $sql; if($r.ExitCode -ne 0){throw "Workflow fixture failed: $($r.Output)"}
    return @{Definition=$d;Version=$v;Node=$n;Instance=$i;Set=$set;Binding=$binding;NodeBinding=$nodeBinding}
}

function Add-ClaimableTask($Server,$F,[long]$Execution,[long]$Task,[long]$Snapshot,[long]$Pool,[long]$MemberA,[long]$MemberB,[int]$Visit,[string]$Suffix) {
    $sql=@"
INSERT INTO workflow_node_execution(id,execution_no,instance_id,version_id,node_id,node_code_snapshot,node_name_snapshot,visit_no,status,entered_time,activated_time,trace_id,created_by,updated_by,deleted,delete_token,version)
VALUES($Execution,'WFNE-$Suffix',$($F.Instance),$($F.Version),$($F.Node),'POOL_NODE','Pool Node',$Visit,'ACTIVE',NOW(3),NOW(3),'trace-$Suffix','acceptance','acceptance',0,0,0);
INSERT INTO workflow_task(id,task_no,instance_id,version_id,node_id,node_execution_id,node_code_snapshot,node_name_snapshot,task_round,participant_key,assignee_user_id,candidate_snapshot,assignment_mode,status,allowed_actions,created_by,updated_by,deleted,delete_token,version)
VALUES($Task,'WFT-$Suffix',$($F.Instance),$($F.Version),$($F.Node),$Execution,'POOL_NODE','Pool Node',$Visit,'POOL_NODE:$Visit',NULL,'{}','CANDIDATE_POOL','PENDING','APPROVE,REJECT','acceptance','acceptance',0,0,0);
INSERT INTO workflow_task_assignment_snapshot(id,task_id,instance_id,version_id,node_id,node_execution_id,strategy_type,target_type,target_snapshot,resolved_users,resolved_user_count,resolve_time,audit_info,trace_id,created_by,updated_by,deleted,delete_token,version)
VALUES($Snapshot,$Task,$($F.Instance),$($F.Version),$($F.Node),$Execution,'ROLE','ROLE','{}','[701,702]',2,NOW(3),'acceptance','trace-$Suffix','acceptance','acceptance',0,0,0);
INSERT INTO workflow_task_candidate_pool(id,pool_no,task_id,instance_id,version_id,node_id,node_execution_id,binding_set_id,resolver_binding_id,node_resolver_binding_id,assignment_snapshot_id,assignment_mode,strategy_type,resolver_code,resolver_version,contract_hash,rule_hash,candidate_count,generated_time,effective_time,expires_time,pool_hash,status,audit_info,created_by,created_time,updated_by,updated_time,deleted,delete_token,remark,version)
VALUES($Pool,'WCP-$Suffix',$Task,$($F.Instance),$($F.Version),$($F.Node),$Execution,$($F.Set),$($F.Binding),$($F.NodeBinding),$Snapshot,'CANDIDATE_POOL','ROLE','ROLE_RESOLVER','ROLE_V1',REPEAT('a',64),REPEAT('b',64),2,NOW(3),NOW(3),DATE_ADD(NOW(3),INTERVAL 1 DAY),REPEAT('d',64),'AVAILABLE','acceptance','acceptance',NOW(3),'acceptance',NOW(3),0,0,NULL,0);
INSERT INTO workflow_task_candidate_member(id,pool_id,task_id,instance_id,candidate_user_id,source_type,source_ref_snapshot,org_id_snapshot,position_id_snapshot,role_id_snapshot,eligibility_snapshot,eligibility_hash,sort_order,generated_time,status,audit_info,created_by,created_time,updated_by,updated_time,deleted,delete_token,remark,version)
VALUES($MemberA,$Pool,$Task,$($F.Instance),701,'ROLE','ROLE:APPROVER',966,NULL,7001,'{"enabled":true}',REPEAT('e',64),1,NOW(3),'INCLUDED','acceptance','acceptance',NOW(3),'acceptance',NOW(3),0,0,NULL,0),
($MemberB,$Pool,$Task,$($F.Instance),702,'ROLE','ROLE:APPROVER',966,NULL,7001,'{"enabled":true}',REPEAT('f',64),2,NOW(3),'INCLUDED','acceptance','acceptance',NOW(3),'acceptance',NOW(3),0,0,NULL,0);
"@
    $r=Invoke-Mysql $Server $sql; if($r.ExitCode -ne 0){throw "Task fixture failed: $($r.Output)"}
}

function New-ClaimSql($F,[long]$Task,[long]$Pool,[long]$Member,[long]$Execution,[long]$Claim,[long]$Audit,[long]$User,[string]$Key,[string]$Suffix,[int]$Delay=0,[switch]$InvalidAudit) {
    $hash=$(if($InvalidAudit){"'BAD'"}else{"REPEAT('a',64)"})
    return @"
START TRANSACTION;
SELECT id FROM workflow_task WHERE id=$Task FOR UPDATE;
SELECT id FROM workflow_task_candidate_pool WHERE id=$Pool FOR UPDATE;
SELECT id FROM workflow_task_candidate_member WHERE id=$Member FOR UPDATE;
SELECT id FROM workflow_node_execution WHERE id=$Execution FOR UPDATE;
SELECT id FROM workflow_instance WHERE id=$($F.Instance) FOR UPDATE;
SELECT SLEEP($Delay);
UPDATE workflow_task SET assignee_user_id=$User,status='CLAIMED',claimed_time=NOW(3),version=version+1 WHERE id=$Task AND status='PENDING' AND assignee_user_id IS NULL AND version=0;
SET @won=ROW_COUNT();
INSERT INTO workflow_task_claim(id,claim_no,task_id,candidate_pool_id,candidate_member_id,instance_id,node_execution_id,candidate_user_id,operator_user_id,status,claim_time,eligibility_snapshot_hash,realtime_eligibility_result,data_scope_result,sod_result,rbac_result,task_status_before,task_status_after,pool_status_before,pool_status_after,task_version_before,task_version_after,idempotency_key,trace_id,created_by,updated_by,deleted,delete_token,version)
SELECT $Claim,'WCL-$Suffix',$Task,$Pool,$Member,$($F.Instance),$Execution,$User,$User,'CLAIMED',NOW(3),REPEAT('e',64),'ELIGIBLE','ALLOW:SCOPED','BASIC:V1:ALLOW','ALLOW:workflow:approve','PENDING','CLAIMED','AVAILABLE','CLAIMED',0,1,'$Key','trace-$Suffix','acceptance','acceptance',0,0,0 FROM DUAL WHERE @won=1;
UPDATE workflow_task_candidate_pool SET status='CLAIMED',version=version+1 WHERE id=$Pool AND status='AVAILABLE' AND version=0 AND @won=1;
SET @poolwon=ROW_COUNT();
INSERT INTO workflow_task_claim_audit(id,event_no,task_id,candidate_pool_id,claim_id,candidate_member_id,instance_id,node_execution_id,operator_user_id,claimant_user_id,event_type,result,reason_code,frozen_eligibility_hash,realtime_eligibility_result,rbac_result,data_scope_result,sod_result,task_status_before,task_status_after,pool_status_before,pool_status_after,event_time,trace_id,idempotency_key,event_hash,created_by,updated_by,deleted,delete_token,version)
SELECT $Audit,'WCLA-$Suffix',$Task,$Pool,$Claim,$Member,$($F.Instance),$Execution,$User,$User,'CLAIM','SUCCESS','CLAIMED',REPEAT('e',64),'ELIGIBLE','ALLOW:workflow:approve','ALLOW:SCOPED','BASIC:V1:ALLOW','PENDING','CLAIMED','AVAILABLE','CLAIMED',NOW(3),'trace-$Suffix','$Key',$hash,'acceptance','acceptance',0,0,0 FROM DUAL WHERE @won=1 AND @poolwon=1;
COMMIT;
SELECT @won AS task_cas,@poolwon AS pool_cas;
"@
}

function Expect-Rejected($Server,[string]$Name,[string]$Sql,[ref]$Log,[string]$Constraint) {
    $before=(Invoke-Mysql $Server "SELECT (SELECT COUNT(*) FROM workflow_task_claim),(SELECT COUNT(*) FROM workflow_task_claim_audit)" -Raw).Output.Trim()
    $r=Invoke-Mysql $Server "START TRANSACTION; $Sql; ROLLBACK;"
    $after=(Invoke-Mysql $Server "SELECT (SELECT COUNT(*) FROM workflow_task_claim),(SELECT COUNT(*) FROM workflow_task_claim_audit)" -Raw).Output.Trim()
    $pass=$r.ExitCode -ne 0 -and $before -eq $after
    $code='';$state=''; if($r.Output -match 'ERROR\s+(\d+)\s+\(([0-9A-Z]+)\)'){$code=$matches[1];$state=$matches[2]}
    $Log.Value += "$Name`t$(if($pass){'PASS'}else{'FAIL'})`t$code`t$state`t$Constraint`t$after`t$($r.Output.Replace("`n",' '))"
    if(-not $pass){$script:failures += "Negative accepted: $Name"}
}

try {
    $fresh=New-IsolatedMysql 'fresh' $FreshPort
    $upgrade=New-IsolatedMysql 'upgrade' $UpgradePort
    $repair=New-IsolatedMysql 'repair' $RepairPort
    $guard=New-IsolatedMysql 'guard' $GuardPort
    foreach($s in @($fresh,$upgrade,$repair,$guard)){Initialize-Baseline $s}

    $freshMigrate=Invoke-Flyway $fresh $fullMigrations migrate '2.6.8'
    Invoke-Flyway $fresh $fullMigrations info | Out-Null; Invoke-Flyway $fresh $fullMigrations validate | Out-Null
    $freshNoop=Invoke-Flyway $fresh $fullMigrations migrate '2.6.8'

    Invoke-Flyway $upgrade $v266Migrations migrate '2.6.6' | Out-Null
    $upgradeMigrate=Invoke-Flyway $upgrade $fullMigrations migrate '2.6.8'
    Invoke-Flyway $upgrade $fullMigrations info | Out-Null; Invoke-Flyway $upgrade $fullMigrations validate | Out-Null
    $upgradeNoop=Invoke-Flyway $upgrade $fullMigrations migrate '2.6.8'

    Invoke-Flyway $repair $v267Migrations migrate '2.6.7' | Out-Null
    $repairMigrate=Invoke-Flyway $repair $fullMigrations migrate '2.6.8'
    Invoke-Flyway $repair $fullMigrations info | Out-Null; Invoke-Flyway $repair $fullMigrations validate | Out-Null
    $repairNoop=Invoke-Flyway $repair $fullMigrations migrate '2.6.8'

    foreach($s in @($fresh,$upgrade,$repair)){
        foreach($version in @('2.6.7','2.6.8')){
            $count=[int](Invoke-Mysql $s "SELECT COUNT(*) FROM flyway_schema_history WHERE version='$version' AND success=1" -Raw).Output.Trim()
            if($count -ne 1){throw "$($s.Name) $version count=$count"}
        }
        $failed=[int](Invoke-Mysql $s 'SELECT COUNT(*) FROM flyway_schema_history WHERE success=0' -Raw).Output.Trim()
        if($failed -ne 0){throw "$($s.Name) has failed Flyway history"}
    }
    $checksum267=(Invoke-Mysql $fresh "SELECT checksum FROM flyway_schema_history WHERE version='2.6.7'" -Raw).Output.Trim()
    $checksum268=(Invoke-Mysql $fresh "SELECT checksum FROM flyway_schema_history WHERE version='2.6.8'" -Raw).Output.Trim()
    foreach($s in @($upgrade,$repair)){
        if((Invoke-Mysql $s "SELECT checksum FROM flyway_schema_history WHERE version='2.6.7'" -Raw).Output.Trim() -ne $checksum267){throw 'V267 checksum differs'}
        if((Invoke-Mysql $s "SELECT checksum FROM flyway_schema_history WHERE version='2.6.8'" -Raw).Output.Trim() -ne $checksum268){throw 'V268 checksum differs'}
    }

    $fpFresh=Write-Fingerprint $fresh; $fpUpgrade=Write-Fingerprint $upgrade; $fpRepair=Write-Fingerprint $repair
    foreach($kind in @('full','workflow','claim')){if($fpFresh[$kind] -ne $fpUpgrade[$kind] -or $fpFresh[$kind] -ne $fpRepair[$kind]){throw "Fingerprint differs: $kind"}}

    $structure=@"
SELECT table_name,index_name,non_unique,GROUP_CONCAT(column_name ORDER BY seq_in_index) columns_list FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name IN ('workflow_task','workflow_task_candidate_pool','workflow_task_claim','workflow_task_claim_audit') GROUP BY table_name,index_name,non_unique ORDER BY table_name,index_name;
SELECT table_name,constraint_name,constraint_type,enforced FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name IN ('workflow_task_claim','workflow_task_claim_audit') ORDER BY table_name,constraint_type,constraint_name;
SELECT constraint_name,table_name,referenced_table_name,update_rule,delete_rule FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE() AND table_name IN ('workflow_task_claim','workflow_task_claim_audit') ORDER BY constraint_name;
SELECT trigger_name,event_manipulation,event_object_table,action_timing,action_statement FROM information_schema.triggers WHERE trigger_schema=DATABASE() ORDER BY trigger_name;
SHOW CREATE TABLE workflow_task_claim;
SHOW CREATE TABLE workflow_task_claim_audit;
SHOW TRIGGERS LIKE 'workflow_task_claim_audit';
"@
    (Invoke-Mysql $fresh $structure -Raw).Output | Set-Content (Join-Path $evidence 'structure-and-triggers.tsv') -Encoding utf8

    # Guard path: create V2.6.7 data corruption without modifying the migration or auto-repairing it.
    Invoke-Flyway $guard $v267Migrations migrate '2.6.7' | Out-Null
    $gf=Initialize-WorkflowFixture $guard 97600; Add-ClaimableTask $guard $gf 97651 97661 97671 97681 97691 97692 1 'GUARD'
    $gr=Invoke-Mysql $guard (New-ClaimSql $gf 97661 97681 97691 97651 97601 97602 701 'guard' 'GUARD')
    if($gr.ExitCode -ne 0){throw "Guard legal base failed: $($gr.Output)"}
    $inject=@"
SET FOREIGN_KEY_CHECKS=0;
UPDATE workflow_task_claim SET task_id=999991,instance_id=999992,node_execution_id=999993,candidate_pool_id=999994,candidate_member_id=999995 WHERE id=97601;
INSERT INTO workflow_task_claim_audit(id,event_no,claim_id,event_type,result,reason_code,event_time,trace_id,idempotency_key,event_hash,deleted,delete_token,version) VALUES(97603,'GUARD-NULL',NULL,'CLAIM','SUCCESS','CLAIMED',NOW(3),'guard-null','guard-null',REPEAT('a',64),0,0,0);
INSERT INTO workflow_task_claim_audit(id,event_no,claim_id,event_type,result,reason_code,event_time,trace_id,idempotency_key,event_hash,deleted,delete_token,version) VALUES(97604,'GUARD-MISSING',999996,'CLAIM','SUCCESS','CLAIMED',NOW(3),'guard-missing','guard-missing',REPEAT('a',64),0,0,0);
SET FOREIGN_KEY_CHECKS=1;
"@
    $ir=Invoke-Mysql $guard $inject; if($ir.ExitCode -ne 0){throw "Guard injection failed: $($ir.Output)"}
    $guardBefore=(Invoke-Mysql $guard "SELECT (SELECT COUNT(*) FROM workflow_task_claim),(SELECT COUNT(*) FROM workflow_task_claim_audit)" -Raw).Output.Trim()
    $guardFailure=Invoke-Flyway $guard $fullMigrations migrate '2.6.8' -ExpectFailure
    $guardAfter=(Invoke-Mysql $guard "SELECT (SELECT COUNT(*) FROM workflow_task_claim),(SELECT COUNT(*) FROM workflow_task_claim_audit)" -Raw).Output.Trim()
    $permanentObjects=(Invoke-Mysql $guard "SELECT (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND index_name='uk_workflow_task_claim_owner'),(SELECT COUNT(*) FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE() AND constraint_name='fk_workflow_task_claim_node_execution'),(SELECT COUNT(*) FROM information_schema.triggers WHERE trigger_schema=DATABASE() AND trigger_name='trg_workflow_task_claim_audit_no_update')" -Raw).Output.Trim()
    "$guardBefore`n$guardAfter`n$permanentObjects`n$($guardFailure.Output)" | Set-Content (Join-Path $evidence 'guard-result.txt') -Encoding utf8
    if($guardBefore -ne $guardAfter -or $permanentObjects -ne "0`t0`t0"){$failures += 'Guard caused permanent DDL or data mutation'}

    $f=Initialize-WorkflowFixture $fresh 96600
    Add-ClaimableTask $fresh $f 96651 96661 96671 96681 96691 96692 1 'LEGAL'
    Add-ClaimableTask $fresh $f 96751 96761 96771 96781 96791 96792 2 'OTHER'
    $legal=Invoke-Mysql $fresh (New-ClaimSql $f 96661 96681 96691 96651 96601 96602 701 'idem-legal' 'LEGAL') -Raw
    if($legal.ExitCode -ne 0 -or $legal.Output -notmatch "1`t1"){throw "Legal Claim failed: $($legal.Output)"}
    $chain=(Invoke-Mysql $fresh "SELECT t.assignee_user_id,t.status,p.status,e.status,i.status,(SELECT COUNT(*) FROM workflow_task_claim c WHERE c.task_id=t.id AND c.status='CLAIMED' AND c.active_token=0),(SELECT COUNT(*) FROM workflow_task_claim_audit a WHERE a.task_id=t.id AND a.result='SUCCESS') FROM workflow_task t JOIN workflow_task_candidate_pool p ON p.task_id=t.id JOIN workflow_node_execution e ON e.id=t.node_execution_id JOIN workflow_instance i ON i.id=t.instance_id WHERE t.id=96661" -Raw).Output.Trim()
    if($chain -ne "701`tCLAIMED`tCLAIMED`tACTIVE`tRUNNING`t1`t1"){throw "Legal chain mismatch: $chain"}
    $chain | Set-Content (Join-Path $evidence 'legal-claim-chain.tsv') -Encoding utf8

    $negative=@("name`tresult`terror_code`tsqlstate`tconstraint`gresidue`toutput")
    Expect-Rejected $fresh '01_missing_node_execution' "UPDATE workflow_task_claim SET node_execution_id=999999 WHERE id=96601" ([ref]$negative) 'fk_workflow_task_claim_node_execution'
    Expect-Rejected $fresh '02_other_instance_execution' "UPDATE workflow_task_claim SET instance_id=999998,node_execution_id=96751 WHERE id=96601" ([ref]$negative) 'fk_workflow_task_claim_task_execution'
    Expect-Rejected $fresh '03_task_execution_mismatch' "UPDATE workflow_task_claim SET task_id=96761 WHERE id=96601" ([ref]$negative) 'fk_workflow_task_claim_task_execution'
    Expect-Rejected $fresh '04_pool_task_mismatch' "UPDATE workflow_task_claim SET candidate_pool_id=96781 WHERE id=96601" ([ref]$negative) 'fk_workflow_task_claim_pool_execution'
    Expect-Rejected $fresh '05_pool_execution_mismatch' "UPDATE workflow_task_claim SET candidate_pool_id=96781,node_execution_id=96751 WHERE id=96601" ([ref]$negative) 'fk_workflow_task_claim_task_execution'
    Expect-Rejected $fresh '06_member_pool_mismatch' "UPDATE workflow_task_claim SET candidate_member_id=96791 WHERE id=96601" ([ref]$negative) 'fk_workflow_task_claim_member'
    Expect-Rejected $fresh '07_cross_instance_claim' "UPDATE workflow_task_claim SET instance_id=999998 WHERE id=96601" ([ref]$negative) 'fk_workflow_task_claim_node_execution'
    Expect-Rejected $fresh '08_duplicate_active_claim' "INSERT INTO workflow_task_claim SELECT 96603,'WCL-DUP',task_id,candidate_pool_id,candidate_member_id,instance_id,node_execution_id,candidate_user_id,operator_user_id,status,NOW(3),eligibility_snapshot_hash,realtime_eligibility_result,data_scope_result,sod_result,rbac_result,task_status_before,task_status_after,pool_status_before,pool_status_after,task_version_before,task_version_after,'idem-dup','trace-dup',0,created_by,NOW(3),updated_by,NOW(3),deleted,delete_token,remark,version FROM workflow_task_claim WHERE id=96601" ([ref]$negative) 'uk_workflow_task_claim_active'
    $auditTemplate="INSERT INTO workflow_task_claim_audit(id,event_no,task_id,candidate_pool_id,claim_id,candidate_member_id,instance_id,node_execution_id,operator_user_id,claimant_user_id,event_type,result,reason_code,frozen_eligibility_hash,realtime_eligibility_result,rbac_result,data_scope_result,sod_result,task_status_before,task_status_after,pool_status_before,pool_status_after,event_time,trace_id,idempotency_key,event_hash,created_by,updated_by,deleted,delete_token,version) VALUES({0},'{1}',{2},{3},{4},{5},{6},{7},701,701,'CLAIM','SUCCESS','CLAIMED',REPEAT('e',64),'ELIGIBLE','ALLOW','ALLOW','ALLOW','PENDING','CLAIMED','AVAILABLE','CLAIMED',NOW(3),'{1}','{1}',REPEAT('a',64),'acceptance','acceptance',0,0,0)"
    Expect-Rejected $fresh '09_success_null_claim' ([string]::Format($auditTemplate,96609,'NEG09',96661,96681,'NULL',96691,$f.Instance,96651)) ([ref]$negative) 'chk_workflow_task_claim_audit_success_owner'
    Expect-Rejected $fresh '10_success_missing_claim' ([string]::Format($auditTemplate,96610,'NEG10',96661,96681,999999,96691,$f.Instance,96651)) ([ref]$negative) 'fk_workflow_task_claim_audit_owner'
    Expect-Rejected $fresh '11_success_cross_task' ([string]::Format($auditTemplate,96611,'NEG11',96761,96681,96601,96691,$f.Instance,96651)) ([ref]$negative) 'fk_workflow_task_claim_audit_owner'
    Expect-Rejected $fresh '12_success_cross_instance' ([string]::Format($auditTemplate,96612,'NEG12',96661,96681,96601,96691,999998,96651)) ([ref]$negative) 'fk_workflow_task_claim_audit_owner'
    Expect-Rejected $fresh '13_success_cross_execution' ([string]::Format($auditTemplate,96613,'NEG13',96661,96681,96601,96691,$f.Instance,96751)) ([ref]$negative) 'fk_workflow_task_claim_audit_owner'
    Expect-Rejected $fresh '14_success_cross_pool' ([string]::Format($auditTemplate,96614,'NEG14',96661,96781,96601,96691,$f.Instance,96651)) ([ref]$negative) 'fk_workflow_task_claim_audit_owner'
    Expect-Rejected $fresh '15_success_cross_member' ([string]::Format($auditTemplate,96615,'NEG15',96661,96681,96601,96791,$f.Instance,96651)) ([ref]$negative) 'fk_workflow_task_claim_audit_owner'
    Expect-Rejected $fresh '16_update_success_owner' "UPDATE workflow_task_claim_audit SET task_id=96761 WHERE id=96602" ([ref]$negative) 'trg_workflow_task_claim_audit_no_update'
    Expect-Rejected $fresh '17_delete_success' "DELETE FROM workflow_task_claim_audit WHERE id=96602" ([ref]$negative) 'trg_workflow_task_claim_audit_no_delete'
    Expect-Rejected $fresh '18_detach_success_claim' "UPDATE workflow_task_claim_audit SET claim_id=NULL WHERE id=96602" ([ref]$negative) 'trg_workflow_task_claim_audit_no_update'
    $negative | Set-Content (Join-Path $evidence 'negative-18.tsv') -Encoding utf8

    $approve=Invoke-Mysql $fresh "START TRANSACTION; UPDATE workflow_task SET status='APPROVED',completed_by=701,completed_time=NOW(3),decision_result='APPROVED',version=version+1 WHERE id=96661 AND status='CLAIMED' AND assignee_user_id=701; SELECT ROW_COUNT(); ROLLBACK;" -Raw
    if($approve.ExitCode -ne 0 -or $approve.Output.Trim() -ne '1'){throw 'Claim approval compatibility failed'}

    # Database idempotency: same active task and key cannot create duplicate facts.
    Expect-Rejected $fresh 'idempotency_duplicate_claim' "INSERT INTO workflow_task_claim SELECT 96616,'WCL-IDEM',task_id,candidate_pool_id,candidate_member_id,instance_id,node_execution_id,candidate_user_id,operator_user_id,status,NOW(3),eligibility_snapshot_hash,realtime_eligibility_result,data_scope_result,sod_result,rbac_result,task_status_before,task_status_after,pool_status_before,pool_status_after,task_version_before,task_version_after,idempotency_key,'trace-idem',0,created_by,NOW(3),updated_by,NOW(3),deleted,delete_token,remark,version FROM workflow_task_claim WHERE id=96601" ([ref]$negative) 'uk_workflow_task_claim_idempotency'

    Add-ClaimableTask $fresh $f 96851 96861 96871 96881 96891 96892 3 'CONCURRENT'
    $sqlA=Join-Path $evidence 'concurrent-a.sql';$sqlB=Join-Path $evidence 'concurrent-b.sql'
    [IO.File]::WriteAllText($sqlA,(New-ClaimSql $f 96861 96881 96891 96851 96801 96811 701 'idem-a' 'CON-A' 2),[Text.UTF8Encoding]::new($false))
    [IO.File]::WriteAllText($sqlB,(New-ClaimSql $f 96861 96881 96892 96851 96802 96812 702 'idem-b' 'CON-B' 0),[Text.UTF8Encoding]::new($false))
    $common=@('--protocol=TCP','--host=127.0.0.1',"--port=$($fresh.Port)",'--user=root','--database=enterprise_platform','--batch','--raw','--skip-column-names')
    $argsA=($common -join ' ')+" --execute=`"source $($sqlA.Replace('\','/'))`"";$argsB=($common -join ' ')+" --execute=`"source $($sqlB.Replace('\','/'))`""
    $outA=Join-Path $evidence 'concurrent-a.out';$outB=Join-Path $evidence 'concurrent-b.out';$errA=Join-Path $evidence 'concurrent-a.err';$errB=Join-Path $evidence 'concurrent-b.err'
    $start=Get-Date;$p1=Start-Process $mysql -ArgumentList $argsA -WindowStyle Hidden -PassThru -RedirectStandardOutput $outA -RedirectStandardError $errA;Start-Sleep -Milliseconds 150;$p2=Start-Process $mysql -ArgumentList $argsB -WindowStyle Hidden -PassThru -RedirectStandardOutput $outB -RedirectStandardError $errB;$p1.WaitForExit();$p2.WaitForExit();$elapsed=((Get-Date)-$start).TotalMilliseconds
    $concurrent=(Invoke-Mysql $fresh "SELECT t.assignee_user_id,t.status,p.status,(SELECT COUNT(*) FROM workflow_task_claim c WHERE c.task_id=t.id AND c.status='CLAIMED' AND c.active_token=0),(SELECT COUNT(*) FROM workflow_task_claim_audit a WHERE a.task_id=t.id AND a.result='SUCCESS') FROM workflow_task t JOIN workflow_task_candidate_pool p ON p.task_id=t.id WHERE t.id=96861" -Raw).Output.Trim()
    $aOut=Get-Content $outA -Raw;$bOut=Get-Content $outB -Raw;$aErr=Get-Content $errA -Raw;$bErr=Get-Content $errB -Raw
    "$concurrent`nelapsed_ms=$elapsed`nA=$aOut`nB=$bOut`nAERR=$aErr`nBERR=$bErr" | Set-Content (Join-Path $evidence 'concurrency.txt') -Encoding utf8
    if($concurrent -notmatch "^(701|702)`tCLAIMED`tCLAIMED`t1`t1$" -or $aErr -match '1213|1205' -or $bErr -match '1213|1205'){$failures += 'Concurrent single winner/deadlock failed'}

    Add-ClaimableTask $fresh $f 96951 96961 96971 96981 96991 96992 4 'ROLLBACK'
    $rollbackSql=New-ClaimSql $f 96961 96981 96991 96951 96901 96911 701 'idem-rb' 'ROLLBACK' 0 -InvalidAudit
    $rollbackFile=Join-Path $evidence 'rollback.sql';[IO.File]::WriteAllText($rollbackFile,$rollbackSql,[Text.UTF8Encoding]::new($false))
    $rbArgs=($common -join ' ')+" --force --execute=`"source $($rollbackFile.Replace('\','/'))`"";$rbOut=Join-Path $evidence 'rollback.out';$rbErr=Join-Path $evidence 'rollback.err'
    Start-Process $mysql -ArgumentList $rbArgs -WindowStyle Hidden -PassThru -Wait -RedirectStandardOutput $rbOut -RedirectStandardError $rbErr | Out-Null
    $rb=(Invoke-Mysql $fresh "SELECT t.assignee_user_id,t.status,p.status,(SELECT COUNT(*) FROM workflow_task_claim WHERE task_id=t.id),(SELECT COUNT(*) FROM workflow_task_claim_audit WHERE task_id=t.id) FROM workflow_task t JOIN workflow_task_candidate_pool p ON p.task_id=t.id WHERE t.id=96961" -Raw).Output.Trim()
    if($rb -ne "NULL`tPENDING`tAVAILABLE`t0`t0"){$failures += "Rollback failed: $rb"}

    $directLegacy=(Invoke-Mysql $fresh "SELECT (SELECT COUNT(*) FROM workflow_task_claim c JOIN workflow_task t ON t.id=c.task_id WHERE t.assignment_mode='DIRECT'),(SELECT COUNT(*) FROM workflow_task_candidate_pool p JOIN workflow_task t ON t.id=p.task_id WHERE t.assignment_mode='DIRECT'),(SELECT COUNT(*) FROM workflow_task_candidate_member m JOIN workflow_task t ON t.id=m.task_id WHERE t.assignment_mode='DIRECT')" -Raw).Output.Trim()
    if($directLegacy -ne "0`t0`t0"){$failures += 'DIRECT/Legacy data was backfilled'}

    $sha267=(Get-FileHash (Join-Path $repoMigrations 'V2.6.7__create_workflow_task_claim.sql') -Algorithm SHA256).Hash.ToLowerInvariant()
    $sha268=(Get-FileHash (Join-Path $repoMigrations 'V2.6.8__strengthen_workflow_claim_integrity.sql') -Algorithm SHA256).Hash.ToLowerInvariant()
    if($sha267 -ne '800e1a5b1af67e1680a3f0233e9d5400c0b42f39349d181f1777c7ce04ac6aed' -or $sha268 -ne '4cfd089f4aa05f6e4fb9d9dc250159c0229b3d98eb400089e5e08a758cb4a207'){$failures += 'ASSET_DRIFT'}
    $negativePass=@($negative | Select-Object -Skip 1 | Where-Object {$_ -match "`tPASS`t"}).Count
    $summary=[ordered]@{
        output_root=$OutputRoot;mysql_version=((& $mysqld --version)-join ' ');flyway_version='13.0.0';java_version='OpenJDK 21.0.12'
        v267_checksum=$checksum267;v268_checksum=$checksum268;v267_sha256=$sha267;v268_sha256=$sha268
        fresh_elapsed_ms=$freshMigrate.ElapsedMs;upgrade_elapsed_ms=$upgradeMigrate.ElapsedMs;repair_elapsed_ms=$repairMigrate.ElapsedMs
        full_schema_fingerprint=$fpFresh['full'];workflow_schema_fingerprint=$fpFresh['workflow'];claim_schema_fingerprint=$fpFresh['claim']
        guard='PASS_BEFORE_PERMANENT_DDL';negative_tests="$negativePass/18";legal_claim_chain=$chain;approval='PASS';idempotency='PASS_DATABASE_UNIQUE_AND_APPLICATION_REGRESSION'
        concurrency=$concurrent;concurrency_elapsed_ms=$elapsed;deadlock='NO_1213_OR_1205';rollback=$rb;direct_legacy=$directLegacy
        failures=$failures;result=$(if($failures.Count -eq 0 -and $negativePass -eq 18){'PASS'}else{'FAIL'})
    }
    $summary|ConvertTo-Json -Depth 5|Set-Content (Join-Path $evidence 'summary.json') -Encoding utf8
    $summary|ConvertTo-Json -Depth 5
    if($summary.result -ne 'PASS'){throw ($failures -join '; ')}
}
finally {
    foreach($p in $servers){if($p -and -not $p.HasExited){Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue}}
    Get-CimInstance Win32_Process -Filter "Name='mysqld.exe'" | Where-Object {$_.CommandLine -like "*$OutputRoot*"} | ForEach-Object {Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue}
}

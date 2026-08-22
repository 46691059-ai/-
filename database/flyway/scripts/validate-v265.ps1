param(
    [string]$OutputRoot = "$env:LOCALAPPDATA\Temp\enterprise-v265-validation-$((Get-Date).ToString('yyyyMMdd-HHmmss'))"
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
foreach ($required in @($mysqld,$mysql,$mysqladmin,$flyway,$fingerprintSql)) {
    if (-not (Test-Path -LiteralPath $required)) { throw "Required validation asset missing: $required" }
}

$evidence = Join-Path $OutputRoot 'evidence'
$migrationSource = Join-Path $OutputRoot 'migrations'
$baselineSource = Join-Path $OutputRoot 'baseline'
New-Item -ItemType Directory -Path $evidence,$migrationSource,$baselineSource -Force | Out-Null
Get-ChildItem $repoMigrations -Filter 'V*.sql' -File | Copy-Item -Destination $migrationSource
foreach ($name in $baselineFiles) {
    Copy-Item (Join-Path $workspace "database\mysql\$name") (Join-Path $baselineSource $name)
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

function Invoke-Flyway($Server, [string]$Command, [string]$Target='') {
    $args = @("-url=jdbc:mysql://127.0.0.1:$($Server.Port)/enterprise_platform",'-user=root','-password=',
        "-locations=filesystem:$($migrationSource.Replace('\','/'))",'-baselineOnMigrate=true','-baselineVersion=2.0.0',
        '-validateMigrationNaming=true','-cleanDisabled=true','-connectRetries=10')
    if ($Target) { $args += "-target=$Target" }
    $args += $Command
    $log = Join-Path $evidence "$($Server.Name)-$Command$(if($Target){"-$Target"}).log"
    & $flyway @args 2>&1 | Tee-Object -FilePath $log
    if ($LASTEXITCODE -ne 0) { throw "Flyway $Command failed for $($Server.Name)" }
}

function Write-Fingerprint($Server) {
    $args = @('--protocol=TCP','--host=127.0.0.1',"--port=$($Server.Port)",'--user=root','--database=enterprise_platform','--batch','--raw','--skip-column-names')
    $rows = Get-Content $fingerprintSql -Raw | & $mysql @args
    if ($LASTEXITCODE -ne 0) { throw 'schema fingerprint query failed' }
    $fullFile = Join-Path $evidence "$($Server.Name)-schema.txt"
    $workflowFile = Join-Path $evidence "$($Server.Name)-workflow-schema.txt"
    $bindingFile = Join-Path $evidence "$($Server.Name)-binding-schema.txt"
    [IO.File]::WriteAllText($fullFile,(($rows -join "`n")+"`n"),[Text.UTF8Encoding]::new($false))
    $workflow = $rows | Where-Object { $_ -match '^\w+\|workflow_' }
    [IO.File]::WriteAllText($workflowFile,(($workflow -join "`n")+"`n"),[Text.UTF8Encoding]::new($false))
    $binding = $workflow | Where-Object { $_ -match 'workflow_(instance_resolver_binding|node_resolver_binding)' }
    [IO.File]::WriteAllText($bindingFile,(($binding -join "`n")+"`n"),[Text.UTF8Encoding]::new($false))
    return @{Full=(Get-FileHash $fullFile -Algorithm SHA256).Hash.ToLower();Workflow=(Get-FileHash $workflowFile -Algorithm SHA256).Hash.ToLower();Binding=(Get-FileHash $bindingFile -Algorithm SHA256).Hash.ToLower()}
}

function Expect-Rejected($Server, [string]$Name, [string]$Sql, [ref]$Log) {
    $result = Invoke-Mysql $Server $Sql
    $passed = $result.ExitCode -ne 0
    $Log.Value += "$Name`t$(if($passed){'PASS'}else{'FAIL'})`t$($result.Output.Replace("`n",' '))"
    if (-not $passed) { throw "negative test was accepted: $Name" }
}

function New-ConcurrentAggregateSql([long]$BaseId, [string]$Suffix, [int]$DelaySeconds) {
    $instance=$BaseId; $set=$BaseId+10; $resolver=$BaseId+11; $nodeA=$BaseId+21; $nodeB=$BaseId+22
    $execution=$BaseId+31; $task=$BaseId+41; $snapshot=$BaseId+51
    return @"
START TRANSACTION;
INSERT INTO workflow_instance(id,instance_no,definition_id,version_id,definition_code_snapshot,definition_version_no,definition_content_hash_snapshot,resolver_code,resolver_version,resolver_contract_hash,business_type,business_id,business_key,enterprise_id,attempt_no,initiator_user_id,initiator_org_id,current_node_id,engine_mode,content_hash_algorithm_snapshot,current_node_execution_id,status,idempotency_key,request_hash,event_sequence,trace_id,started_time,created_by,updated_by,deleted,delete_token,version)
VALUES($instance,'WFI-CONCURRENT-$Suffix',96500,96501,'V265_MULTI',1,REPEAT('1',64),'RESOLVER_A','A_V1',REPEAT('a',64),'ACCEPTANCE','CONCURRENT','ACCEPTANCE:CONCURRENT',965,1,700,965,96511,'MULTI_NODE_LINEAR_V1','GRAPH_V2_SHA256',NULL,'RUNNING','v265-concurrent',REPEAT('2',64),0,'v265-concurrent',CURRENT_TIMESTAMP(3),'acceptance','acceptance',0,0,0);
SELECT SLEEP($DelaySeconds);
INSERT INTO workflow_instance_resolver_binding_set(id,instance_id,definition_id,definition_version_id,manifest_version,manifest_hash,binding_count,binding_status,frozen_time,audit_info,created_by,updated_by,deleted,delete_token,version)
VALUES($set,$instance,96500,96501,'MULTI_RESOLVER_V1',REPEAT('4',64),2,'FROZEN',CURRENT_TIMESTAMP(3),'concurrent','acceptance','acceptance',0,0,0);
INSERT INTO workflow_instance_resolver_binding(id,binding_set_id,instance_id,definition_version_id,resolver_code,resolver_version,strategy_type,resolver_mode,contract_hash,rule_hash,binding_status,frozen_time,audit_info,created_by,updated_by,deleted,delete_token,version)
VALUES($resolver,$set,$instance,96501,'RESOLVER_A','A_V1','USER','DIRECT',REPEAT('a',64),REPEAT('c',64),'FROZEN',CURRENT_TIMESTAMP(3),'concurrent','acceptance','acceptance',0,0,0);
INSERT INTO workflow_node_resolver_binding_snapshot(id,binding_set_id,resolver_binding_id,instance_id,definition_version_id,node_id,node_code_snapshot,strategy_type,resolver_mode,target_type,target_value_snapshot,rule_version,rule_snapshot,rule_hash,node_binding_hash,binding_status,frozen_time,audit_info,created_by,updated_by,deleted,delete_token,version)
VALUES($nodeA,$set,$resolver,$instance,96501,96511,'NODE_A','USER','DIRECT','USER','{"userId":701}','ASSIGNMENT_RULE_V1','{"userId":701}',REPEAT('c',64),REPEAT('e',64),'FROZEN',CURRENT_TIMESTAMP(3),'concurrent','acceptance','acceptance',0,0,0),
      ($nodeB,$set,$resolver,$instance,96501,96512,'NODE_B','USER','DIRECT','USER','{"userId":702}','ASSIGNMENT_RULE_V1','{"userId":702}',REPEAT('d',64),REPEAT('f',64),'FROZEN',CURRENT_TIMESTAMP(3),'concurrent','acceptance','acceptance',0,0,0);
INSERT INTO workflow_node_execution(id,execution_no,instance_id,version_id,node_id,node_code_snapshot,node_name_snapshot,visit_no,status,entered_time,activated_time,trace_id,created_by,updated_by,deleted,delete_token,version)
VALUES($execution,'WFNE-CONCURRENT-$Suffix',$instance,96501,96511,'NODE_A','Node A',1,'ACTIVE',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3),'v265-concurrent','acceptance','acceptance',0,0,0);
UPDATE workflow_instance SET current_node_execution_id=$execution WHERE id=$instance;
INSERT INTO workflow_task(id,task_no,instance_id,version_id,node_id,node_execution_id,node_code_snapshot,node_name_snapshot,task_round,participant_key,assignee_user_id,candidate_snapshot,status,allowed_actions,created_by,updated_by,deleted,delete_token,version)
VALUES($task,'WFT-CONCURRENT-$Suffix',$instance,96501,96511,$execution,'NODE_A','Node A',1,'NODE_A:1',701,'{"userId":701}','PENDING','APPROVE,REJECT','acceptance','acceptance',0,0,0);
INSERT INTO workflow_task_assignment_snapshot(id,task_id,instance_id,version_id,node_id,node_execution_id,strategy_type,target_type,target_snapshot,resolved_users,resolved_user_count,resolve_time,audit_info,trace_id,created_by,updated_by,deleted,delete_token,version)
VALUES($snapshot,$task,$instance,96501,96511,$execution,'USER','USER','{"userId":701}','[701]',1,CURRENT_TIMESTAMP(3),'concurrent','v265-concurrent','acceptance','acceptance',0,0,0);
COMMIT;
"@
}

try {
    $fresh = New-IsolatedMysql 'fresh' 34865
    $upgrade = New-IsolatedMysql 'upgrade-v264' 34866
    Initialize-Baseline $fresh; Initialize-Baseline $upgrade

    Invoke-Flyway $fresh migrate '2.6.5'; Invoke-Flyway $fresh validate; Invoke-Flyway $fresh migrate '2.6.5'
    # V2.6.4 is only an Upgrade-path preparation point. Strict validation is
    # intentionally performed after V2.6.5 because the full scan root already
    # contains the pending candidate and Flyway 13 treats that interim state as invalid.
    Invoke-Flyway $upgrade migrate '2.6.4'
    Invoke-Flyway $upgrade migrate '2.6.5'; Invoke-Flyway $upgrade validate; Invoke-Flyway $upgrade migrate '2.6.5'

    $fixture = @"
START TRANSACTION;
INSERT INTO workflow_definition(id,definition_code,definition_name,business_type,enterprise_id,owner_org_id,status,current_version_id,created_by,updated_by,deleted,delete_token,version)
VALUES(96500,'V265_MULTI','V265 Multi Resolver','ACCEPTANCE',965,965,'DRAFT',NULL,'acceptance','acceptance',0,0,0);
INSERT INTO workflow_version(id,definition_id,version_no,status,schema_version,engine_mode,content_hash_algorithm,content_hash,effective_from,published_by,published_time,created_by,updated_by,deleted,delete_token,version)
VALUES(96501,96500,1,'PUBLISHED','2.0','MULTI_NODE_LINEAR_V1','GRAPH_V2_SHA256',REPEAT('1',64),CURRENT_TIMESTAMP(3),965,CURRENT_TIMESTAMP(3),'acceptance','acceptance',0,0,0);
INSERT INTO workflow_node(id,version_id,node_code,node_name,node_type,node_order,governance_node_type,approval_mode,assignment_rule_type,assignment_rule_config,withdraw_allowed,enabled,created_by,updated_by,deleted,delete_token,version)
VALUES(96511,96501,'NODE_A','Node A','APPROVAL',1,'GENERAL_APPROVAL','SINGLE','USER','{"userId":701}',0,1,'acceptance','acceptance',0,0,0),
      (96512,96501,'NODE_B','Node B','APPROVAL',2,'GENERAL_APPROVAL','SINGLE','USER','{"userId":702}',0,1,'acceptance','acceptance',0,0,0);
UPDATE workflow_definition SET status='ACTIVE',current_version_id=96501,version=1 WHERE id=96500;
INSERT INTO workflow_transition(id,version_id,transition_code,transition_name,from_node_id,to_node_id,trigger_type,route_type,priority,enabled,created_by,updated_by,deleted,delete_token,version)
VALUES(96515,96501,'A_TO_B','A to B',96511,96512,'APPROVE','DIRECT',1,1,'acceptance','acceptance',0,0,0);
INSERT INTO workflow_instance(id,instance_no,definition_id,version_id,definition_code_snapshot,definition_version_no,definition_content_hash_snapshot,resolver_code,resolver_version,resolver_contract_hash,business_type,business_id,business_key,enterprise_id,attempt_no,initiator_user_id,initiator_org_id,current_node_id,engine_mode,content_hash_algorithm_snapshot,current_node_execution_id,status,idempotency_key,request_hash,event_sequence,trace_id,started_time,created_by,updated_by,deleted,delete_token,version)
VALUES(96520,'WFI-V265',96500,96501,'V265_MULTI',1,REPEAT('1',64),'RESOLVER_A','A_V1',REPEAT('a',64),'ACCEPTANCE','96520','ACCEPTANCE:96520',965,1,700,965,96511,'MULTI_NODE_LINEAR_V1','GRAPH_V2_SHA256',NULL,'RUNNING','v265-start',REPEAT('2',64),0,'v265',CURRENT_TIMESTAMP(3),'acceptance','acceptance',0,0,0);
INSERT INTO workflow_instance_resolver_binding_set(id,instance_id,definition_id,definition_version_id,manifest_version,manifest_hash,binding_count,binding_status,frozen_time,audit_info,created_by,updated_by,deleted,delete_token,version)
VALUES(96530,96520,96500,96501,'MULTI_RESOLVER_V1',REPEAT('3',64),2,'FROZEN',CURRENT_TIMESTAMP(3),'acceptance','acceptance','acceptance',0,0,0);
INSERT INTO workflow_instance_resolver_binding(id,binding_set_id,instance_id,definition_version_id,resolver_code,resolver_version,strategy_type,resolver_mode,contract_hash,rule_hash,binding_status,frozen_time,audit_info,created_by,updated_by,deleted,delete_token,version)
VALUES(96531,96530,96520,96501,'RESOLVER_A','A_V1','USER','DIRECT',REPEAT('a',64),REPEAT('c',64),'FROZEN',CURRENT_TIMESTAMP(3),'A','acceptance','acceptance',0,0,0),
      (96532,96530,96520,96501,'RESOLVER_B','B_V1','USER','DIRECT',REPEAT('b',64),REPEAT('d',64),'FROZEN',CURRENT_TIMESTAMP(3),'B','acceptance','acceptance',0,0,0);
INSERT INTO workflow_node_resolver_binding_snapshot(id,binding_set_id,resolver_binding_id,instance_id,definition_version_id,node_id,node_code_snapshot,strategy_type,resolver_mode,target_type,target_value_snapshot,rule_version,rule_snapshot,rule_hash,node_binding_hash,binding_status,frozen_time,audit_info,created_by,updated_by,deleted,delete_token,version)
VALUES(96541,96530,96531,96520,96501,96511,'NODE_A','USER','DIRECT','USER','{"userId":701}','ASSIGNMENT_RULE_V1','{"userId":701}',REPEAT('c',64),REPEAT('e',64),'FROZEN',CURRENT_TIMESTAMP(3),'A','acceptance','acceptance',0,0,0),
      (96542,96530,96532,96520,96501,96512,'NODE_B','USER','DIRECT','USER','{"userId":702}','ASSIGNMENT_RULE_V1','{"userId":702}',REPEAT('d',64),REPEAT('f',64),'FROZEN',CURRENT_TIMESTAMP(3),'B','acceptance','acceptance',0,0,0);
INSERT INTO workflow_node_execution(id,execution_no,instance_id,version_id,node_id,node_code_snapshot,node_name_snapshot,visit_no,status,entered_time,activated_time,trace_id,created_by,updated_by,deleted,delete_token,version)
VALUES(96551,'WFNE-A',96520,96501,96511,'NODE_A','Node A',1,'ACTIVE',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3),'v265','acceptance','acceptance',0,0,0);
UPDATE workflow_instance SET current_node_execution_id=96551 WHERE id=96520;
INSERT INTO workflow_task(id,task_no,instance_id,version_id,node_id,node_execution_id,node_code_snapshot,node_name_snapshot,task_round,participant_key,assignee_user_id,candidate_snapshot,status,allowed_actions,created_by,updated_by,deleted,delete_token,version)
VALUES(96561,'WFT-A',96520,96501,96511,96551,'NODE_A','Node A',1,'NODE_A:1',701,'{"userId":701}','PENDING','APPROVE,REJECT','acceptance','acceptance',0,0,0);
INSERT INTO workflow_task_assignment_snapshot(id,task_id,instance_id,version_id,node_id,node_execution_id,strategy_type,target_type,target_snapshot,resolved_users,resolved_user_count,resolve_time,audit_info,trace_id,created_by,updated_by,deleted,delete_token,version)
VALUES(96571,96561,96520,96501,96511,96551,'USER','USER','{"userId":701}','[701]',1,CURRENT_TIMESTAMP(3),'resolver=A','v265','acceptance','acceptance',0,0,0);
COMMIT;
"@
    $fixtureFile = Join-Path $evidence 'multi-resolver-fixture.sql'
    [IO.File]::WriteAllText($fixtureFile,$fixture,[Text.UTF8Encoding]::new($false)); Invoke-MysqlFile $fresh $fixtureFile enterprise_platform

    $advance = @"
START TRANSACTION;
UPDATE workflow_task SET status='APPROVED',completed_by=701,completed_time=CURRENT_TIMESTAMP(3),decision_result='APPROVED',version=version+1 WHERE id=96561;
UPDATE workflow_node_execution SET status='COMPLETED',result='APPROVED',completed_by=701,completed_time=CURRENT_TIMESTAMP(3),version=version+1 WHERE id=96551;
INSERT INTO workflow_node_execution(id,execution_no,instance_id,version_id,node_id,node_code_snapshot,node_name_snapshot,visit_no,previous_execution_id,source_transition_id,status,entered_time,activated_time,trace_id,created_by,updated_by,deleted,delete_token,version)
VALUES(96552,'WFNE-B',96520,96501,96512,'NODE_B','Node B',1,96551,96515,'ACTIVE',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3),'v265','acceptance','acceptance',0,0,0);
INSERT INTO workflow_task(id,task_no,instance_id,version_id,node_id,node_execution_id,node_code_snapshot,node_name_snapshot,task_round,participant_key,assignee_user_id,candidate_snapshot,status,allowed_actions,created_by,updated_by,deleted,delete_token,version)
VALUES(96562,'WFT-B',96520,96501,96512,96552,'NODE_B','Node B',1,'NODE_B:1',702,'{"userId":702}','PENDING','APPROVE,REJECT','acceptance','acceptance',0,0,0);
INSERT INTO workflow_task_assignment_snapshot(id,task_id,instance_id,version_id,node_id,node_execution_id,strategy_type,target_type,target_snapshot,resolved_users,resolved_user_count,resolve_time,audit_info,trace_id,created_by,updated_by,deleted,delete_token,version)
VALUES(96572,96562,96520,96501,96512,96552,'USER','USER','{"userId":702}','[702]',1,CURRENT_TIMESTAMP(3),'resolver=B','v265','acceptance','acceptance',0,0,0);
UPDATE workflow_instance SET current_node_id=96512,current_node_execution_id=96552,event_sequence=event_sequence+1,version=version+1 WHERE id=96520;
COMMIT;
"@
    $advanceFile = Join-Path $evidence 'linear-advance.sql'
    [IO.File]::WriteAllText($advanceFile,$advance,[Text.UTF8Encoding]::new($false)); Invoke-MysqlFile $fresh $advanceFile enterprise_platform

    $chain = Invoke-Mysql $fresh "SELECT n.node_code_snapshot,r.resolver_code,t.assignee_user_id,e.status FROM workflow_node_resolver_binding_snapshot n JOIN workflow_instance_resolver_binding r ON r.id=n.resolver_binding_id JOIN workflow_node_execution e ON e.instance_id=n.instance_id AND e.node_id=n.node_id JOIN workflow_task t ON t.node_execution_id=e.id WHERE n.instance_id=96520 ORDER BY n.node_code_snapshot" -Raw
    $chain.Output | Set-Content (Join-Path $evidence 'runtime-chain.tsv') -Encoding utf8
    if ($chain.Output -notmatch 'NODE_A.*RESOLVER_A.*701.*COMPLETED' -or $chain.Output -notmatch 'NODE_B.*RESOLVER_B.*702.*ACTIVE') { throw 'multi-resolver runtime chain mismatch' }

    $structureSql = "SELECT table_name,COUNT(*) columns FROM information_schema.columns WHERE table_schema='enterprise_platform' AND table_name IN ('workflow_instance_resolver_binding_set','workflow_instance_resolver_binding','workflow_node_resolver_binding_snapshot') GROUP BY table_name ORDER BY table_name; SELECT table_name,constraint_type,COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema='enterprise_platform' AND table_name IN ('workflow_instance_resolver_binding_set','workflow_instance_resolver_binding','workflow_node_resolver_binding_snapshot') GROUP BY table_name,constraint_type ORDER BY table_name,constraint_type; SELECT table_name,constraint_name,enforced FROM information_schema.table_constraints WHERE constraint_schema='enterprise_platform' AND constraint_type='CHECK' AND table_name IN ('workflow_instance_resolver_binding_set','workflow_instance_resolver_binding','workflow_node_resolver_binding_snapshot') ORDER BY table_name,constraint_name;"
    (Invoke-Mysql $fresh $structureSql -Raw).Output | Set-Content (Join-Path $evidence 'binding-structure.tsv') -Encoding utf8

    $negative = @()
    Expect-Rejected $fresh 'fk_missing_instance' "INSERT INTO workflow_instance_resolver_binding_set(id,instance_id,definition_id,definition_version_id,manifest_version,manifest_hash,binding_count,binding_status,frozen_time,audit_info,deleted,delete_token,version) VALUES(96600,999999,96500,96501,'V1',REPEAT('a',64),1,'FROZEN',NOW(3),'x',0,0,0)" ([ref]$negative)
    Expect-Rejected $fresh 'fk_missing_set' "INSERT INTO workflow_instance_resolver_binding(id,binding_set_id,instance_id,definition_version_id,resolver_code,resolver_version,strategy_type,resolver_mode,contract_hash,rule_hash,binding_status,frozen_time,audit_info,deleted,delete_token,version) VALUES(96601,999999,96520,96501,'X','V1','USER','DIRECT',REPEAT('a',64),REPEAT('b',64),'FROZEN',NOW(3),'x',0,0,0)" ([ref]$negative)
    Expect-Rejected $fresh 'fk_missing_resolver' "INSERT INTO workflow_node_resolver_binding_snapshot(id,binding_set_id,resolver_binding_id,instance_id,definition_version_id,node_id,node_code_snapshot,strategy_type,resolver_mode,target_type,target_value_snapshot,rule_version,rule_snapshot,rule_hash,node_binding_hash,binding_status,frozen_time,audit_info,deleted,delete_token,version) VALUES(96602,96530,999999,96520,96501,96511,'A','USER','DIRECT','USER','x','V1','x',REPEAT('a',64),REPEAT('b',64),'FROZEN',NOW(3),'x',0,0,0)" ([ref]$negative)
    Expect-Rejected $fresh 'fk_missing_node' "INSERT INTO workflow_node_resolver_binding_snapshot(id,binding_set_id,resolver_binding_id,instance_id,definition_version_id,node_id,node_code_snapshot,strategy_type,resolver_mode,target_type,target_value_snapshot,rule_version,rule_snapshot,rule_hash,node_binding_hash,binding_status,frozen_time,audit_info,deleted,delete_token,version) VALUES(96603,96530,96531,96520,96501,999999,'X','USER','DIRECT','USER','x','V1','x',REPEAT('a',64),REPEAT('b',64),'FROZEN',NOW(3),'x',0,0,0)" ([ref]$negative)
    Expect-Rejected $fresh 'unique_binding_set' "INSERT INTO workflow_instance_resolver_binding_set(id,instance_id,definition_id,definition_version_id,manifest_version,manifest_hash,binding_count,binding_status,frozen_time,audit_info,deleted,delete_token,version) VALUES(96604,96520,96500,96501,'V1',REPEAT('a',64),1,'FROZEN',NOW(3),'x',0,0,0)" ([ref]$negative)
    Expect-Rejected $fresh 'unique_resolver' "INSERT INTO workflow_instance_resolver_binding(id,binding_set_id,instance_id,definition_version_id,resolver_code,resolver_version,strategy_type,resolver_mode,contract_hash,rule_hash,binding_status,frozen_time,audit_info,deleted,delete_token,version) VALUES(96605,96530,96520,96501,'RESOLVER_A','A_V1','USER','DIRECT',REPEAT('a',64),REPEAT('b',64),'FROZEN',NOW(3),'x',0,0,0)" ([ref]$negative)
    Expect-Rejected $fresh 'unique_node' "INSERT INTO workflow_node_resolver_binding_snapshot(id,binding_set_id,resolver_binding_id,instance_id,definition_version_id,node_id,node_code_snapshot,strategy_type,resolver_mode,target_type,target_value_snapshot,rule_version,rule_snapshot,rule_hash,node_binding_hash,binding_status,frozen_time,audit_info,deleted,delete_token,version) VALUES(96606,96530,96531,96520,96501,96511,'A2','USER','DIRECT','USER','x','V1','x',REPEAT('a',64),REPEAT('b',64),'FROZEN',NOW(3),'x',0,0,0)" ([ref]$negative)
    foreach($case in @(
        @{n='status';s="UPDATE workflow_instance_resolver_binding_set SET binding_status='INVALID' WHERE id=96530"},
        @{n='strategy';s="UPDATE workflow_instance_resolver_binding SET strategy_type='INVALID' WHERE id=96531"},
        @{n='mode';s="UPDATE workflow_instance_resolver_binding SET resolver_mode='CANDIDATE_POOL' WHERE id=96531"},
        @{n='contract_hash';s="UPDATE workflow_instance_resolver_binding SET contract_hash='ABC' WHERE id=96531"},
        @{n='manifest_hash';s="UPDATE workflow_instance_resolver_binding_set SET manifest_hash=REPEAT('A',64) WHERE id=96530"},
        @{n='delete_token';s="UPDATE workflow_instance_resolver_binding_set SET delete_token=1 WHERE id=96530"},
        @{n='negative_version';s="UPDATE workflow_instance_resolver_binding_set SET version=-1 WHERE id=96530"}
    )) { Expect-Rejected $fresh "check_$($case.n)" $case.s ([ref]$negative) }
    $negative | Set-Content (Join-Path $evidence 'negative-tests.tsv') -Encoding utf8

    $concurrentA = Join-Path $evidence 'concurrent-a.sql'; $concurrentB = Join-Path $evidence 'concurrent-b.sql'
    [IO.File]::WriteAllText($concurrentA,(New-ConcurrentAggregateSql 96820 'A' 2),[Text.UTF8Encoding]::new($false))
    [IO.File]::WriteAllText($concurrentB,(New-ConcurrentAggregateSql 96920 'B' 0),[Text.UTF8Encoding]::new($false))
    $commonArgs = @('--protocol=TCP','--host=127.0.0.1',"--port=$($fresh.Port)",'--user=root','--database=enterprise_platform')
    $argsA = ($commonArgs -join ' ') + " --execute=`"source $($concurrentA.Replace('\','/'))`""
    $argsB = ($commonArgs -join ' ') + " --execute=`"source $($concurrentB.Replace('\','/'))`""
    $p1 = Start-Process $mysql -ArgumentList $argsA -WindowStyle Hidden -PassThru -Wait:$false `
        -RedirectStandardError (Join-Path $evidence 'concurrent-a.err')
    Start-Sleep -Milliseconds 200
    $p2 = Start-Process $mysql -ArgumentList $argsB -WindowStyle Hidden -PassThru -Wait:$false `
        -RedirectStandardError (Join-Path $evidence 'concurrent-b.err')
    $p1.WaitForExit(); $p2.WaitForExit()
    $errorA = Get-Content (Join-Path $evidence 'concurrent-a.err') -Raw
    $errorB = Get-Content (Join-Path $evidence 'concurrent-b.err') -Raw
    $singleDuplicate = ([string]::IsNullOrWhiteSpace($errorA) -and $errorB -match 'ERROR 1062') -or
        ([string]::IsNullOrWhiteSpace($errorB) -and $errorA -match 'ERROR 1062')
    $winnerCount = [int](Invoke-Mysql $fresh "SELECT COUNT(*) FROM workflow_instance WHERE enterprise_id=965 AND idempotency_key='v265-concurrent' AND deleted=0" -Raw).Output.Trim()
    $aggregateCounts = (Invoke-Mysql $fresh "SELECT (SELECT COUNT(*) FROM workflow_instance_resolver_binding_set s JOIN workflow_instance i ON i.id=s.instance_id WHERE i.idempotency_key='v265-concurrent'),(SELECT COUNT(*) FROM workflow_instance_resolver_binding r JOIN workflow_instance i ON i.id=r.instance_id WHERE i.idempotency_key='v265-concurrent'),(SELECT COUNT(*) FROM workflow_node_resolver_binding_snapshot n JOIN workflow_instance i ON i.id=n.instance_id WHERE i.idempotency_key='v265-concurrent'),(SELECT COUNT(*) FROM workflow_node_execution e JOIN workflow_instance i ON i.id=e.instance_id WHERE i.idempotency_key='v265-concurrent'),(SELECT COUNT(*) FROM workflow_task t JOIN workflow_instance i ON i.id=t.instance_id WHERE i.idempotency_key='v265-concurrent'),(SELECT COUNT(*) FROM workflow_task_assignment_snapshot a JOIN workflow_instance i ON i.id=a.instance_id WHERE i.idempotency_key='v265-concurrent')" -Raw).Output.Trim()
    "$(if([string]::IsNullOrWhiteSpace($errorA)){'SUCCESS'}else{'REJECTED'})`t$(if([string]::IsNullOrWhiteSpace($errorB)){'SUCCESS'}else{'REJECTED'})`t$winnerCount`t$aggregateCounts" | Set-Content (Join-Path $evidence 'concurrency-result.tsv') -Encoding utf8
    if ($winnerCount -ne 1 -or -not $singleDuplicate -or $aggregateCounts -ne "1`t1`t2`t1`t1`t1") {
        throw "concurrent aggregate single-winner validation failed: $winnerCount,$aggregateCounts"
    }

    $rollbackSql = "START TRANSACTION; INSERT INTO workflow_instance(id,instance_no,definition_id,version_id,definition_code_snapshot,definition_version_no,definition_content_hash_snapshot,resolver_code,resolver_version,resolver_contract_hash,business_type,business_id,business_key,enterprise_id,attempt_no,initiator_user_id,initiator_org_id,current_node_id,engine_mode,content_hash_algorithm_snapshot,current_node_execution_id,status,idempotency_key,request_hash,event_sequence,started_time,deleted,delete_token,version) VALUES(96720,'WFI-ROLLBACK',96500,96501,'V265_MULTI',1,REPEAT('1',64),'R','V1',REPEAT('a',64),'ACCEPTANCE','96720','A:96720',965,1,700,965,96511,'MULTI_NODE_LINEAR_V1','GRAPH_V2_SHA256',NULL,'RUNNING','rollback',REPEAT('2',64),0,NOW(3),0,0,0); INSERT INTO workflow_instance_resolver_binding_set(id,instance_id,definition_id,definition_version_id,manifest_version,manifest_hash,binding_count,binding_status,frozen_time,audit_info,deleted,delete_token,version) VALUES(96730,96720,96500,96501,'V1','INVALID',1,'FROZEN',NOW(3),'x',0,0,0); COMMIT;"
    $rollback = Invoke-Mysql $fresh $rollbackSql
    if ($rollback.ExitCode -eq 0 -or [int](Invoke-Mysql $fresh "SELECT COUNT(*) FROM workflow_instance WHERE id=96720" -Raw).Output.Trim() -ne 0) { throw 'transaction rollback test failed' }

    $freshFingerprint = Write-Fingerprint $fresh; $upgradeFingerprint = Write-Fingerprint $upgrade
    if ($freshFingerprint.Full -ne $upgradeFingerprint.Full -or $freshFingerprint.Workflow -ne $upgradeFingerprint.Workflow -or $freshFingerprint.Binding -ne $upgradeFingerprint.Binding) { throw 'Fresh and Upgrade schema fingerprints differ' }
    $checksumFresh = (Invoke-Mysql $fresh "SELECT checksum FROM flyway_schema_history WHERE version='2.6.5' AND success=1" -Raw).Output.Trim()
    $checksumUpgrade = (Invoke-Mysql $upgrade "SELECT checksum FROM flyway_schema_history WHERE version='2.6.5' AND success=1" -Raw).Output.Trim()
    if ($checksumFresh -ne $checksumUpgrade) { throw 'V2.6.5 Flyway checksums differ' }
    $historyFailure = [int](Invoke-Mysql $fresh "SELECT COUNT(*) FROM flyway_schema_history WHERE success=0" -Raw).Output.Trim() + [int](Invoke-Mysql $upgrade "SELECT COUNT(*) FROM flyway_schema_history WHERE success=0" -Raw).Output.Trim()
    $summary = [ordered]@{
        output_root=$OutputRoot; mysql_version=((& $mysqld --version)-join ' '); flyway_version='13.0.0';
        v265_sha256=(Get-FileHash (Join-Path $repoMigrations 'V2.6.5__create_workflow_multi_resolver_binding.sql') -Algorithm SHA256).Hash.ToLower();
        v265_flyway_checksum=$checksumFresh; fresh_history_success=[int](Invoke-Mysql $fresh "SELECT COUNT(*) FROM flyway_schema_history WHERE success=1" -Raw).Output.Trim();
        upgrade_history_success=[int](Invoke-Mysql $upgrade "SELECT COUNT(*) FROM flyway_schema_history WHERE success=1" -Raw).Output.Trim(); failed_history=$historyFailure;
        full_schema_fingerprint=$freshFingerprint.Full; workflow_schema_fingerprint=$freshFingerprint.Workflow; binding_schema_fingerprint=$freshFingerprint.Binding;
        binding_sets=[int](Invoke-Mysql $fresh "SELECT COUNT(*) FROM workflow_instance_resolver_binding_set WHERE instance_id=96520 AND deleted=0" -Raw).Output.Trim();
        resolver_bindings=[int](Invoke-Mysql $fresh "SELECT COUNT(*) FROM workflow_instance_resolver_binding WHERE instance_id=96520 AND deleted=0" -Raw).Output.Trim();
        node_bindings=[int](Invoke-Mysql $fresh "SELECT COUNT(*) FROM workflow_node_resolver_binding_snapshot WHERE instance_id=96520 AND deleted=0" -Raw).Output.Trim();
        negative_tests=$negative.Count; rollback='PASS'; concurrency='TWO_SESSION_SINGLE_WINNER_FULL_AGGREGATE_PASS'; result='PASS'
    }
    $summary | ConvertTo-Json | Set-Content (Join-Path $evidence 'summary.json') -Encoding utf8
    $summary | ConvertTo-Json
}
finally {
    foreach ($process in $serverProcesses) { if ($process -and -not $process.HasExited) { Stop-Process -Id $process.Id -Force } }
    Get-CimInstance Win32_Process -Filter "Name='mysqld.exe'" | Where-Object { $_.CommandLine -like "*$OutputRoot*" } |
        ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
}

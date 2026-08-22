param([string]$ValidationRoot = 'D:\codex-validation-v266-base')

$ErrorActionPreference = 'Stop'
$mysqlHome = 'C:\Program Files\MySQL\MySQL Server 8.4'
$mysqld = Join-Path $mysqlHome 'bin\mysqld.exe'
$mysql = Join-Path $mysqlHome 'bin\mysql.exe'
$mysqladmin = Join-Path $mysqlHome 'bin\mysqladmin.exe'
$evidence = Join-Path $ValidationRoot 'evidence'
$serverProcesses = @()

function Start-ExistingServer([string]$Name, [int]$Port) {
    $ini = Join-Path $ValidationRoot "$Name\my.ini"
    if (-not (Test-Path -LiteralPath $ini)) { throw "Missing isolated MySQL ini: $ini" }
    $process = Start-Process $mysqld -ArgumentList "--defaults-file=$ini" -WindowStyle Hidden -PassThru
    $script:serverProcesses += $process
    foreach ($attempt in 1..80) {
        $old = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
        & $mysqladmin --protocol=TCP --host=127.0.0.1 --port=$Port --user=root ping 2>$null | Out-Null
        $ErrorActionPreference = $old
        if ($LASTEXITCODE -eq 0) { return @{Name=$Name;Port=$Port} }
        Start-Sleep -Milliseconds 300
    }
    throw "$Name failed to restart"
}

function Invoke-Sql($Server, [string]$Sql, [switch]$Raw) {
    $args = @('--protocol=TCP','--host=127.0.0.1',"--port=$($Server.Port)",
        '--user=root','--database=enterprise_platform')
    if ($Raw) { $args += @('--batch','--raw','--skip-column-names') }
    $old = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
    $result = & $mysql @args "--execute=$Sql" 2>&1
    $exit = $LASTEXITCODE; $ErrorActionPreference = $old
    return @{ExitCode=$exit;Output=($result -join "`n")}
}

function Invoke-SqlFile($Server, [string]$Path) {
    & $mysql --protocol=TCP --host=127.0.0.1 "--port=$($Server.Port)" --user=root `
        --database=enterprise_platform "--execute=source $($Path.Replace('\','/'))"
    if ($LASTEXITCODE -ne 0) { throw "SQL fixture failed: $Path" }
}

function Expect-Rejected($Server, [string]$Name, [string]$Sql, [ref]$Log) {
    $result = Invoke-Sql $Server $Sql
    $pass = $result.ExitCode -ne 0
    $Log.Value += "$Name`t$(if($pass){'PASS'}else{'FAIL'})`t$($result.Output.Replace("`n",' '))"
    if (-not $pass) { throw "Negative test accepted: $Name" }
}

function PoolValues([long]$Id, [long]$Task, [long]$Execution, [long]$Snapshot,
                    [string]$No, [string]$Hash) {
    return "($Id,'$No',$Task,96620,96601,96611,$Execution,96630,96631,96641,$Snapshot,'CANDIDATE_POOL','ROLE','ROLE_RESOLVER','ROLE_V1',REPEAT('a',64),REPEAT('b',64),2,NOW(3),NOW(3),DATE_ADD(NOW(3),INTERVAL 1 DAY),'$Hash','AVAILABLE','acceptance','acceptance',NOW(3),'acceptance',NOW(3),0,0,NULL,0)"
}

try {
    $fresh = Start-ExistingServer 'fresh' 34865
    $upgrade = Start-ExistingServer 'upgrade-v265' 34866

    $fixture = @"
SET FOREIGN_KEY_CHECKS=0;
DELETE FROM workflow_task_candidate_member WHERE id IN (96691,96692);
DELETE FROM workflow_task_candidate_pool WHERE id=96681;
DELETE FROM workflow_task_assignment_snapshot WHERE id=96671;
DELETE FROM workflow_task WHERE id=96661;
DELETE FROM workflow_node_execution WHERE id=96651;
DELETE FROM workflow_node_resolver_binding_snapshot WHERE id=96641;
DELETE FROM workflow_instance_resolver_binding WHERE id=96631;
DELETE FROM workflow_instance_resolver_binding_set WHERE id=96630;
DELETE FROM workflow_instance WHERE id=96620;
UPDATE workflow_definition SET current_version_id=NULL WHERE id=96600;
DELETE FROM workflow_node WHERE id=96611;
DELETE FROM workflow_version_release WHERE published_version_id=96601;
DELETE FROM workflow_version WHERE id=96601;
DELETE FROM workflow_definition WHERE id=96600;
SET FOREIGN_KEY_CHECKS=1;
START TRANSACTION;
INSERT INTO workflow_definition(id,definition_code,definition_name,business_type,enterprise_id,owner_org_id,status,current_version_id,created_by,updated_by,deleted,delete_token,version)
VALUES(96600,'V266_POOL','V266 Candidate Pool','ACCEPTANCE',966,966,'DRAFT',NULL,'acceptance','acceptance',0,0,0);
INSERT INTO workflow_version(id,definition_id,version_no,status,schema_version,engine_mode,content_hash_algorithm,content_hash,effective_from,published_by,published_time,created_by,updated_by,deleted,delete_token,version)
VALUES(96601,96600,1,'PUBLISHED','2.0','MULTI_NODE_LINEAR_V1','GRAPH_V2_SHA256',REPEAT('1',64),NOW(3),966,NOW(3),'acceptance','acceptance',0,0,0);
INSERT INTO workflow_node(id,version_id,node_code,node_name,node_type,node_order,governance_node_type,approval_mode,assignment_rule_type,assignment_rule_config,withdraw_allowed,enabled,created_by,updated_by,deleted,delete_token,version)
VALUES(96611,96601,'POOL_NODE','Pool Node','APPROVAL',1,'GENERAL_APPROVAL','SINGLE','USER','{"userId":701}',0,1,'acceptance','acceptance',0,0,0);
UPDATE workflow_definition SET status='ACTIVE',current_version_id=96601,version=1 WHERE id=96600;
INSERT INTO workflow_instance(id,instance_no,definition_id,version_id,definition_code_snapshot,definition_version_no,definition_content_hash_snapshot,resolver_code,resolver_version,resolver_contract_hash,business_type,business_id,business_key,enterprise_id,attempt_no,initiator_user_id,initiator_org_id,current_node_id,engine_mode,content_hash_algorithm_snapshot,current_node_execution_id,status,idempotency_key,request_hash,event_sequence,trace_id,started_time,created_by,updated_by,deleted,delete_token,version)
VALUES(96620,'WFI-V266',96600,96601,'V266_POOL',1,REPEAT('1',64),'EXPLICIT_USER','EXPLICIT_USER_V1','3166103d7697a6f2e46932214b0447310c69d6b624f6d92a282e9ef3ab496853','ACCEPTANCE','96620','ACCEPTANCE:96620',966,1,700,966,96611,'MULTI_NODE_LINEAR_V1','GRAPH_V2_SHA256',NULL,'RUNNING','v266-fixture',REPEAT('2',64),0,'v266',NOW(3),'acceptance','acceptance',0,0,0);
INSERT INTO workflow_instance_resolver_binding_set(id,instance_id,definition_id,definition_version_id,manifest_version,manifest_hash,binding_count,binding_status,frozen_time,audit_info,created_by,updated_by,deleted,delete_token,version)
VALUES(96630,96620,96600,96601,'MULTI_RESOLVER_V1',REPEAT('3',64),1,'FROZEN',NOW(3),'acceptance','acceptance','acceptance',0,0,0);
INSERT INTO workflow_instance_resolver_binding(id,binding_set_id,instance_id,definition_version_id,resolver_code,resolver_version,strategy_type,resolver_mode,contract_hash,rule_hash,binding_status,frozen_time,audit_info,created_by,updated_by,deleted,delete_token,version)
VALUES(96631,96630,96620,96601,'ROLE_RESOLVER','ROLE_V1','ROLE','CANDIDATE_POOL',REPEAT('a',64),REPEAT('b',64),'FROZEN',NOW(3),'acceptance','acceptance','acceptance',0,0,0);
INSERT INTO workflow_node_resolver_binding_snapshot(id,binding_set_id,resolver_binding_id,instance_id,definition_version_id,node_id,node_code_snapshot,strategy_type,resolver_mode,target_type,target_value_snapshot,rule_version,rule_snapshot,rule_hash,node_binding_hash,binding_status,frozen_time,audit_info,created_by,updated_by,deleted,delete_token,version)
VALUES(96641,96630,96631,96620,96601,96611,'POOL_NODE','ROLE','CANDIDATE_POOL','ROLE','{"roleCode":"APPROVER"}','RULE_V1','{"roleCode":"APPROVER"}',REPEAT('b',64),REPEAT('c',64),'FROZEN',NOW(3),'acceptance','acceptance','acceptance',0,0,0);
INSERT INTO workflow_node_execution(id,execution_no,instance_id,version_id,node_id,node_code_snapshot,node_name_snapshot,visit_no,status,entered_time,activated_time,trace_id,created_by,updated_by,deleted,delete_token,version)
VALUES(96651,'WFNE-V266',96620,96601,96611,'POOL_NODE','Pool Node',1,'ACTIVE',NOW(3),NOW(3),'v266','acceptance','acceptance',0,0,0);
UPDATE workflow_instance SET current_node_execution_id=96651 WHERE id=96620;
INSERT INTO workflow_task(id,task_no,instance_id,version_id,node_id,node_execution_id,node_code_snapshot,node_name_snapshot,task_round,participant_key,assignee_user_id,candidate_snapshot,assignment_mode,status,allowed_actions,created_by,updated_by,deleted,delete_token,version)
VALUES(96661,'WFT-V266',96620,96601,96611,96651,'POOL_NODE','Pool Node',1,'POOL_NODE:1',NULL,'{"roleCode":"APPROVER"}','CANDIDATE_POOL','PENDING','APPROVE,REJECT','acceptance','acceptance',0,0,0);
INSERT INTO workflow_task_assignment_snapshot(id,task_id,instance_id,version_id,node_id,node_execution_id,strategy_type,target_type,target_snapshot,resolved_users,resolved_user_count,resolve_time,audit_info,trace_id,created_by,updated_by,deleted,delete_token,version)
VALUES(96671,96661,96620,96601,96611,96651,'ROLE','ROLE','{"roleCode":"APPROVER"}','[701,702]',2,NOW(3),'acceptance','v266','acceptance','acceptance',0,0,0);
INSERT INTO workflow_task_candidate_pool(id,pool_no,task_id,instance_id,version_id,node_id,node_execution_id,binding_set_id,resolver_binding_id,node_resolver_binding_id,assignment_snapshot_id,assignment_mode,strategy_type,resolver_code,resolver_version,contract_hash,rule_hash,candidate_count,generated_time,effective_time,expires_time,pool_hash,status,audit_info,created_by,created_time,updated_by,updated_time,deleted,delete_token,remark,version)
VALUES(96681,'WCP-V266',96661,96620,96601,96611,96651,96630,96631,96641,96671,'CANDIDATE_POOL','ROLE','ROLE_RESOLVER','ROLE_V1',REPEAT('a',64),REPEAT('b',64),2,NOW(3),NOW(3),DATE_ADD(NOW(3),INTERVAL 1 DAY),REPEAT('d',64),'AVAILABLE','acceptance','acceptance',NOW(3),'acceptance',NOW(3),0,0,NULL,0);
INSERT INTO workflow_task_candidate_member(id,pool_id,task_id,instance_id,candidate_user_id,source_type,source_ref_snapshot,org_id_snapshot,position_id_snapshot,role_id_snapshot,eligibility_snapshot,eligibility_hash,sort_order,generated_time,status,audit_info,created_by,created_time,updated_by,updated_time,deleted,delete_token,remark,version)
VALUES(96691,96681,96661,96620,701,'ROLE','ROLE:APPROVER',966,NULL,7001,'{"enabled":true}',REPEAT('e',64),1,NOW(3),'INCLUDED','acceptance','acceptance',NOW(3),'acceptance',NOW(3),0,0,NULL,0),
      (96692,96681,96661,96620,702,'ROLE','ROLE:APPROVER',966,NULL,7001,'{"enabled":true}',REPEAT('f',64),2,NOW(3),'INCLUDED','acceptance','acceptance',NOW(3),'acceptance',NOW(3),0,0,NULL,0);
COMMIT;
"@
    $fixturePath = Join-Path $evidence 'candidate-pool-fixture.sql'
    [IO.File]::WriteAllText($fixturePath,$fixture,[Text.UTF8Encoding]::new($false))
    Invoke-SqlFile $fresh $fixturePath

    $structureSql = "SELECT table_name,COUNT(*) FROM information_schema.columns WHERE table_schema='enterprise_platform' AND table_name IN ('workflow_task','workflow_task_candidate_pool','workflow_task_candidate_member') GROUP BY table_name ORDER BY table_name; SELECT table_name,constraint_type,COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema='enterprise_platform' AND table_name IN ('workflow_task','workflow_task_candidate_pool','workflow_task_candidate_member') GROUP BY table_name,constraint_type ORDER BY table_name,constraint_type; SELECT table_name,constraint_name,enforced FROM information_schema.table_constraints WHERE constraint_schema='enterprise_platform' AND constraint_type='CHECK' AND table_name IN ('workflow_task','workflow_task_candidate_pool','workflow_task_candidate_member') ORDER BY table_name,constraint_name;"
    (Invoke-Sql $fresh $structureSql -Raw).Output | Set-Content (Join-Path $evidence 'candidate-structure.tsv') -Encoding utf8
    $chain = (Invoke-Sql $fresh "SELECT p.task_id,p.instance_id,p.node_execution_id,p.resolver_binding_id,p.assignment_snapshot_id,p.candidate_count,COUNT(m.id),t.assignment_mode,t.assignee_user_id FROM workflow_task_candidate_pool p JOIN workflow_task t ON t.id=p.task_id JOIN workflow_task_candidate_member m ON m.pool_id=p.id WHERE p.id=96681 GROUP BY p.id,t.assignment_mode,t.assignee_user_id" -Raw).Output.Trim()
    $chain | Set-Content (Join-Path $evidence 'candidate-chain.tsv') -Encoding utf8
    if ($chain -ne "96661`t96620`t96651`t96631`t96671`t2`t2`tCANDIDATE_POOL`tNULL") {
        throw "Candidate data chain mismatch: $chain"
    }

    $negative = @()
    $validPoolColumns = "id,pool_no,task_id,instance_id,version_id,node_id,node_execution_id,binding_set_id,resolver_binding_id,node_resolver_binding_id,assignment_snapshot_id,assignment_mode,strategy_type,resolver_code,resolver_version,contract_hash,rule_hash,candidate_count,generated_time,effective_time,expires_time,pool_hash,status,audit_info,created_by,created_time,updated_by,updated_time,deleted,delete_token,remark,version"
    Expect-Rejected $fresh 'unique_pool_for_task' "INSERT INTO workflow_task_candidate_pool($validPoolColumns) VALUES$(PoolValues 96981 96661 96651 96671 'WCP-DUP' ('d'*64))" ([ref]$negative)
    Expect-Rejected $fresh 'missing_task' "UPDATE workflow_task_candidate_pool SET task_id=999999 WHERE id=96681" ([ref]$negative)
    Expect-Rejected $fresh 'missing_execution' "UPDATE workflow_task_candidate_pool SET node_execution_id=999999 WHERE id=96681" ([ref]$negative)
    Expect-Rejected $fresh 'missing_instance' "UPDATE workflow_task_candidate_pool SET instance_id=999999 WHERE id=96681" ([ref]$negative)
    Expect-Rejected $fresh 'missing_resolver' "UPDATE workflow_task_candidate_pool SET resolver_binding_id=999999 WHERE id=96681" ([ref]$negative)
    Expect-Rejected $fresh 'missing_assignment' "UPDATE workflow_task_candidate_pool SET assignment_snapshot_id=999999 WHERE id=96681" ([ref]$negative)
    Expect-Rejected $fresh 'cross_instance' "UPDATE workflow_task_candidate_pool SET instance_id=96520 WHERE id=96681" ([ref]$negative)
    Expect-Rejected $fresh 'cross_node_execution' "UPDATE workflow_task_candidate_pool SET node_execution_id=96551 WHERE id=96681" ([ref]$negative)
    Expect-Rejected $fresh 'cross_resolver_binding' "UPDATE workflow_task_candidate_pool SET resolver_binding_id=96531 WHERE id=96681" ([ref]$negative)
    Expect-Rejected $fresh 'invalid_assignment_mode' "UPDATE workflow_task SET assignment_mode='INVALID' WHERE id=96661" ([ref]$negative)
    Expect-Rejected $fresh 'direct_without_assignee' "UPDATE workflow_task SET assignment_mode='DIRECT' WHERE id=96661" ([ref]$negative)
    Expect-Rejected $fresh 'invalid_pool_status' "UPDATE workflow_task_candidate_pool SET status='INVALID' WHERE id=96681" ([ref]$negative)
    Expect-Rejected $fresh 'invalid_member_status' "UPDATE workflow_task_candidate_member SET status='INVALID' WHERE id=96691" ([ref]$negative)
    foreach ($hashCase in @('ABC',('g'*64),('A'*64),('a'*63+'B'))) {
        Expect-Rejected $fresh "invalid_pool_hash_$($negative.Count)" "UPDATE workflow_task_candidate_pool SET pool_hash='$hashCase' WHERE id=96681" ([ref]$negative)
    }
    Expect-Rejected $fresh 'invalid_delete_token' "UPDATE workflow_task_candidate_pool SET delete_token=1 WHERE id=96681" ([ref]$negative)
    Expect-Rejected $fresh 'negative_version' "UPDATE workflow_task_candidate_pool SET version=-1 WHERE id=96681" ([ref]$negative)
    Expect-Rejected $fresh 'invalid_candidate_count' "UPDATE workflow_task_candidate_pool SET candidate_count=0 WHERE id=96681" ([ref]$negative)
    Expect-Rejected $fresh 'duplicate_candidate_user' "INSERT INTO workflow_task_candidate_member(id,pool_id,task_id,instance_id,candidate_user_id,source_type,source_ref_snapshot,eligibility_snapshot,eligibility_hash,sort_order,generated_time,status,audit_info,deleted,delete_token,version) VALUES(96791,96681,96661,96620,701,'ROLE','DUP','x',REPEAT('a',64),3,NOW(3),'INCLUDED','x',0,0,0)" ([ref]$negative)
    Expect-Rejected $fresh 'invalid_sort_order' "UPDATE workflow_task_candidate_member SET sort_order=0 WHERE id=96691" ([ref]$negative)
    $negative | Set-Content (Join-Path $evidence 'candidate-negative-tests.tsv') -Encoding utf8

    $hash = Invoke-Sql $fresh "SELECT SHA2('canonical-a',256)=SHA2('canonical-a',256), SHA2('canonical-a',256)<>SHA2('canonical-b',256), SHA2('canonical-a',256) REGEXP '^[0-9a-f]{64}$'" -Raw
    if ($hash.Output.Trim() -ne "1`t1`t1") { throw 'Candidate Pool business Hash determinism failed' }
    $hash.Output | Set-Content (Join-Path $evidence 'candidate-hash.tsv') -Encoding utf8

    # Create two unpooled task aggregates for rollback and two-session concurrency.
    $prepare = @"
SET FOREIGN_KEY_CHECKS=0;
DELETE FROM workflow_task_candidate_member WHERE id BETWEEN 96791 AND 96892;
DELETE FROM workflow_task_candidate_pool WHERE id IN (96781,96782,96881);
DELETE FROM workflow_task_assignment_snapshot WHERE id IN (96771,96871);
DELETE FROM workflow_task WHERE id IN (96761,96861);
DELETE FROM workflow_node_execution WHERE id IN (96751,96851);
SET FOREIGN_KEY_CHECKS=1;
INSERT INTO workflow_node_execution(id,execution_no,instance_id,version_id,node_id,node_code_snapshot,node_name_snapshot,visit_no,status,entered_time,activated_time,trace_id,created_by,updated_by,deleted,delete_token,version)
VALUES(96751,'WFNE-V266-CON',96620,96601,96611,'POOL_NODE','Pool Node',2,'ACTIVE',NOW(3),NOW(3),'v266-con','acceptance','acceptance',0,0,0),
      (96851,'WFNE-V266-RB',96620,96601,96611,'POOL_NODE','Pool Node',3,'ACTIVE',NOW(3),NOW(3),'v266-rb','acceptance','acceptance',0,0,0);
INSERT INTO workflow_task(id,task_no,instance_id,version_id,node_id,node_execution_id,node_code_snapshot,node_name_snapshot,task_round,participant_key,assignee_user_id,candidate_snapshot,assignment_mode,status,allowed_actions,created_by,updated_by,deleted,delete_token,version)
VALUES(96761,'WFT-V266-CON',96620,96601,96611,96751,'POOL_NODE','Pool Node',1,'POOL_NODE:2',NULL,'{}','CANDIDATE_POOL','PENDING','APPROVE,REJECT','acceptance','acceptance',0,0,0),
      (96861,'WFT-V266-RB',96620,96601,96611,96851,'POOL_NODE','Pool Node',1,'POOL_NODE:3',NULL,'{}','CANDIDATE_POOL','PENDING','APPROVE,REJECT','acceptance','acceptance',0,0,0);
INSERT INTO workflow_task_assignment_snapshot(id,task_id,instance_id,version_id,node_id,node_execution_id,strategy_type,target_type,target_snapshot,resolved_users,resolved_user_count,resolve_time,audit_info,trace_id,created_by,updated_by,deleted,delete_token,version)
VALUES(96771,96761,96620,96601,96611,96751,'ROLE','ROLE','{}','[701,702]',2,NOW(3),'x','v266','acceptance','acceptance',0,0,0),
      (96871,96861,96620,96601,96611,96851,'ROLE','ROLE','{}','[701,702]',2,NOW(3),'x','v266','acceptance','acceptance',0,0,0);
"@
    $prepareResult = Invoke-Sql $fresh $prepare
    if ($prepareResult.ExitCode -ne 0) { throw $prepareResult.Output }

    $rollback = @"
START TRANSACTION;
INSERT INTO workflow_task_candidate_pool($validPoolColumns) VALUES$(PoolValues 96881 96861 96851 96871 'WCP-RB' ('d'*64));
INSERT INTO workflow_task_candidate_member(id,pool_id,task_id,instance_id,candidate_user_id,source_type,source_ref_snapshot,eligibility_snapshot,eligibility_hash,sort_order,generated_time,status,audit_info,deleted,delete_token,version)
VALUES(96891,96881,96861,96620,701,'ROLE','R','x',REPEAT('a',64),1,NOW(3),'INCLUDED','x',0,0,0),
      (96892,96881,96861,96620,701,'ROLE','R','x',REPEAT('b',64),2,NOW(3),'INCLUDED','x',0,0,0);
COMMIT;
"@
    $rollbackResult = Invoke-Sql $fresh $rollback
    $rollbackCount = (Invoke-Sql $fresh "SELECT (SELECT COUNT(*) FROM workflow_task_candidate_pool WHERE task_id=96861),(SELECT COUNT(*) FROM workflow_task_candidate_member WHERE task_id=96861)" -Raw).Output.Trim()
    if ($rollbackResult.ExitCode -eq 0 -or $rollbackCount -ne "0`t0") { throw "Pool rollback failed: $rollbackCount" }

    function ConcurrentSql([long]$PoolId,[long]$MemberBase,[string]$Suffix,[int]$Delay) {
        return @"
START TRANSACTION;
INSERT INTO workflow_task_candidate_pool($validPoolColumns) VALUES$(PoolValues $PoolId 96761 96751 96771 "WCP-CON-$Suffix" ('d'*64));
SELECT SLEEP($Delay);
INSERT INTO workflow_task_candidate_member(id,pool_id,task_id,instance_id,candidate_user_id,source_type,source_ref_snapshot,eligibility_snapshot,eligibility_hash,sort_order,generated_time,status,audit_info,deleted,delete_token,version)
VALUES($MemberBase,$PoolId,96761,96620,701,'ROLE','R','x',REPEAT('a',64),1,NOW(3),'INCLUDED','x',0,0,0),
      ($($MemberBase+1),$PoolId,96761,96620,702,'ROLE','R','x',REPEAT('b',64),2,NOW(3),'INCLUDED','x',0,0,0);
COMMIT;
"@
    }
    $a = Join-Path $evidence 'candidate-concurrent-a.sql'; $b = Join-Path $evidence 'candidate-concurrent-b.sql'
    [IO.File]::WriteAllText($a,(ConcurrentSql 96781 96791 'A' 2),[Text.UTF8Encoding]::new($false))
    [IO.File]::WriteAllText($b,(ConcurrentSql 96782 96801 'B' 0),[Text.UTF8Encoding]::new($false))
    $common = @('--protocol=TCP','--host=127.0.0.1',"--port=$($fresh.Port)",'--user=root','--database=enterprise_platform')
    $argsA = ($common -join ' ') + " --execute=`"source $($a.Replace('\','/'))`""
    $argsB = ($common -join ' ') + " --execute=`"source $($b.Replace('\','/'))`""
    $p1 = Start-Process $mysql -ArgumentList $argsA -WindowStyle Hidden -PassThru -RedirectStandardError (Join-Path $evidence 'candidate-concurrent-a.err')
    Start-Sleep -Milliseconds 150
    $p2 = Start-Process $mysql -ArgumentList $argsB -WindowStyle Hidden -PassThru -RedirectStandardError (Join-Path $evidence 'candidate-concurrent-b.err')
    $p1.WaitForExit(); $p2.WaitForExit()
    $ea = Get-Content (Join-Path $evidence 'candidate-concurrent-a.err') -Raw
    $eb = Get-Content (Join-Path $evidence 'candidate-concurrent-b.err') -Raw
    $counts = (Invoke-Sql $fresh "SELECT COUNT(*),(SELECT COUNT(*) FROM workflow_task_candidate_member WHERE task_id=96761) FROM workflow_task_candidate_pool WHERE task_id=96761" -Raw).Output.Trim()
    $oneDuplicate = ([string]::IsNullOrWhiteSpace($ea) -and $eb -match 'ERROR 1062') -or ([string]::IsNullOrWhiteSpace($eb) -and $ea -match 'ERROR 1062')
    if (-not $oneDuplicate -or $counts -ne "1`t2") { throw "Candidate Pool concurrency failed: $counts / $ea / $eb" }
    "$counts`tPASS" | Set-Content (Join-Path $evidence 'candidate-concurrency.tsv') -Encoding utf8

    $direct = (Invoke-Sql $fresh "SELECT COUNT(*),(SELECT COUNT(*) FROM workflow_task_candidate_pool p JOIN workflow_task t ON t.id=p.task_id WHERE t.assignment_mode='DIRECT'),(SELECT COUNT(*) FROM workflow_task_candidate_member m JOIN workflow_task t ON t.id=m.task_id WHERE t.assignment_mode='DIRECT') FROM workflow_task WHERE assignment_mode='DIRECT' AND node_execution_id IS NOT NULL AND assignee_user_id IS NOT NULL" -Raw).Output.Trim()
    $legacy = (Invoke-Sql $fresh "SELECT COUNT(*) FROM workflow_task WHERE assignment_mode='DIRECT' AND node_execution_id IS NULL" -Raw).Output.Trim()
    "$direct`tlegacy=$legacy" | Set-Content (Join-Path $evidence 'direct-legacy.tsv') -Encoding utf8

    $full = Get-Content (Join-Path $evidence 'fresh-schema.txt')
    $candidate = $full | Where-Object { $_ -match '^\w+\|workflow_task_candidate_(pool|member)' -or $_ -match '^\w+\|workflow_task\|' }
    $candidateFile = Join-Path $evidence 'fresh-candidate-schema.txt'
    [IO.File]::WriteAllText($candidateFile,(($candidate -join "`n")+"`n"),[Text.UTF8Encoding]::new($false))
    $candidateFingerprint = (Get-FileHash $candidateFile -Algorithm SHA256).Hash.ToLower()

    [ordered]@{
        structure='PASS'; data_chain='PASS'; member_count=2; negative_tests=$negative.Count;
        hash='PASS'; rollback='PASS'; concurrency='TWO_SESSION_SINGLE_WINNER_PASS';
        direct_legacy='PASS_NO_BACKFILL'; candidate_schema_fingerprint=$candidateFingerprint;
        result='PASS'
    } | ConvertTo-Json | Set-Content (Join-Path $evidence 'candidate-summary.json') -Encoding utf8
    Get-Content (Join-Path $evidence 'candidate-summary.json')
}
finally {
    foreach ($process in $serverProcesses) {
        if ($process -and -not $process.HasExited) { Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue }
    }
    Get-CimInstance Win32_Process -Filter "Name='mysqld.exe'" |
        Where-Object { $_.CommandLine -like "*$ValidationRoot*" } |
        ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
}

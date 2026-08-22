param(
    [string]$OutputRoot = "D:\codex-validation-v2612-$((Get-Date).ToString('yyyyMMdd-HHmmss'))",
    [int]$BasePort = 36200
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
$baselineSource = Join-Path $workspace 'database\mysql'
$baselineFiles = @(
    '01_database.sql','02_sys.sql','03_hr.sql','04_party.sql','05_project.sql',
    '06_operation.sql','07_investment.sql','08_data_asset.sql','09_risk.sql',
    '10_init_data.sql','11_sprint_1_user_permissions.sql','12_sprint_1_org_permissions.sql',
    '13_sprint_1_role_permissions.sql','14_sprint_1_rbac_acceptance.sql',
    '15_sprint_1_menu_center.sql','16_sprint_1_log_center.sql','V1.1.0__investment_data_risk_bi.sql'
)
$expectedSha = @{
    'V2.6.9__create_role_runtime_snapshot.sql' = 'fbe41e2a354e616ee4b369c57980009057c75ced46a2eb9e48d1eb8354beb0d2'
    'V2.6.10__harden_role_runtime_persistence_integrity.sql' = '2fad3f67a4a487568f5fc29f40cec73894d89fff65564212a75be98da186f7d6'
    'V2.6.11__repair_role_runtime_historical_integrity_guard.sql' = 'ee16801af2f989449b1372ff82d76bd9850950a51ea7badff0125804c0815b06'
    'V2.6.12__fix_role_runtime_resolver_version_collation.sql' = '55513b47c278d2e7d8f5d7656115b1383c3c50494bd221d95f58c66f972ec67e'
}
foreach ($asset in @($mysqld,$mysql,$mysqladmin,$flyway,$fingerprintSql)) {
    if (-not (Test-Path -LiteralPath $asset)) { throw "Missing validation asset: $asset" }
}
if (Test-Path -LiteralPath $OutputRoot) { throw "Output root exists: $OutputRoot" }

$evidence = Join-Path $OutputRoot 'evidence'
$baselineCopy = Join-Path $OutputRoot 'baseline'
$full = Join-Path $OutputRoot 'migrations-full'
$to268 = Join-Path $OutputRoot 'migrations-to-268'
$to2611 = Join-Path $OutputRoot 'migrations-to-2611'
New-Item -ItemType Directory -Path $evidence,$baselineCopy,$full,$to268,$to2611 -Force | Out-Null
foreach ($name in $baselineFiles) { Copy-Item (Join-Path $baselineSource $name) (Join-Path $baselineCopy $name) }
Get-ChildItem $repoMigrations -Filter 'V*.sql' -File | Copy-Item -Destination $full
Get-ChildItem $repoMigrations -Filter 'V*.sql' -File |
    Where-Object { $_.Name -notin @(
        'V2.6.9__create_role_runtime_snapshot.sql','V2.6.10__harden_role_runtime_persistence_integrity.sql',
        'V2.6.11__repair_role_runtime_historical_integrity_guard.sql','V2.6.12__fix_role_runtime_resolver_version_collation.sql') } |
    Copy-Item -Destination $to268
Get-ChildItem $repoMigrations -Filter 'V*.sql' -File |
    Where-Object Name -ne 'V2.6.12__fix_role_runtime_resolver_version_collation.sql' |
    Copy-Item -Destination $to2611

$servers = @()
$failures = @()

function New-IsolatedMysql([string]$Name,[int]$Port) {
    $active = Get-NetTCPConnection -LocalPort $Port -ErrorAction SilentlyContinue |
        Where-Object State -ne 'TimeWait'
    if ($active) { throw "Port in use: $Port" }
    $root = Join-Path $OutputRoot $Name
    $data = Join-Path $root 'data'; $logs = Join-Path $root 'logs'
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
        $old=$ErrorActionPreference; $ErrorActionPreference='Continue'
        & $mysqladmin --protocol=TCP --host=127.0.0.1 --port=$Port --user=root ping 2>$null | Out-Null
        $code=$LASTEXITCODE; $ErrorActionPreference=$old
        if ($code -eq 0) { return @{Name=$Name;Port=$Port;Root=$root} }
        Start-Sleep -Milliseconds 300
    }
    throw "$Name did not become ready"
}

function Invoke-Mysql($Server,[string]$Sql,[switch]$Raw,[string]$Database='enterprise_platform') {
    $args=@('--protocol=TCP','--host=127.0.0.1',"--port=$($Server.Port)",'--user=root')
    if ($Database) { $args += "--database=$Database" }
    if ($Raw) { $args += @('--batch','--raw','--skip-column-names') }
    $old=$ErrorActionPreference; $ErrorActionPreference='Continue'
    $out=&$mysql @args "--execute=$Sql" 2>&1; $code=$LASTEXITCODE
    $ErrorActionPreference=$old
    return @{ExitCode=$code;Output=($out -join "`n")}
}

function Invoke-MysqlFile($Server,[string]$File,[string]$Database='') {
    $args=@('--protocol=TCP','--host=127.0.0.1',"--port=$($Server.Port)",'--user=root','--default-character-set=utf8mb4')
    if ($Database) { $args += "--database=$Database" }
    $old=$ErrorActionPreference; $ErrorActionPreference='Continue'
    $out=&$mysql @args "--execute=source $((Resolve-Path $File).Path.Replace('\','/'))" 2>&1
    $code=$LASTEXITCODE; $ErrorActionPreference=$old
    return @{ExitCode=$code;Output=($out -join "`n")}
}

function Initialize-Baseline($Server) {
    foreach ($name in $baselineFiles) {
        $database = if ($name -eq '01_database.sql') { '' } else { 'enterprise_platform' }
        $result=Invoke-MysqlFile $Server (Join-Path $baselineCopy $name) $database
        if ($result.ExitCode -ne 0) { throw "Baseline failed: $name $($result.Output)" }
    }
}

function Invoke-FlywayRaw($Server,[string]$Location,[string]$Command,[string]$Target='') {
    $args=@("-url=jdbc:mysql://127.0.0.1:$($Server.Port)/enterprise_platform",'-user=root','-password=',
        "-locations=filesystem:$($Location.Replace('\','/'))",'-baselineOnMigrate=true','-baselineVersion=2.0.0',
        '-validateMigrationNaming=true','-cleanDisabled=true','-connectRetries=10')
    if ($Target) { $args += "-target=$Target" }; $args += $Command
    $log=Join-Path $evidence "$($Server.Name)-$Command$(if($Target){"-$Target"})-$([DateTime]::UtcNow.Ticks).log"
    $old=$ErrorActionPreference; $ErrorActionPreference='Continue'
    $out=&$flyway @args 2>&1; $code=$LASTEXITCODE; $ErrorActionPreference=$old
    $out | Set-Content $log -Encoding utf8
    return @{ExitCode=$code;Output=($out -join "`n");Log=$log}
}

function Invoke-Flyway($Server,[string]$Location,[string]$Command,[string]$Target='') {
    $result=Invoke-FlywayRaw $Server $Location $Command $Target
    if ($result.ExitCode -ne 0) { throw "Flyway $Command failed for $($Server.Name): $($result.Output)" }
    return $result
}

function Get-CanonicalHash([string]$ResolverVersion,[string]$ContractHash,[string]$OrganizationId) {
    $canonical = '{"candidateRuleHash":"'+('d'*64)+'","contractHash":"'+$ContractHash+
        '","directoryHash":"'+('b'*64)+'","directoryRevision":12,"effectiveAt":"2026-08-13T02:03:04.005Z"'+
        ',"organizationId":"'+$OrganizationId+'","resolverCode":"ROLE_DIRECTORY","resolverVersion":"'+$ResolverVersion+
        '","roleCode":"REVIEWER","roleRuleHash":"'+('c'*64)+'","schema":"ROLE_RUNTIME_PERSISTENCE_CANONICAL_V1"'+
        ',"sourceEvidenceHash":"'+('e'*64)+'"}'
    $algorithm=[Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($algorithm.ComputeHash([Text.Encoding]::UTF8.GetBytes($canonical)))).Replace('-','').ToLowerInvariant() }
    finally { $algorithm.Dispose() }
}

function Add-Fixture($Server,[long]$Base,[string]$ResolverVersion='ROLE_DIRECTORY_V1',
        [string]$ApprovalContract='',[string]$SnapshotContract='',[string]$BindingHash='') {
    $a='a'*64
    if (-not $ApprovalContract) { $ApprovalContract=$a }
    if (-not $SnapshotContract) { $SnapshotContract=$ApprovalContract }
    $organizationId="ORG-$Base"
    if (-not $BindingHash) { $BindingHash=Get-CanonicalHash $ResolverVersion $SnapshotContract $organizationId }
    $definition=$Base; $workflowVersion=$Base+1; $node=$Base+2; $instance=$Base+3
    $set=$Base+4; $binding=$Base+5; $nodeBinding=$Base+6; $approval=$Base+7; $snapshot=$Base+8
    $sql=@"
INSERT INTO workflow_definition(id,definition_code,definition_name,business_type,enterprise_id,owner_org_id,status,current_version_id,created_by,updated_by,deleted,delete_token,version) VALUES($definition,'WF_$Base','ROLE validation','ACCEPTANCE',2612,2612,'DRAFT',NULL,'acceptance','acceptance',0,0,0);
INSERT INTO workflow_version(id,definition_id,version_no,status,schema_version,engine_mode,content_hash_algorithm,content_hash,effective_from,published_by,published_time,created_by,updated_by,deleted,delete_token,version) VALUES($workflowVersion,$definition,1,'PUBLISHED','2.0','MULTI_NODE_LINEAR_V1','GRAPH_V2_SHA256',REPEAT('1',64),NOW(3),2612,NOW(3),'acceptance','acceptance',0,0,0);
INSERT INTO workflow_node(id,version_id,node_code,node_name,node_type,node_order,governance_node_type,approval_mode,assignment_rule_type,assignment_rule_config,withdraw_allowed,enabled,created_by,updated_by,deleted,delete_token,version) VALUES($node,$workflowVersion,'ROLE_NODE_$Base','ROLE Node','APPROVAL',1,'GENERAL_APPROVAL','SINGLE','USER','{"userId":2612}',0,1,'acceptance','acceptance',0,0,0);
UPDATE workflow_definition SET status='ACTIVE',current_version_id=$workflowVersion,version=1 WHERE id=$definition;
INSERT INTO workflow_instance(id,instance_no,definition_id,version_id,definition_code_snapshot,definition_version_no,definition_content_hash_snapshot,resolver_code,resolver_version,resolver_contract_hash,business_type,business_id,business_key,enterprise_id,attempt_no,initiator_user_id,initiator_org_id,current_node_id,engine_mode,content_hash_algorithm_snapshot,current_node_execution_id,status,idempotency_key,request_hash,event_sequence,trace_id,started_time,created_by,updated_by,deleted,delete_token,version) VALUES($instance,'WFI-$Base',$definition,$workflowVersion,'WF_$Base',1,REPEAT('1',64),'EXPLICIT_USER','EXPLICIT_USER_V1','3166103d7697a6f2e46932214b0447310c69d6b624f6d92a282e9ef3ab496853','ACCEPTANCE','$instance','ACCEPTANCE:$instance',2612,1,2612,2612,$node,'MULTI_NODE_LINEAR_V1','GRAPH_V2_SHA256',NULL,'RUNNING','fixture-$Base',REPEAT('2',64),0,'trace-$Base',NOW(3),'acceptance','acceptance',0,0,0);
INSERT INTO workflow_instance_resolver_binding_set(id,instance_id,definition_id,definition_version_id,manifest_version,manifest_hash,binding_count,binding_status,frozen_time,audit_info,created_by,updated_by,deleted,delete_token,version) VALUES($set,$instance,$definition,$workflowVersion,'MULTI_RESOLVER_V1',REPEAT('3',64),1,'FROZEN',NOW(3),'acceptance','acceptance','acceptance',0,0,0);
INSERT INTO workflow_instance_resolver_binding(id,binding_set_id,instance_id,definition_version_id,resolver_code,resolver_version,strategy_type,resolver_mode,contract_hash,rule_hash,binding_status,frozen_time,audit_info,created_by,updated_by,deleted,delete_token,version) VALUES($binding,$set,$instance,$workflowVersion,'ROLE_DIRECTORY','$ResolverVersion','ROLE','CANDIDATE_POOL','$SnapshotContract',REPEAT('b',64),'FROZEN',NOW(3),'acceptance','acceptance','acceptance',0,0,0);
INSERT INTO workflow_node_resolver_binding_snapshot(id,binding_set_id,resolver_binding_id,instance_id,definition_version_id,node_id,node_code_snapshot,strategy_type,resolver_mode,target_type,target_value_snapshot,rule_version,rule_snapshot,rule_hash,node_binding_hash,binding_status,frozen_time,audit_info,created_by,updated_by,deleted,delete_token,version) VALUES($nodeBinding,$set,$binding,$instance,$workflowVersion,$node,'ROLE_NODE_$Base','ROLE','CANDIDATE_POOL','ROLE','{"roleCode":"REVIEWER"}','RULE_V1','{"roleCode":"REVIEWER"}',REPEAT('b',64),REPEAT('c',64),'FROZEN',NOW(3),'acceptance','acceptance','acceptance',0,0,0);
INSERT INTO role_runtime_binding_approval(id,proposal_hash,eligibility_hash,resolver_code,resolver_version,contract_hash,status,approved_by,approved_at,audit_info,created_by,updated_by,deleted,delete_token,version) VALUES($approval,SHA2('proposal-$Base',256),SHA2('eligibility-$Base',256),'ROLE_DIRECTORY','$ResolverVersion','$ApprovalContract','APPROVED','approver',NOW(3),'acceptance','acceptance','acceptance',0,0,0);
INSERT INTO workflow_role_runtime_binding_snapshot(id,approval_id,binding_set_id,resolver_binding_id,node_resolver_binding_id,instance_id,definition_version_id,node_id,resolver_code,resolver_version,contract_hash,role_code,organization_id,directory_revision,effective_at,directory_hash,role_rule_hash,candidate_rule_hash,source_evidence_hash,binding_hash,status,audit_info,created_by,updated_by,deleted,delete_token,version) VALUES($snapshot,$approval,$set,$binding,$nodeBinding,$instance,$workflowVersion,$node,'ROLE_DIRECTORY','$ResolverVersion','$SnapshotContract','REVIEWER','$organizationId',12,'2026-08-13 02:03:04.005',REPEAT('b',64),REPEAT('c',64),REPEAT('d',64),REPEAT('e',64),'$BindingHash','FROZEN','acceptance','acceptance','acceptance',0,0,0);
"@
    $result=Invoke-Mysql $Server $sql
    if ($result.ExitCode -ne 0) { throw "Fixture failed: $($result.Output)" }
    return @{Approval=$approval;Snapshot=$snapshot;BindingHash=$BindingHash}
}

function Get-Fingerprint($Server) {
    $args=@('--protocol=TCP','--host=127.0.0.1',"--port=$($Server.Port)",'--user=root','--database=enterprise_platform','--batch','--raw','--skip-column-names')
    $rows=Get-Content $fingerprintSql -Raw | &$mysql @args
    if ($LASTEXITCODE -ne 0) { throw 'Fingerprint query failed' }
    $triggerSql="SELECT CONCAT('TRIGGER|',trigger_name,'|',event_manipulation,'|',event_object_table,'|',action_timing,'|',REPLACE(REPLACE(REPLACE(action_statement,'|','\\|'),CHAR(13),' '),CHAR(10),' ')) FROM information_schema.triggers WHERE trigger_schema=DATABASE() ORDER BY trigger_name"
    $triggers=&$mysql @args "--execute=$triggerSql"
    $all=@($rows)+@($triggers)
    $workflow=$all | Where-Object { $_ -match '^\w+\|workflow_' -or $_ -match '^\w+\|role_runtime_' -or $_ -match '^TRIGGER\|.*\|(workflow_|role_runtime_)' }
    $role=$all | Where-Object { $_ -match '^\w+\|role_runtime_binding_approval\|' -or $_ -match '^\w+\|workflow_role_runtime_binding_snapshot\|' -or $_ -match '^TRIGGER\|.*\|(role_runtime_binding_approval|workflow_role_runtime_binding_snapshot)' }
    $result=@{}
    foreach ($pair in @(@('full',$all),@('workflow',$workflow),@('role',$role))) {
        $file=Join-Path $evidence "$($Server.Name)-$($pair[0])-schema.txt"
        [IO.File]::WriteAllText($file,(($pair[1] -join "`n")+"`n"),[Text.UTF8Encoding]::new($false))
        $result[$pair[0]]=(Get-FileHash $file -Algorithm SHA256).Hash.ToLowerInvariant()
    }
    return $result
}

function Assert-Rejected($Server,[string]$Name,[string]$Sql,[ref]$Log) {
    $result=Invoke-Mysql $Server $Sql
    $pass=$result.ExitCode -ne 0
    $Log.Value += "$Name`t$(if($pass){'PASS'}else{'FAIL'})`t$($result.Output.Replace("`n",' '))"
    if (-not $pass) { $script:failures += "Database accepted: $Name" }
}

function Get-RoleDdlSignature($Server) {
    $sql=@"
SELECT CONCAT('COLUMN|',table_name,'|',column_name,'|',column_type,'|',character_set_name,'|',collation_name,'|',is_nullable)
FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name IN ('role_runtime_binding_approval','workflow_role_runtime_binding_snapshot') AND column_name='resolver_version'
UNION ALL
SELECT CONCAT('CHECK|',tc.table_name,'|',tc.constraint_name,'|',cc.check_clause)
FROM information_schema.table_constraints tc JOIN information_schema.check_constraints cc ON cc.constraint_schema=tc.constraint_schema AND cc.constraint_name=tc.constraint_name
WHERE tc.table_schema=DATABASE() AND tc.table_name IN ('role_runtime_binding_approval','workflow_role_runtime_binding_snapshot') AND tc.constraint_type='CHECK'
UNION ALL
SELECT CONCAT('TRIGGER|',event_object_table,'|',trigger_name,'|',event_manipulation,'|',action_timing)
FROM information_schema.triggers WHERE trigger_schema=DATABASE() AND event_object_table IN ('role_runtime_binding_approval','workflow_role_runtime_binding_snapshot')
ORDER BY 1
"@
    return (Invoke-Mysql $Server $sql -Raw).Output.Trim()
}

try {
    $fresh=New-IsolatedMysql 'fresh' ($BasePort+1)
    $upgrade=New-IsolatedMysql 'upgrade' ($BasePort+2)
    $forward=New-IsolatedMysql 'forward' ($BasePort+3)
    $guard=New-IsolatedMysql 'failed-guard-recovery' ($BasePort+4)
    foreach ($server in @($fresh,$upgrade,$forward,$guard)) { Initialize-Baseline $server }

    Invoke-Flyway $fresh $full migrate '2.6.12' | Out-Null
    Invoke-Flyway $fresh $full info | Out-Null
    Invoke-Flyway $fresh $full validate | Out-Null
    $freshNoop=Invoke-Flyway $fresh $full migrate '2.6.12'

    Invoke-Flyway $upgrade $to268 migrate '2.6.8' | Out-Null
    $upgradeBefore=[int](Invoke-Mysql $upgrade "SELECT COUNT(*) FROM flyway_schema_history WHERE success=1" -Raw).Output.Trim()
    Invoke-Flyway $upgrade $full migrate '2.6.12' | Out-Null
    Invoke-Flyway $upgrade $full validate | Out-Null
    $upgradeNoop=Invoke-Flyway $upgrade $full migrate '2.6.12'
    $upgradeAfter=[int](Invoke-Mysql $upgrade "SELECT COUNT(*) FROM flyway_schema_history WHERE success=1" -Raw).Output.Trim()

    Invoke-Flyway $forward $to2611 migrate '2.6.11' | Out-Null
    $beforeColumns=Invoke-Mysql $forward "SELECT table_name,column_type,character_set_name,collation_name,is_nullable FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name IN ('role_runtime_binding_approval','workflow_role_runtime_binding_snapshot') AND column_name='resolver_version' ORDER BY table_name" -Raw
    $forwardBefore=[int](Invoke-Mysql $forward "SELECT COUNT(*) FROM flyway_schema_history WHERE success=1" -Raw).Output.Trim()
    Invoke-Flyway $forward $full migrate '2.6.12' | Out-Null
    Invoke-Flyway $forward $full validate | Out-Null
    $forwardNoop=Invoke-Flyway $forward $full migrate '2.6.12'
    $forwardAfter=[int](Invoke-Mysql $forward "SELECT COUNT(*) FROM flyway_schema_history WHERE success=1" -Raw).Output.Trim()
    $afterColumns=Invoke-Mysql $forward "SELECT table_name,column_type,character_set_name,collation_name,is_nullable FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name IN ('role_runtime_binding_approval','workflow_role_runtime_binding_snapshot') AND column_name='resolver_version' ORDER BY table_name" -Raw

    if ($upgradeAfter-$upgradeBefore -ne 4) { $failures += 'Upgrade did not apply exactly V2.6.9-V2.6.12' }
    if ($forwardAfter-$forwardBefore -ne 1) { $failures += 'Forward did not apply exactly V2.6.12' }
    foreach ($noop in @($freshNoop,$upgradeNoop,$forwardNoop)) { if ($noop.Output -notmatch 'No migration necessary') { $failures += 'Second migrate was not no-op' } }

    $fpFresh=Get-Fingerprint $fresh; $fpUpgrade=Get-Fingerprint $upgrade; $fpForward=Get-Fingerprint $forward
    foreach ($scope in @('full','workflow','role')) { if ($fpFresh[$scope] -ne $fpUpgrade[$scope] -or $fpFresh[$scope] -ne $fpForward[$scope]) { $failures += "Fingerprint mismatch: $scope" } }

    $columnResult=Invoke-Mysql $forward "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name IN ('role_runtime_binding_approval','workflow_role_runtime_binding_snapshot') AND column_name='resolver_version' AND data_type='varchar' AND character_maximum_length=64 AND character_set_name='ascii' AND collation_name='ascii_bin' AND is_nullable='NO'" -Raw
    if ($columnResult.Output.Trim() -ne '2') { $failures += 'V2.6.12 column governance mismatch' }

    # Failed Guard Recovery: V2.6.11 is successful first. In this disposable fixture only,
    # remove write guards, inject three forms of corrupt historical evidence, then prove
    # V2.6.12 fails before changing its target columns/CHECKs or adding any object.
    Invoke-Flyway $guard $to2611 migrate '2.6.11' | Out-Null
    $drop=Invoke-Mysql $guard "DROP TRIGGER trg_workflow_role_runtime_snapshot_insert_guard; DROP TRIGGER trg_workflow_role_runtime_snapshot_canonical_guard; ALTER TABLE workflow_role_runtime_binding_snapshot DROP CHECK chk_role_runtime_snapshot_hashes;"
    if($drop.ExitCode-ne0){throw $drop.Output}
    $dirty=Add-Fixture $guard 261200 'role_directory_v1' ('a'*64) ('b'*64) ('A'*64)
    $guardBefore=Get-RoleDdlSignature $guard
    $guardResult=Invoke-FlywayRaw $guard $full migrate '2.6.12'
    $guardAfter=Get-RoleDdlSignature $guard
    $guardPass=$guardResult.ExitCode-ne0 -and $guardBefore-eq$guardAfter -and $guardResult.Output-match 'chk_role_runtime_v2612_guard|3819'
    if(-not$guardPass){$failures+='Failed Guard Recovery did not fail cleanly before V2.6.12 DDL'}
    @("result`tbefore_equals_after`tdetail","$(if($guardPass){'PASS'}else{'FAIL'})`t$($guardBefore-eq$guardAfter)`t$($guardResult.Output.Replace("`n",' '))") | Set-Content (Join-Path $evidence 'guard-recovery.tsv') -Encoding utf8

    $legal=Add-Fixture $forward 261300
    $negative=@("name`tresult`tdetail")
    $baseApproval="INSERT INTO role_runtime_binding_approval(id,proposal_hash,eligibility_hash,resolver_code,resolver_version,contract_hash,status,approved_by,approved_at,audit_info,created_by,updated_by,deleted,delete_token,version) VALUES({ID},SHA2('{NAME}-proposal',256),SHA2('{NAME}-eligibility',256),'ROLE_DIRECTORY',{VERSION},REPEAT('a',64),'APPROVED','approver',NOW(3),'acceptance','acceptance','acceptance',0,0,0)"
    $valid=Invoke-Mysql $forward ($baseApproval.Replace('{ID}','261400').Replace('{NAME}','uppercase').Replace('{VERSION}',"'ROLE_DIRECTORY_V1'"))
    if($valid.ExitCode-ne0){$failures+="Uppercase resolver version rejected: $($valid.Output)"}; $negative += "uppercase`t$(if($valid.ExitCode-eq0){'PASS'}else{'FAIL'})`t$($valid.Output.Replace("`n",' '))"
    Assert-Rejected $forward 'lowercase' ($baseApproval.Replace('{ID}','261401').Replace('{NAME}','lowercase').Replace('{VERSION}',"'role_directory_v1'")) ([ref]$negative)
    Assert-Rejected $forward 'mixed_case' ($baseApproval.Replace('{ID}','261402').Replace('{NAME}','mixed').Replace('{VERSION}',"'Role_Directory_V1'")) ([ref]$negative)
    Assert-Rejected $forward 'partial_lowercase' ($baseApproval.Replace('{ID}','261403').Replace('{NAME}','partial').Replace('{VERSION}',"'ROLE_directory_V1'")) ([ref]$negative)
    Assert-Rejected $forward 'blank' ($baseApproval.Replace('{ID}','261404').Replace('{NAME}','blank').Replace('{VERSION}',"''")) ([ref]$negative)
    Assert-Rejected $forward 'null' ($baseApproval.Replace('{ID}','261405').Replace('{NAME}','null').Replace('{VERSION}','NULL')) ([ref]$negative)
    Assert-Rejected $forward 'approval_update' "UPDATE role_runtime_binding_approval SET resolver_version='OTHER_V1' WHERE id=$($legal.Approval)" ([ref]$negative)
    Assert-Rejected $forward 'approval_delete' "DELETE FROM role_runtime_binding_approval WHERE id=$($legal.Approval)" ([ref]$negative)
    Assert-Rejected $forward 'snapshot_update' "UPDATE workflow_role_runtime_binding_snapshot SET resolver_version='OTHER_V1' WHERE id=$($legal.Snapshot)" ([ref]$negative)
    Assert-Rejected $forward 'snapshot_delete' "DELETE FROM workflow_role_runtime_binding_snapshot WHERE id=$($legal.Snapshot)" ([ref]$negative)
    $negative | Set-Content (Join-Path $evidence 'negative.tsv') -Encoding utf8

    $hashUpper=Get-CanonicalHash 'ROLE_DIRECTORY_V1' ('a'*64) 'ORG-HASH'
    $hashLower=Get-CanonicalHash 'role_directory_v1' ('a'*64) 'ORG-HASH'
    $hashContract=Get-CanonicalHash 'ROLE_DIRECTORY_V1' ('f'*64) 'ORG-HASH'
    $hashBinding=Get-CanonicalHash 'ROLE_DIRECTORY_V1' ('a'*64) 'ORG-HASH-CHANGED'
    if($hashUpper-eq$hashLower-or$hashUpper-eq$hashContract-or$hashUpper-eq$hashBinding){$failures+='Canonical Hash sensitivity failed'}

    $compatBefore=(Invoke-Mysql $forward "SELECT (SELECT COUNT(*) FROM workflow_task WHERE assignment_mode='DIRECT'),(SELECT COUNT(*) FROM workflow_task_candidate_pool),(SELECT COUNT(*) FROM workflow_task_claim),(SELECT COUNT(*) FROM workflow_role_runtime_binding_snapshot WHERE resolver_code='ROLE_DIRECTORY')" -Raw).Output.Trim()
    $compatAfter=(Invoke-Mysql $forward "SELECT (SELECT COUNT(*) FROM workflow_task WHERE assignment_mode='DIRECT'),(SELECT COUNT(*) FROM workflow_task_candidate_pool),(SELECT COUNT(*) FROM workflow_task_claim),(SELECT COUNT(*) FROM workflow_role_runtime_binding_snapshot WHERE resolver_code='ROLE_DIRECTORY')" -Raw).Output.Trim()
    if($compatBefore-ne$compatAfter){$failures+='Compatibility counts changed without runtime execution'}

    $raceSql="START TRANSACTION; SELECT resolver_version,contract_hash FROM role_runtime_binding_approval WHERE id=$($legal.Approval) FOR SHARE; SELECT resolver_version,binding_hash FROM workflow_role_runtime_binding_snapshot WHERE id=$($legal.Snapshot) FOR SHARE; COMMIT;"
    $raceWorker={param($client,$port,$statement)
        $output=&$client --protocol=TCP --host=127.0.0.1 "--port=$port" --user=root --database=enterprise_platform "--execute=$statement" 2>&1
        [pscustomobject]@{ExitCode=$LASTEXITCODE;Output=($output -join "`n")}
    }
    $job1=Start-Job -ScriptBlock $raceWorker -ArgumentList $mysql,$forward.Port,$raceSql
    $job2=Start-Job -ScriptBlock $raceWorker -ArgumentList $mysql,$forward.Port,$raceSql
    Wait-Job $job1,$job2 | Out-Null
    $result1=Receive-Job $job1; $result2=Receive-Job $job2
    Remove-Job $job1,$job2 -Force
    $raceText="$($result1.Output)`n$($result2.Output)"
    if($result1.ExitCode-ne0-or$result2.ExitCode-ne0-or$raceText-match'ERROR 1213|ERROR 1205'){$failures+='Concurrent Approval/Snapshot reads failed or deadlocked'}

    $checksums=(Invoke-Mysql $fresh "SELECT version,checksum FROM flyway_schema_history WHERE version IN ('2.6.9','2.6.10','2.6.11','2.6.12') ORDER BY installed_rank" -Raw).Output.Trim()
    foreach ($entry in $expectedSha.GetEnumerator()) { $actual=(Get-FileHash (Join-Path $repoMigrations $entry.Key) -Algorithm SHA256).Hash.ToLowerInvariant(); if($actual-ne$entry.Value){$failures+="SHA drift: $($entry.Key)"} }
    $sumLines=Get-Content (Join-Path $repoMigrations 'SHA256SUMS'); $shaPass=0
    foreach($line in $sumLines){if($line-match'^([0-9a-f]{64})\s+(.+)$'){if((Get-FileHash (Join-Path $repoMigrations $matches[2]) -Algorithm SHA256).Hash.ToLowerInvariant()-eq$matches[1]){$shaPass++}}}
    if($shaPass-ne$sumLines.Count){$failures+='Migration SHA list mismatch'}

    $summary=[ordered]@{
        result=$(if($failures.Count-eq0){'PASS'}else{'FAIL'}); output_root=$OutputRoot
        mysql_version=((&$mysqld --version)-join' '); flyway_version='13.0.0'; java_version='21.0.12'; maven_version='3.9.9'
        checksums=$checksums; fresh_no_op=($freshNoop.Output-match'No migration necessary'); upgrade_applied=$upgradeAfter-$upgradeBefore
        upgrade_no_op=($upgradeNoop.Output-match'No migration necessary'); forward_applied=$forwardAfter-$forwardBefore; forward_no_op=($forwardNoop.Output-match'No migration necessary')
        full_schema_fingerprint=$fpFresh.full; workflow_schema_fingerprint=$fpFresh.workflow; role_runtime_schema_fingerprint=$fpFresh.role
        columns_before=$beforeColumns.Output; columns_after=$afterColumns.Output; governed_column_count=$columnResult.Output.Trim()
        guard_recovery=$(if($guardPass){'PASS'}else{'FAIL'}); negative_results=$negative[1..($negative.Count-1)]
        hash_upper=$hashUpper; hash_lower=$hashLower; hash_contract_changed=$hashContract; hash_binding_changed=$hashBinding
        compatibility_before=$compatBefore; compatibility_after=$compatAfter
        concurrent_exit_codes="$($result1.ExitCode),$($result2.ExitCode)"; concurrent_output=$raceText
        sha="$shaPass/$($sumLines.Count)"; failures=$failures
    }
    $summary | ConvertTo-Json -Depth 6 | Set-Content (Join-Path $evidence 'summary.json') -Encoding utf8
    $summary | ConvertTo-Json -Depth 6
    if($summary.result-ne'PASS'){throw($failures-join'; ')}
}
finally {
    foreach($process in $servers){if($process-and-not$process.HasExited){Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue}}
    Get-CimInstance Win32_Process -Filter "Name='mysqld.exe'" | Where-Object { $_.CommandLine -like "*$OutputRoot*" } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
}

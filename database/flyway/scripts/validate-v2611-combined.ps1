param(
    [string]$OutputRoot = "D:\codex-validation-v2611-$((Get-Date).ToString('yyyyMMdd-HHmmss'))",
    [int]$BasePort = 35800
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
}
foreach ($asset in @($mysqld,$mysql,$mysqladmin,$flyway,$fingerprintSql)) {
    if (-not (Test-Path -LiteralPath $asset)) { throw "Missing validation asset: $asset" }
}
if (Test-Path -LiteralPath $OutputRoot) { throw "Output root exists: $OutputRoot" }

$evidence = Join-Path $OutputRoot 'evidence'
$baselineCopy = Join-Path $OutputRoot 'baseline'
$full = Join-Path $OutputRoot 'migrations-full'
$to268 = Join-Path $OutputRoot 'migrations-to-268'
$to269 = Join-Path $OutputRoot 'migrations-to-269'
$to2610 = Join-Path $OutputRoot 'migrations-to-2610'
New-Item -ItemType Directory -Path $evidence,$baselineCopy,$full,$to268,$to269,$to2610 -Force | Out-Null
foreach ($name in $baselineFiles) { Copy-Item (Join-Path $baselineSource $name) (Join-Path $baselineCopy $name) }
Get-ChildItem $repoMigrations -Filter 'V*.sql' -File | Copy-Item -Destination $full
Get-ChildItem $repoMigrations -Filter 'V*.sql' -File |
    Where-Object { $_.Name -notin @(
        'V2.6.9__create_role_runtime_snapshot.sql',
        'V2.6.10__harden_role_runtime_persistence_integrity.sql',
        'V2.6.11__repair_role_runtime_historical_integrity_guard.sql') } |
    Copy-Item -Destination $to268
Get-ChildItem $repoMigrations -Filter 'V*.sql' -File |
    Where-Object Name -ne 'V2.6.11__repair_role_runtime_historical_integrity_guard.sql' |
    Copy-Item -Destination $to2610
Get-ChildItem $repoMigrations -Filter 'V*.sql' -File |
    Where-Object { $_.Name -notin @(
        'V2.6.10__harden_role_runtime_persistence_integrity.sql',
        'V2.6.11__repair_role_runtime_historical_integrity_guard.sql') } |
    Copy-Item -Destination $to269

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

function Get-CanonicalHash([string]$ResolverCode,[string]$ResolverVersion,[string]$ContractHash,
        [string]$RoleCode,[string]$OrganizationId,[long]$Revision,[string]$EffectiveAt,
        [string]$DirectoryHash,[string]$RoleRuleHash,[string]$CandidateRuleHash,[string]$SourceHash) {
    $canonical = '{"candidateRuleHash":"'+$CandidateRuleHash+'","contractHash":"'+$ContractHash+
        '","directoryHash":"'+$DirectoryHash+'","directoryRevision":'+$Revision+
        ',"effectiveAt":"'+$EffectiveAt+'","organizationId":"'+$OrganizationId+
        '","resolverCode":"'+$ResolverCode+'","resolverVersion":"'+$ResolverVersion+
        '","roleCode":"'+$RoleCode+'","roleRuleHash":"'+$RoleRuleHash+
        '","schema":"ROLE_RUNTIME_PERSISTENCE_CANONICAL_V1","sourceEvidenceHash":"'+$SourceHash+'"}'
    $bytes=[Text.Encoding]::UTF8.GetBytes($canonical)
    $algorithm=[Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($algorithm.ComputeHash($bytes))).Replace('-','').ToLowerInvariant() }
    finally { $algorithm.Dispose() }
}

function Add-Fixture($Server,[long]$Base,[string]$ResolverVersion='ROLE_DIRECTORY_V1',
        [string]$ApprovalContract='',[string]$SnapshotContract='',[string]$BindingHash='',
        [bool]$InsertSnapshot=$true) {
    $a='a'*64; $b='b'*64; $c='c'*64; $dHash='d'*64; $e='e'*64
    if (-not $ApprovalContract) { $ApprovalContract=$a }
    if (-not $SnapshotContract) { $SnapshotContract=$ApprovalContract }
    $organizationId="ORG-$Base"
    $effectiveInstant='2026-08-13T02:03:04.005Z'; $effectiveSql='2026-08-13 02:03:04.005'
    if (-not $BindingHash) { $BindingHash=Get-CanonicalHash 'ROLE_DIRECTORY' $ResolverVersion $SnapshotContract 'REVIEWER' $organizationId 12 $effectiveInstant $b $c $dHash $e }
    $definition=$Base; $workflowVersion=$Base+1; $node=$Base+2; $instance=$Base+3
    $set=$Base+4; $binding=$Base+5; $nodeBinding=$Base+6; $approval=$Base+7; $snapshot=$Base+8
    $sql=@"
INSERT INTO workflow_definition(id,definition_code,definition_name,business_type,enterprise_id,owner_org_id,status,current_version_id,created_by,updated_by,deleted,delete_token,version) VALUES($definition,'WF_$Base','ROLE validation','ACCEPTANCE',2611,2611,'DRAFT',NULL,'acceptance','acceptance',0,0,0);
INSERT INTO workflow_version(id,definition_id,version_no,status,schema_version,engine_mode,content_hash_algorithm,content_hash,effective_from,published_by,published_time,created_by,updated_by,deleted,delete_token,version) VALUES($workflowVersion,$definition,1,'PUBLISHED','2.0','MULTI_NODE_LINEAR_V1','GRAPH_V2_SHA256',REPEAT('1',64),NOW(3),2611,NOW(3),'acceptance','acceptance',0,0,0);
INSERT INTO workflow_node(id,version_id,node_code,node_name,node_type,node_order,governance_node_type,approval_mode,assignment_rule_type,assignment_rule_config,withdraw_allowed,enabled,created_by,updated_by,deleted,delete_token,version) VALUES($node,$workflowVersion,'ROLE_NODE_$Base','ROLE Node','APPROVAL',1,'GENERAL_APPROVAL','SINGLE','USER','{"userId":2611}',0,1,'acceptance','acceptance',0,0,0);
UPDATE workflow_definition SET status='ACTIVE',current_version_id=$workflowVersion,version=1 WHERE id=$definition;
INSERT INTO workflow_instance(id,instance_no,definition_id,version_id,definition_code_snapshot,definition_version_no,definition_content_hash_snapshot,resolver_code,resolver_version,resolver_contract_hash,business_type,business_id,business_key,enterprise_id,attempt_no,initiator_user_id,initiator_org_id,current_node_id,engine_mode,content_hash_algorithm_snapshot,current_node_execution_id,status,idempotency_key,request_hash,event_sequence,trace_id,started_time,created_by,updated_by,deleted,delete_token,version) VALUES($instance,'WFI-$Base',$definition,$workflowVersion,'WF_$Base',1,REPEAT('1',64),'EXPLICIT_USER','EXPLICIT_USER_V1','3166103d7697a6f2e46932214b0447310c69d6b624f6d92a282e9ef3ab496853','ACCEPTANCE','$instance','ACCEPTANCE:$instance',2611,1,2611,2611,$node,'MULTI_NODE_LINEAR_V1','GRAPH_V2_SHA256',NULL,'RUNNING','fixture-$Base',REPEAT('2',64),0,'trace-$Base',NOW(3),'acceptance','acceptance',0,0,0);
INSERT INTO workflow_instance_resolver_binding_set(id,instance_id,definition_id,definition_version_id,manifest_version,manifest_hash,binding_count,binding_status,frozen_time,audit_info,created_by,updated_by,deleted,delete_token,version) VALUES($set,$instance,$definition,$workflowVersion,'MULTI_RESOLVER_V1',REPEAT('3',64),1,'FROZEN',NOW(3),'acceptance','acceptance','acceptance',0,0,0);
INSERT INTO workflow_instance_resolver_binding(id,binding_set_id,instance_id,definition_version_id,resolver_code,resolver_version,strategy_type,resolver_mode,contract_hash,rule_hash,binding_status,frozen_time,audit_info,created_by,updated_by,deleted,delete_token,version) VALUES($binding,$set,$instance,$workflowVersion,'ROLE_DIRECTORY','$ResolverVersion','ROLE','CANDIDATE_POOL','$SnapshotContract',REPEAT('b',64),'FROZEN',NOW(3),'acceptance','acceptance','acceptance',0,0,0);
INSERT INTO workflow_node_resolver_binding_snapshot(id,binding_set_id,resolver_binding_id,instance_id,definition_version_id,node_id,node_code_snapshot,strategy_type,resolver_mode,target_type,target_value_snapshot,rule_version,rule_snapshot,rule_hash,node_binding_hash,binding_status,frozen_time,audit_info,created_by,updated_by,deleted,delete_token,version) VALUES($nodeBinding,$set,$binding,$instance,$workflowVersion,$node,'ROLE_NODE_$Base','ROLE','CANDIDATE_POOL','ROLE','{"roleCode":"REVIEWER"}','RULE_V1','{"roleCode":"REVIEWER"}',REPEAT('b',64),REPEAT('c',64),'FROZEN',NOW(3),'acceptance','acceptance','acceptance',0,0,0);
INSERT INTO role_runtime_binding_approval(id,proposal_hash,eligibility_hash,resolver_code,resolver_version,contract_hash,status,approved_by,approved_at,audit_info,created_by,updated_by,deleted,delete_token,version) VALUES($approval,SHA2('proposal-$Base',256),SHA2('eligibility-$Base',256),'ROLE_DIRECTORY','$ResolverVersion','$ApprovalContract','APPROVED','approver',NOW(3),'acceptance','acceptance','acceptance',0,0,0);
"@
    if ($InsertSnapshot) {
        $sql += "`nINSERT INTO workflow_role_runtime_binding_snapshot(id,approval_id,binding_set_id,resolver_binding_id,node_resolver_binding_id,instance_id,definition_version_id,node_id,resolver_code,resolver_version,contract_hash,role_code,organization_id,directory_revision,effective_at,directory_hash,role_rule_hash,candidate_rule_hash,source_evidence_hash,binding_hash,status,audit_info,created_by,updated_by,deleted,delete_token,version) VALUES($snapshot,$approval,$set,$binding,$nodeBinding,$instance,$workflowVersion,$node,'ROLE_DIRECTORY','$ResolverVersion','$SnapshotContract','REVIEWER','$organizationId',12,'$effectiveSql',REPEAT('b',64),REPEAT('c',64),REPEAT('d',64),REPEAT('e',64),'$BindingHash','FROZEN','acceptance','acceptance','acceptance',0,0,0);"
    }
    $result=Invoke-Mysql $Server $sql
    if ($result.ExitCode -ne 0) { throw "Fixture failed: $($result.Output)" }
    return @{Approval=$approval;Snapshot=$snapshot;BindingHash=$BindingHash;Ids=@($set,$binding,$nodeBinding,$instance,$workflowVersion,$node)}
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

function Get-V2611PermanentCount($Server) {
    return (Invoke-Mysql $Server "SELECT COUNT(*) FROM information_schema.triggers WHERE trigger_schema=DATABASE() AND trigger_name IN ('trg_role_runtime_approval_no_delete','trg_workflow_role_runtime_snapshot_canonical_guard')" -Raw).Output.Trim()
}

function Assert-Rejected($Server,[string]$Name,[string]$Sql,[ref]$Log) {
    $result=Invoke-Mysql $Server $Sql
    $pass=$result.ExitCode -ne 0
    $Log.Value += "$Name`t$(if($pass){'PASS'}else{'FAIL'})`t$($result.Output.Replace("`n",' '))"
    if (-not $pass) { $script:failures += "Database accepted: $Name" }
}

function Test-DirtyGuard($Server,[string]$Name,[scriptblock]$Prepare) {
    Invoke-Flyway $Server $to269 migrate '2.6.9' | Out-Null
    & $Prepare $Server
    Invoke-Flyway $Server $to2610 migrate '2.6.10' | Out-Null
    $before=Get-V2611PermanentCount $Server
    $result=Invoke-FlywayRaw $Server $full migrate '2.6.11'
    $after=Get-V2611PermanentCount $Server
    $pass=$result.ExitCode -ne 0 -and $before -eq '0' -and $after -eq '0' -and $result.Output -match 'chk_role_runtime_v2611_guard|3819'
    if (-not $pass) { $script:failures += "V2.6.11 dirty guard failed: $Name" }
    return "$Name`t$(if($pass){'PASS'}else{'FAIL'})`t$($result.ExitCode)`t$before`t$after`t$($result.Output.Replace("`n",' '))"
}

try {
    $fresh=New-IsolatedMysql 'fresh' ($BasePort+1)
    $upgrade=New-IsolatedMysql 'upgrade' ($BasePort+2)
    $forward=New-IsolatedMysql 'forward' ($BasePort+3)
    $badResolver=New-IsolatedMysql 'guard-resolver' ($BasePort+4)
    $badContract=New-IsolatedMysql 'guard-contract' ($BasePort+5)
    $badHash=New-IsolatedMysql 'guard-hash' ($BasePort+6)
    $badCanonical=New-IsolatedMysql 'guard-canonical' ($BasePort+7)
    foreach ($server in @($fresh,$upgrade,$forward,$badResolver,$badContract,$badHash,$badCanonical)) { Initialize-Baseline $server }

    Invoke-Flyway $fresh $full migrate '2.6.11' | Out-Null
    Invoke-Flyway $fresh $full info | Out-Null
    Invoke-Flyway $fresh $full validate | Out-Null
    $freshNoop=Invoke-Flyway $fresh $full migrate '2.6.11'

    Invoke-Flyway $upgrade $to268 migrate '2.6.8' | Out-Null
    $upgradeBefore=[int](Invoke-Mysql $upgrade "SELECT COUNT(*) FROM flyway_schema_history WHERE success=1" -Raw).Output.Trim()
    Invoke-Flyway $upgrade $full migrate '2.6.11' | Out-Null
    Invoke-Flyway $upgrade $full validate | Out-Null
    $upgradeNoop=Invoke-Flyway $upgrade $full migrate '2.6.11'
    $upgradeAfter=[int](Invoke-Mysql $upgrade "SELECT COUNT(*) FROM flyway_schema_history WHERE success=1" -Raw).Output.Trim()

    Invoke-Flyway $forward $to2610 migrate '2.6.10' | Out-Null
    $legal=Add-Fixture $forward 261100
    $forwardBefore=[int](Invoke-Mysql $forward "SELECT COUNT(*) FROM flyway_schema_history WHERE success=1" -Raw).Output.Trim()
    Invoke-Flyway $forward $full migrate '2.6.11' | Out-Null
    Invoke-Flyway $forward $full validate | Out-Null
    $forwardNoop=Invoke-Flyway $forward $full migrate '2.6.11'
    $forwardAfter=[int](Invoke-Mysql $forward "SELECT COUNT(*) FROM flyway_schema_history WHERE success=1" -Raw).Output.Trim()

    if ($upgradeAfter-$upgradeBefore -ne 3) { $failures += 'Upgrade did not apply exactly V2.6.9-V2.6.11' }
    if ($forwardAfter-$forwardBefore -ne 1) { $failures += 'Forward path did not apply exactly V2.6.11' }
    foreach ($noop in @($freshNoop,$upgradeNoop,$forwardNoop)) { if ($noop.Output -notmatch 'No migration necessary') { $failures += 'Second migrate was not no-op' } }

    $fpFresh=Get-Fingerprint $fresh; $fpUpgrade=Get-Fingerprint $upgrade; $fpForward=Get-Fingerprint $forward
    foreach ($scope in @('full','workflow','role')) { if ($fpFresh[$scope] -ne $fpUpgrade[$scope] -or $fpFresh[$scope] -ne $fpForward[$scope]) { $failures += "Fingerprint mismatch: $scope" } }

    $guard=@("case`tresult`texit_code`tbefore_v2611_ddl`tafter_v2611_ddl`tdetail")
    $guard += Test-DirtyGuard $badResolver 'illegal_resolver_version' { param($s) Add-Fixture $s 261200 'lowercase' | Out-Null }
    $guard += Test-DirtyGuard $badContract 'approval_snapshot_contract_mismatch' { param($s) Add-Fixture $s 261300 'ROLE_DIRECTORY_V1' ('a'*64) ('b'*64) ('9'*64) | Out-Null }
    $guard += Test-DirtyGuard $badHash 'invalid_binding_hash_matrix' { param($s)
        $drop=Invoke-Mysql $s 'ALTER TABLE workflow_role_runtime_binding_snapshot DROP CHECK chk_role_runtime_snapshot_hashes'; if($drop.ExitCode-ne0){throw $drop.Output}
        Add-Fixture $s 261400 'ROLE_DIRECTORY_V1' ('a'*64) ('a'*64) ('A'*64) | Out-Null
    }
    $guard += Test-DirtyGuard $badCanonical 'canonical_hash_drift' { param($s) Add-Fixture $s 261500 'ROLE_DIRECTORY_V1' ('a'*64) ('a'*64) ('9'*64) | Out-Null }
    $guard | Set-Content (Join-Path $evidence 'guard.tsv') -Encoding utf8

    $negative=@("name`tresult`tdetail")
    Assert-Rejected $forward 'approval_update' "UPDATE role_runtime_binding_approval SET status='EXPIRED' WHERE id=$($legal.Approval)" ([ref]$negative)
    Assert-Rejected $forward 'approval_delete' "DELETE FROM role_runtime_binding_approval WHERE id=$($legal.Approval)" ([ref]$negative)
    Assert-Rejected $forward 'snapshot_update' "UPDATE workflow_role_runtime_binding_snapshot SET role_code='OTHER' WHERE id=$($legal.Snapshot)" ([ref]$negative)
    Assert-Rejected $forward 'snapshot_delete' "DELETE FROM workflow_role_runtime_binding_snapshot WHERE id=$($legal.Snapshot)" ([ref]$negative)
    Assert-Rejected $forward 'snapshot_invalid_insert' "INSERT INTO workflow_role_runtime_binding_snapshot SELECT id+1000,approval_id,binding_set_id,resolver_binding_id,node_resolver_binding_id,instance_id,definition_version_id,node_id,resolver_code,resolver_version,contract_hash,role_code,organization_id,directory_revision,effective_at,directory_hash,role_rule_hash,candidate_rule_hash,source_evidence_hash,REPEAT('9',64),status,audit_info,created_by,created_time,updated_by,updated_time,deleted,delete_token,remark,version FROM workflow_role_runtime_binding_snapshot WHERE id=$($legal.Snapshot)" ([ref]$negative)
    $negative | Set-Content (Join-Path $evidence 'negative.tsv') -Encoding utf8

    $chain=(Invoke-Mysql $forward "SELECT approval.status,snapshot.status,approval.contract_hash=snapshot.contract_hash,snapshot.binding_hash='$($legal.BindingHash)' FROM role_runtime_binding_approval approval JOIN workflow_role_runtime_binding_snapshot snapshot ON snapshot.approval_id=approval.id WHERE snapshot.id=$($legal.Snapshot)" -Raw).Output.Trim()
    if ($chain -notmatch '^APPROVED\s+FROZEN\s+1\s+1$') { $failures += "Legal evidence chain mismatch: $chain" }
    $compat=(Invoke-Mysql $forward "SELECT (SELECT COUNT(*) FROM workflow_task WHERE assignment_mode='DIRECT'),(SELECT COUNT(*) FROM workflow_task_candidate_pool),(SELECT COUNT(*) FROM workflow_task_claim),(SELECT COUNT(*) FROM workflow_role_runtime_binding_snapshot WHERE resolver_code='ROLE_DIRECTORY')" -Raw).Output.Trim()

    $concurrent=Add-Fixture $forward 261600 'ROLE_DIRECTORY_V1' ('a'*64) ('a'*64) '' $false
    $ids=$concurrent.Ids; $baseInsert="INSERT INTO workflow_role_runtime_binding_snapshot(id,approval_id,binding_set_id,resolver_binding_id,node_resolver_binding_id,instance_id,definition_version_id,node_id,resolver_code,resolver_version,contract_hash,role_code,organization_id,directory_revision,effective_at,directory_hash,role_rule_hash,candidate_rule_hash,source_evidence_hash,binding_hash,status,audit_info,created_by,updated_by,deleted,delete_token,version) VALUES({ID},$($concurrent.Approval),$($ids[0]),$($ids[1]),$($ids[2]),$($ids[3]),$($ids[4]),$($ids[5]),'ROLE_DIRECTORY','ROLE_DIRECTORY_V1',REPEAT('a',64),'REVIEWER','ORG-261600',12,'2026-08-13 02:03:04.005',REPEAT('b',64),REPEAT('c',64),REPEAT('d',64),REPEAT('e',64),'$($concurrent.BindingHash)','FROZEN','concurrent','acceptance','acceptance',0,0,0)"
    $concurrentWorker={param($client,$port,$statement)
        $output=&$client --protocol=TCP --host=127.0.0.1 "--port=$port" --user=root --database=enterprise_platform "--execute=$statement" 2>&1
        [pscustomobject]@{ExitCode=$LASTEXITCODE;Output=($output -join "`n")}
    }
    $job1=Start-Job -ScriptBlock $concurrentWorker -ArgumentList $mysql,$forward.Port,$baseInsert.Replace('{ID}','261608')
    $job2=Start-Job -ScriptBlock $concurrentWorker -ArgumentList $mysql,$forward.Port,$baseInsert.Replace('{ID}','261609')
    Wait-Job $job1,$job2 | Out-Null
    $result1=Receive-Job $job1; $result2=Receive-Job $job2
    Remove-Job $job1,$job2 -Force
    $concurrentText="$($result1.Output)`n$($result2.Output)"
    $successCount=@(@($result1.ExitCode,$result2.ExitCode) | Where-Object { $_ -eq 0 }).Count
    if ($successCount -ne 1 -or $concurrentText -match 'ERROR 1213|ERROR 1205') { $failures += 'Concurrent Snapshot insert was not a clean single winner' }

    $checksums=(Invoke-Mysql $fresh "SELECT version,checksum FROM flyway_schema_history WHERE version IN ('2.6.9','2.6.10','2.6.11') ORDER BY installed_rank" -Raw).Output.Trim()
    foreach ($entry in $expectedSha.GetEnumerator()) { $actual=(Get-FileHash (Join-Path $repoMigrations $entry.Key) -Algorithm SHA256).Hash.ToLowerInvariant(); if($actual-ne$entry.Value){$failures+="SHA drift: $($entry.Key)"} }
    $sumLines=Get-Content (Join-Path $repoMigrations 'SHA256SUMS'); $shaPass=0
    foreach($line in $sumLines){if($line-match'^([0-9a-f]{64})\s+(.+)$'){if((Get-FileHash (Join-Path $repoMigrations $matches[2]) -Algorithm SHA256).Hash.ToLowerInvariant()-eq$matches[1]){$shaPass++}}}
    if($shaPass-ne$sumLines.Count){$failures+='Migration SHA list mismatch'}

    $summary=[ordered]@{result=$(if($failures.Count-eq0){'PASS'}else{'FAIL'});output_root=$OutputRoot;mysql_version=((&$mysqld --version)-join' ');flyway_version='13.0.0';java_version='21.0.12';maven_version='3.9.9';checksums=$checksums;full_schema_fingerprint=$fpFresh.full;workflow_schema_fingerprint=$fpFresh.workflow;role_runtime_schema_fingerprint=$fpFresh.role;guard_results=$guard[1..($guard.Count-1)];negative_results=$negative[1..($negative.Count-1)];legal_chain=$chain;compatibility=$compat;concurrent_exit_codes="$($result1.ExitCode),$($result2.ExitCode)";concurrent_errors=$concurrentText;sha="$shaPass/$($sumLines.Count)";failures=$failures}
    $summary | ConvertTo-Json -Depth 6 | Set-Content (Join-Path $evidence 'summary.json') -Encoding utf8
    $summary | ConvertTo-Json -Depth 6
    if($summary.result-ne'PASS'){throw($failures-join'; ')}
}
finally {
    foreach($process in $servers){if($process -and -not $process.HasExited){Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue}}
    Get-CimInstance Win32_Process -Filter "Name='mysqld.exe'" | Where-Object { $_.CommandLine -like "*$OutputRoot*" } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
}

param(
    [string]$OutputRoot = "D:\codex-validation-v2610-$((Get-Date).ToString('yyyyMMdd-HHmmss'))",
    [int]$FreshPort = 35301,
    [int]$UpgradePort = 35302,
    [int]$ForwardPort = 35303,
    [int]$GuardContractPort = 35304,
    [int]$GuardHashPort = 35305
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
$v2610File = Join-Path $repoMigrations 'V2.6.10__harden_role_runtime_persistence_integrity.sql'
$baselineFiles = @(
    '01_database.sql','02_sys.sql','03_hr.sql','04_party.sql','05_project.sql',
    '06_operation.sql','07_investment.sql','08_data_asset.sql','09_risk.sql',
    '10_init_data.sql','11_sprint_1_user_permissions.sql','12_sprint_1_org_permissions.sql',
    '13_sprint_1_role_permissions.sql','14_sprint_1_rbac_acceptance.sql',
    '15_sprint_1_menu_center.sql','16_sprint_1_log_center.sql','V1.1.0__investment_data_risk_bi.sql'
)
foreach ($asset in @($mysqld,$mysql,$mysqladmin,$flyway,$fingerprintSql,$v2610File)) {
    if (-not (Test-Path -LiteralPath $asset)) { throw "Missing validation asset: $asset" }
}
if (Test-Path -LiteralPath $OutputRoot) { throw "Output root exists: $OutputRoot" }

$evidence = Join-Path $OutputRoot 'evidence'
$fullMigrations = Join-Path $OutputRoot 'migrations-full'
$v268Migrations = Join-Path $OutputRoot 'migrations-v268'
$v269Migrations = Join-Path $OutputRoot 'migrations-v269'
$baselineSource = Join-Path $OutputRoot 'baseline'
New-Item -ItemType Directory -Path $evidence,$fullMigrations,$v268Migrations,$v269Migrations,$baselineSource -Force | Out-Null
Get-ChildItem $repoMigrations -Filter 'V*.sql' -File | Copy-Item -Destination $fullMigrations
$v2610ExecutionFile = Join-Path $fullMigrations 'V2.6.10__harden_role_runtime_persistence_integrity.sql'
Get-ChildItem $repoMigrations -Filter 'V*.sql' -File |
    Where-Object { $_.Name -notin @('V2.6.9__create_role_runtime_snapshot.sql','V2.6.10__harden_role_runtime_persistence_integrity.sql') } |
    Copy-Item -Destination $v268Migrations
Get-ChildItem $repoMigrations -Filter 'V*.sql' -File |
    Where-Object Name -ne 'V2.6.10__harden_role_runtime_persistence_integrity.sql' |
    Copy-Item -Destination $v269Migrations
foreach ($name in $baselineFiles) { Copy-Item (Join-Path $workspace "database\mysql\$name") (Join-Path $baselineSource $name) }

$servers=@(); $failures=@()

function New-IsolatedMysql([string]$Name,[int]$Port) {
    $active=Get-NetTCPConnection -LocalPort $Port -ErrorAction SilentlyContinue | Where-Object State -ne 'TimeWait'
    if($active){throw "Port in use: $Port"}
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
    if($LASTEXITCODE-ne0){throw "Initialize failed: $Name"}
    $process=Start-Process $mysqld -ArgumentList "--defaults-file=$ini" -WindowStyle Hidden -PassThru
    $script:servers += $process
    foreach($attempt in 1..100){
        $old=$ErrorActionPreference;$ErrorActionPreference='Continue'
        & $mysqladmin --protocol=TCP --host=127.0.0.1 --port=$Port --user=root ping 2>$null|Out-Null
        $code=$LASTEXITCODE;$ErrorActionPreference=$old
        if($code-eq0){return @{Name=$Name;Port=$Port;Root=$root}}
        Start-Sleep -Milliseconds 300
    }
    throw "$Name did not become ready"
}

function Invoke-Mysql($Server,[string]$Sql,[switch]$Raw,[string]$Database='enterprise_platform'){
    $args=@('--protocol=TCP','--host=127.0.0.1',"--port=$($Server.Port)",'--user=root')
    if($Database){$args+="--database=$Database"};if($Raw){$args+=@('--batch','--raw','--skip-column-names')}
    $old=$ErrorActionPreference;$ErrorActionPreference='Continue';$out=&$mysql @args "--execute=$Sql" 2>&1;$code=$LASTEXITCODE;$ErrorActionPreference=$old
    return @{ExitCode=$code;Output=($out-join"`n")}
}

function Invoke-MysqlFile($Server,[string]$File,[string]$Database=''){
    $args=@('--protocol=TCP','--host=127.0.0.1',"--port=$($Server.Port)",'--user=root','--default-character-set=utf8mb4')
    if($Database){$args+="--database=$Database"};$old=$ErrorActionPreference;$ErrorActionPreference='Continue'
    $out=&$mysql @args "--execute=source $((Resolve-Path $File).Path.Replace('\','/'))" 2>&1;$code=$LASTEXITCODE;$ErrorActionPreference=$old
    return @{ExitCode=$code;Output=($out-join"`n")}
}

function Initialize-Baseline($Server){foreach($name in $baselineFiles){$r=Invoke-MysqlFile $Server (Join-Path $baselineSource $name) $(if($name-eq'01_database.sql'){''}else{'enterprise_platform'});if($r.ExitCode-ne0){throw "Baseline failed: $name $($r.Output)"}}}

function Invoke-Flyway($Server,[string]$Location,[string]$Command,[string]$Target=''){
    $args=@("-url=jdbc:mysql://127.0.0.1:$($Server.Port)/enterprise_platform",'-user=root','-password=',"-locations=filesystem:$($Location.Replace('\','/'))",'-baselineOnMigrate=true','-baselineVersion=2.0.0','-validateMigrationNaming=true','-cleanDisabled=true','-connectRetries=10')
    if($Target){$args+="-target=$Target"};$args+=$Command;$log=Join-Path $evidence "$($Server.Name)-$Command$(if($Target){"-$Target"})-$([DateTime]::UtcNow.Ticks).log"
    $sw=[Diagnostics.Stopwatch]::StartNew();$old=$ErrorActionPreference;$ErrorActionPreference='Continue';$out=&$flyway @args 2>&1;$code=$LASTEXITCODE;$ErrorActionPreference=$old;$sw.Stop();$out|Set-Content $log -Encoding utf8;"elapsed_ms=$($sw.Elapsed.TotalMilliseconds)"|Add-Content $log
    if($code-ne0){throw "Flyway $Command failed for $($Server.Name): $($out-join' ')"};return @{Output=($out-join"`n");ElapsedMs=$sw.Elapsed.TotalMilliseconds}
}

function Write-Fingerprint($Server){
    $args=@('--protocol=TCP','--host=127.0.0.1',"--port=$($Server.Port)",'--user=root','--database=enterprise_platform','--batch','--raw','--skip-column-names')
    $rows=Get-Content $fingerprintSql -Raw|&$mysql @args;if($LASTEXITCODE-ne0){throw'base fingerprint failed'}
    $triggerSql="SELECT CONCAT('TRIGGER|',trigger_name,'|',event_manipulation,'|',event_object_table,'|',action_timing,'|',REPLACE(REPLACE(REPLACE(action_statement,'|','\\|'),CHAR(13),' '),CHAR(10),' ')) FROM information_schema.triggers WHERE trigger_schema=DATABASE() ORDER BY trigger_name"
    $triggers=&$mysql @args "--execute=$triggerSql";if($LASTEXITCODE-ne0){throw'trigger fingerprint failed'};$all=@($rows)+@($triggers)
    $workflow=$all|Where-Object{$_-match'^\w+\|workflow_'-or$_-match'^\w+\|role_runtime_'-or$_-match'^TRIGGER\|.*\|(workflow_|role_runtime_)'}
    $role=$all|Where-Object{$_-match'^\w+\|role_runtime_binding_approval\|'-or$_-match'^\w+\|workflow_role_runtime_binding_snapshot\|'-or$_-match'^TRIGGER\|.*\|(role_runtime_binding_approval|workflow_role_runtime_binding_snapshot)'}
    $result=@{};foreach($pair in @(@('full',$all),@('workflow',$workflow),@('role',$role))){$file=Join-Path $evidence "$($Server.Name)-$($pair[0])-schema.txt";[IO.File]::WriteAllText($file,(($pair[1]-join"`n")+"`n"),[Text.UTF8Encoding]::new($false));$result[$pair[0]]=(Get-FileHash $file -Algorithm SHA256).Hash.ToLowerInvariant()};return $result
}

function Add-Fixture($Server,[long]$Base,[string]$ApprovalStatus='APPROVED',[string]$ApprovalContract='a',[string]$SnapshotContract='a',[string]$BindingHash='9'){
    $d=$Base;$v=$Base+1;$n=$Base+2;$i=$Base+3;$set=$Base+4;$binding=$Base+5;$nodeBinding=$Base+6;$approval=$Base+7;$snapshot=$Base+8
    $decision=$(if($ApprovalStatus-eq'APPROVED'){"'approver',NOW(3),NULL"}elseif($ApprovalStatus-eq'REJECTED'){"NULL,NULL,'rejected'"}else{"NULL,NULL,NULL"})
    $sql=@"
INSERT INTO workflow_definition(id,definition_code,definition_name,business_type,enterprise_id,owner_org_id,status,current_version_id,created_by,updated_by,deleted,delete_token,version) VALUES($d,'WF_$Base','ROLE validation','ACCEPTANCE',2610,2610,'DRAFT',NULL,'acceptance','acceptance',0,0,0);
INSERT INTO workflow_version(id,definition_id,version_no,status,schema_version,engine_mode,content_hash_algorithm,content_hash,effective_from,published_by,published_time,created_by,updated_by,deleted,delete_token,version) VALUES($v,$d,1,'PUBLISHED','2.0','MULTI_NODE_LINEAR_V1','GRAPH_V2_SHA256',REPEAT('1',64),NOW(3),2610,NOW(3),'acceptance','acceptance',0,0,0);
INSERT INTO workflow_node(id,version_id,node_code,node_name,node_type,node_order,governance_node_type,approval_mode,assignment_rule_type,assignment_rule_config,withdraw_allowed,enabled,created_by,updated_by,deleted,delete_token,version) VALUES($n,$v,'ROLE_NODE_$Base','ROLE Node','APPROVAL',1,'GENERAL_APPROVAL','SINGLE','USER','{"userId":2610}',0,1,'acceptance','acceptance',0,0,0);
UPDATE workflow_definition SET status='ACTIVE',current_version_id=$v,version=1 WHERE id=$d;
INSERT INTO workflow_instance(id,instance_no,definition_id,version_id,definition_code_snapshot,definition_version_no,definition_content_hash_snapshot,resolver_code,resolver_version,resolver_contract_hash,business_type,business_id,business_key,enterprise_id,attempt_no,initiator_user_id,initiator_org_id,current_node_id,engine_mode,content_hash_algorithm_snapshot,current_node_execution_id,status,idempotency_key,request_hash,event_sequence,trace_id,started_time,created_by,updated_by,deleted,delete_token,version) VALUES($i,'WFI-$Base',$d,$v,'WF_$Base',1,REPEAT('1',64),'EXPLICIT_USER','EXPLICIT_USER_V1','3166103d7697a6f2e46932214b0447310c69d6b624f6d92a282e9ef3ab496853','ACCEPTANCE','$i','ACCEPTANCE:$i',2610,1,2610,2610,$n,'MULTI_NODE_LINEAR_V1','GRAPH_V2_SHA256',NULL,'RUNNING','fixture-$Base',REPEAT('2',64),0,'trace-$Base',NOW(3),'acceptance','acceptance',0,0,0);
INSERT INTO workflow_instance_resolver_binding_set(id,instance_id,definition_id,definition_version_id,manifest_version,manifest_hash,binding_count,binding_status,frozen_time,audit_info,created_by,updated_by,deleted,delete_token,version) VALUES($set,$i,$d,$v,'MULTI_RESOLVER_V1',REPEAT('3',64),1,'FROZEN',NOW(3),'acceptance','acceptance','acceptance',0,0,0);
INSERT INTO workflow_instance_resolver_binding(id,binding_set_id,instance_id,definition_version_id,resolver_code,resolver_version,strategy_type,resolver_mode,contract_hash,rule_hash,binding_status,frozen_time,audit_info,created_by,updated_by,deleted,delete_token,version) VALUES($binding,$set,$i,$v,'ROLE_DIRECTORY','ROLE_DIRECTORY_V1','ROLE','CANDIDATE_POOL',REPEAT('a',64),REPEAT('b',64),'FROZEN',NOW(3),'acceptance','acceptance','acceptance',0,0,0);
INSERT INTO workflow_node_resolver_binding_snapshot(id,binding_set_id,resolver_binding_id,instance_id,definition_version_id,node_id,node_code_snapshot,strategy_type,resolver_mode,target_type,target_value_snapshot,rule_version,rule_snapshot,rule_hash,node_binding_hash,binding_status,frozen_time,audit_info,created_by,updated_by,deleted,delete_token,version) VALUES($nodeBinding,$set,$binding,$i,$v,$n,'ROLE_NODE_$Base','ROLE','CANDIDATE_POOL','ROLE','{"roleCode":"REVIEWER"}','RULE_V1','{"roleCode":"REVIEWER"}',REPEAT('b',64),REPEAT('c',64),'FROZEN',NOW(3),'acceptance','acceptance','acceptance',0,0,0);
INSERT INTO role_runtime_binding_approval(id,proposal_hash,eligibility_hash,resolver_code,resolver_version,contract_hash,status,approved_by,approved_at,reject_reason,audit_info,created_by,created_time,updated_by,updated_time,deleted,delete_token,version) VALUES($approval,REPEAT('1',64),REPEAT('2',64),'ROLE_DIRECTORY','ROLE_DIRECTORY_V1',REPEAT('$ApprovalContract',64),'$ApprovalStatus',$decision,'acceptance','acceptance',NOW(3),'acceptance',NOW(3),0,0,0);
INSERT INTO workflow_role_runtime_binding_snapshot(id,approval_id,binding_set_id,resolver_binding_id,node_resolver_binding_id,instance_id,definition_version_id,node_id,resolver_code,resolver_version,contract_hash,role_code,organization_id,directory_revision,effective_at,directory_hash,role_rule_hash,candidate_rule_hash,source_evidence_hash,binding_hash,status,audit_info,created_by,created_time,updated_by,updated_time,deleted,delete_token,version) VALUES($snapshot,$approval,$set,$binding,$nodeBinding,$i,$v,$n,'ROLE_DIRECTORY','ROLE_DIRECTORY_V1',REPEAT('$SnapshotContract',64),'REVIEWER','ORG-001',12,NOW(3),REPEAT('b',64),REPEAT('c',64),REPEAT('d',64),REPEAT('e',64),REPEAT('$BindingHash',64),'FROZEN','acceptance','acceptance',NOW(3),'acceptance',NOW(3),0,0,0);
"@
    $r=Invoke-Mysql $Server $sql;if($r.ExitCode-ne0){throw "Fixture failed: $($r.Output)"};return @{Approval=$approval;Snapshot=$snapshot;Instance=$i}
}

function Expect-Rejected($Server,[string]$Name,[string]$Sql,[ref]$Log,[string]$Expected){$r=Invoke-Mysql $Server "START TRANSACTION; $Sql; ROLLBACK;";$pass=$r.ExitCode-ne0;$code='';$state='';if($r.Output-match'ERROR\s+(\d+)\s+\(([0-9A-Z]+)\)'){$code=$matches[1];$state=$matches[2]};$Log.Value+="$Name`t$(if($pass){'PASS'}else{'FAIL'})`t$code`t$state`t$Expected`t$($r.Output.Replace("`n",' '))";if(-not$pass){$script:failures+="Negative accepted: $Name"}}

function Permanent-Guard-Count($Server){return(Invoke-Mysql $Server "SELECT (SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND constraint_name IN ('chk_role_runtime_approval_resolver_identity','chk_role_runtime_snapshot_resolver_identity'))+(SELECT COUNT(*) FROM information_schema.triggers WHERE trigger_schema=DATABASE() AND trigger_name LIKE 'trg_%role_runtime%')" -Raw).Output.Trim()}

try{
    $fresh=New-IsolatedMysql 'fresh' $FreshPort;$upgrade=New-IsolatedMysql 'upgrade' $UpgradePort;$forward=New-IsolatedMysql 'forward' $ForwardPort;$guardContract=New-IsolatedMysql 'guard-contract' $GuardContractPort;$guardHash=New-IsolatedMysql 'guard-hash' $GuardHashPort
    foreach($s in @($fresh,$upgrade,$forward,$guardContract,$guardHash)){Initialize-Baseline $s}

    $freshMigrate=Invoke-Flyway $fresh $fullMigrations migrate '2.6.10';Invoke-Flyway $fresh $fullMigrations info|Out-Null;Invoke-Flyway $fresh $fullMigrations validate|Out-Null;$freshNoop=Invoke-Flyway $fresh $fullMigrations migrate '2.6.10'
    Invoke-Flyway $upgrade $v268Migrations migrate '2.6.8'|Out-Null;$upgradeBefore=[int](Invoke-Mysql $upgrade "SELECT COUNT(*) FROM flyway_schema_history WHERE success=1" -Raw).Output.Trim();$upgradeMigrate=Invoke-Flyway $upgrade $fullMigrations migrate '2.6.10';Invoke-Flyway $upgrade $fullMigrations validate|Out-Null;$upgradeNoop=Invoke-Flyway $upgrade $fullMigrations migrate '2.6.10';$upgradeAfter=[int](Invoke-Mysql $upgrade "SELECT COUNT(*) FROM flyway_schema_history WHERE success=1" -Raw).Output.Trim()
    Invoke-Flyway $forward $v269Migrations migrate '2.6.9'|Out-Null;$historyBefore=(Invoke-Mysql $forward "SELECT COUNT(*),SUM(success=0),SUM(version='2.6.9') FROM flyway_schema_history" -Raw).Output.Trim();$forwardMigrate=Invoke-Flyway $forward $fullMigrations migrate '2.6.10';Invoke-Flyway $forward $fullMigrations validate|Out-Null;$forwardNoop=Invoke-Flyway $forward $fullMigrations migrate '2.6.10';$historyAfter=(Invoke-Mysql $forward "SELECT COUNT(*),SUM(success=0),SUM(version='2.6.9'),SUM(version='2.6.10') FROM flyway_schema_history" -Raw).Output.Trim()
    if($upgradeAfter-$upgradeBefore-ne2){$failures+="Upgrade applied $($upgradeAfter-$upgradeBefore), expected 2"};foreach($noop in @($freshNoop,$upgradeNoop,$forwardNoop)){if($noop.Output-notmatch'No migration necessary'){$failures+='Second migrate was not no-op'}}

    $checksum269=[int](Invoke-Mysql $fresh "SELECT checksum FROM flyway_schema_history WHERE version='2.6.9'" -Raw).Output.Trim();$checksum2610=[int](Invoke-Mysql $fresh "SELECT checksum FROM flyway_schema_history WHERE version='2.6.10'" -Raw).Output.Trim()
    $fpFresh=Write-Fingerprint $fresh;$fpUpgrade=Write-Fingerprint $upgrade;$fpForward=Write-Fingerprint $forward;foreach($scope in @('full','workflow','role')){if($fpFresh[$scope]-ne$fpUpgrade[$scope]-or$fpFresh[$scope]-ne$fpForward[$scope]){$failures+="Fingerprint mismatch: $scope"}}

    $fixture=Add-Fixture $fresh 261000
    $negative=@("name`tresult`tmysql_code`tsqlstate`texpected`tdetail")
    Expect-Rejected $fresh '01_null_resolver_version' "INSERT INTO role_runtime_binding_approval(id,proposal_hash,eligibility_hash,resolver_code,resolver_version,contract_hash,status,audit_info,deleted,delete_token,version) VALUES(261100,REPEAT('3',64),REPEAT('4',64),'ROLE_DIRECTORY',NULL,REPEAT('a',64),'PENDING','x',0,0,0)" ([ref]$negative) 'NOT NULL'
    Expect-Rejected $fresh '02_blank_resolver_version' "INSERT INTO role_runtime_binding_approval(id,proposal_hash,eligibility_hash,resolver_code,resolver_version,contract_hash,status,audit_info,deleted,delete_token,version) VALUES(261101,REPEAT('3',64),REPEAT('4',64),'ROLE_DIRECTORY','',REPEAT('a',64),'PENDING','x',0,0,0)" ([ref]$negative) 'resolver identity CHECK'
    Expect-Rejected $fresh '03_snapshot_update' "UPDATE workflow_role_runtime_binding_snapshot SET role_code='CHANGED' WHERE id=$($fixture.Snapshot)" ([ref]$negative) 'ROLE_RUNTIME_SNAPSHOT_IMMUTABLE'
    Expect-Rejected $fresh '04_snapshot_delete' "DELETE FROM workflow_role_runtime_binding_snapshot WHERE id=$($fixture.Snapshot)" ([ref]$negative) 'ROLE_RUNTIME_SNAPSHOT_IMMUTABLE'
    foreach($field in @("status='EXPIRED'","approved_by='changed'","approved_at=NOW(3)","proposal_hash=REPEAT('3',64)")){Expect-Rejected $fresh ("approval_update_"+$field.Split('=')[0]) "UPDATE role_runtime_binding_approval SET $field WHERE id=$($fixture.Approval)" ([ref]$negative) 'ROLE_RUNTIME_APPROVAL_APPEND_ONLY'}
    $pendingSql="INSERT INTO role_runtime_binding_approval(id,proposal_hash,eligibility_hash,resolver_code,resolver_version,contract_hash,status,audit_info,deleted,delete_token,version) VALUES(261110,REPEAT('4',64),REPEAT('5',64),'ROLE_DIRECTORY','ROLE_DIRECTORY_V1',REPEAT('a',64),'PENDING','x',0,0,0); INSERT INTO workflow_role_runtime_binding_snapshot SELECT 261111,261110,binding_set_id,resolver_binding_id,node_resolver_binding_id,instance_id,definition_version_id,node_id,resolver_code,resolver_version,contract_hash,role_code,organization_id,directory_revision,effective_at,directory_hash,role_rule_hash,candidate_rule_hash,source_evidence_hash,REPEAT('8',64),status,audit_info,created_by,created_time,updated_by,updated_time,deleted,delete_token,remark,version FROM workflow_role_runtime_binding_snapshot WHERE id=$($fixture.Snapshot)"
    Expect-Rejected $fresh '09_pending_approval_snapshot' $pendingSql ([ref]$negative) 'ROLE_RUNTIME_APPROVAL_NOT_APPROVED'
    $mismatchCases=@(
        @('10_resolver_code_mismatch',261120,"'OTHER','ROLE_DIRECTORY_V1',REPEAT('a',64)"),
        @('11_resolver_version_mismatch',261121,"'ROLE_DIRECTORY','OTHER_V1',REPEAT('a',64)"),
        @('12_contract_mismatch',261122,"'ROLE_DIRECTORY','ROLE_DIRECTORY_V1',REPEAT('b',64)")
    )
    foreach($case in $mismatchCases){$sql="INSERT INTO workflow_role_runtime_binding_snapshot SELECT $($case[1]),approval_id,binding_set_id,resolver_binding_id,node_resolver_binding_id,instance_id,definition_version_id,node_id,$($case[2]),role_code,organization_id,directory_revision,effective_at,directory_hash,role_rule_hash,candidate_rule_hash,source_evidence_hash,REPEAT('7',64),status,audit_info,created_by,created_time,updated_by,updated_time,deleted,delete_token,remark,version FROM workflow_role_runtime_binding_snapshot WHERE id=$($fixture.Snapshot)";Expect-Rejected $fresh $case[0] $sql ([ref]$negative) 'ROLE_RUNTIME_APPROVAL_CONTRACT_MISMATCH'}
    foreach($case in @(@('13_uppercase_hash',"REPEAT('A',64)"),@('14_nonhex_hash',"REPEAT('z',64)"),@('15_short_hash',"REPEAT('a',63)"))){$sql="INSERT INTO role_runtime_binding_approval(id,proposal_hash,eligibility_hash,resolver_code,resolver_version,contract_hash,status,audit_info,deleted,delete_token,version) VALUES($([Math]::Abs($case[0].GetHashCode())+300000),REPEAT('6',64),REPEAT('7',64),'ROLE_DIRECTORY','ROLE_DIRECTORY_V1',$($case[1]),'PENDING','x',0,0,0)";Expect-Rejected $fresh $case[0] $sql ([ref]$negative) 'hash CHECK'}
    $negative|Set-Content (Join-Path $evidence 'negative.tsv') -Encoding utf8

    $compat=(Invoke-Mysql $fresh "SELECT (SELECT COUNT(*) FROM workflow_instance WHERE resolver_code='EXPLICIT_USER' AND resolver_version='EXPLICIT_USER_V1'),(SELECT COUNT(*) FROM workflow_task WHERE assignment_mode='DIRECT'),(SELECT COUNT(*) FROM workflow_task_candidate_pool),(SELECT COUNT(*) FROM workflow_task_claim),(SELECT COUNT(*) FROM workflow_task_candidate_member WHERE source_ref_snapshot LIKE 'ROLE_DIRECTORY:%')" -Raw).Output.Trim()

    # Guard path 1: contract mismatch is legal in V2.6.9 and must be stopped before permanent DDL.
    Invoke-Flyway $guardContract $v269Migrations migrate '2.6.9'|Out-Null;Add-Fixture $guardContract 262000 'APPROVED' 'a' 'b'|Out-Null;$beforeContract=Permanent-Guard-Count $guardContract;$guardContractResult=Invoke-MysqlFile $guardContract $v2610ExecutionFile 'enterprise_platform';$afterContract=Permanent-Guard-Count $guardContract
    $guardContractPass=$guardContractResult.ExitCode-ne0-and$guardContractResult.Output-match'chk_role_runtime_v2610_guard|3819'-and$beforeContract-eq'0'-and$afterContract-eq'0';if(-not$guardContractPass){$failures+='Guard did not stop contract mismatch before permanent DDL'}

    # Guard path 2: temporarily remove only V2.6.9 hash CHECK to simulate a historical corrupted row.
    Invoke-Flyway $guardHash $v269Migrations migrate '2.6.9'|Out-Null
    $drop=Invoke-Mysql $guardHash "ALTER TABLE workflow_role_runtime_binding_snapshot DROP CHECK chk_role_runtime_snapshot_hashes";if($drop.ExitCode-ne0){throw $drop.Output};Add-Fixture $guardHash 263000 'APPROVED' 'a' 'a' 'z'|Out-Null;$beforeHash=Permanent-Guard-Count $guardHash;$guardHashResult=Invoke-MysqlFile $guardHash $v2610ExecutionFile 'enterprise_platform';$afterHash=Permanent-Guard-Count $guardHash
    $guardHashPass=$guardHashResult.ExitCode-ne0-and$guardHashResult.Output-match'chk_role_runtime_v2610_guard|3819'-and$beforeHash-eq'0'-and$afterHash-eq'0';if(-not$guardHashPass){$failures+='Guard did not stop invalid Snapshot hash before permanent DDL'}
    @("case`tresult`texit_code`tbefore_permanent`tafter_permanent`tdetail","contract_mismatch`t$(if($guardContractPass){'PASS'}else{'FAIL'})`t$($guardContractResult.ExitCode)`t$beforeContract`t$afterContract`t$($guardContractResult.Output.Replace("`n",' '))","invalid_snapshot_hash`t$(if($guardHashPass){'PASS'}else{'FAIL'})`t$($guardHashResult.ExitCode)`t$beforeHash`t$afterHash`t$($guardHashResult.Output.Replace("`n",' '))")|Set-Content (Join-Path $evidence 'guard.tsv') -Encoding utf8

    $sha269=(Get-FileHash (Join-Path $repoMigrations 'V2.6.9__create_role_runtime_snapshot.sql') -Algorithm SHA256).Hash.ToLowerInvariant();$sha2610=(Get-FileHash $v2610File -Algorithm SHA256).Hash.ToLowerInvariant();if($sha269-ne'fbe41e2a354e616ee4b369c57980009057c75ced46a2eb9e48d1eb8354beb0d2'-or$sha2610-ne'2fad3f67a4a487568f5fc29f40cec73894d89fff65564212a75be98da186f7d6'){$failures+='Migration SHA drift'}
    $negativePass=@($negative|Select-Object -Skip 1|Where-Object{$_-match"`tPASS`t"}).Count
    $summary=[ordered]@{output_root=$OutputRoot;mysql_version=((&$mysqld --version)-join' ');flyway_version='13.0.0';java_version='21.0.12';maven_version='3.9.9';v269_checksum=$checksum269;v2610_checksum=$checksum2610;v269_sha256=$sha269;v2610_sha256=$sha2610;fresh_history=(Invoke-Mysql $fresh "SELECT COUNT(*),SUM(success=0),SUM(version='2.6.9'),SUM(version='2.6.10') FROM flyway_schema_history" -Raw).Output.Trim();upgrade_applied_count=$($upgradeAfter-$upgradeBefore);forward_history_before=$historyBefore;forward_history_after=$historyAfter;full_schema_fingerprint=$fpFresh.full;workflow_schema_fingerprint=$fpFresh.workflow;role_runtime_schema_fingerprint=$fpFresh.role;negative_tests="$negativePass/$($negative.Count-1)";guard_contract=$(if($guardContractPass){'PASS'}else{'FAIL'});guard_hash=$(if($guardHashPass){'PASS'}else{'FAIL'});compatibility=$compat;failures=$failures;result=$(if($failures.Count-eq0-and$negativePass-eq$negative.Count-1){'PASS'}else{'FAIL'})}
    $summary|ConvertTo-Json -Depth 5|Set-Content (Join-Path $evidence 'summary.json') -Encoding utf8;$summary|ConvertTo-Json -Depth 5;if($summary.result-ne'PASS'){throw($failures-join'; ')}
}
finally{foreach($p in $servers){if($p-and-not$p.HasExited){Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue}};Get-CimInstance Win32_Process -Filter "Name='mysqld.exe'"|Where-Object{$_.CommandLine-like"*$OutputRoot*"}|ForEach-Object{Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue}}

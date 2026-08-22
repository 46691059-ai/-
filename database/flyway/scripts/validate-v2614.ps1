param(
    [string]$OutputRoot = "D:\codex-validation-v2614-$((Get-Date).ToString('yyyyMMdd-HHmmss'))",
    [int]$BasePort = 36420
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
$expectedSha = 'a232501633eb86d98374b99859f43b092ed5692461fa5e673924d02d0e1ca442'
$targetTables = @('workflow_role_binding_promotion',
    'workflow_role_binding_candidate_snapshot','workflow_role_binding_snapshot_event')

foreach ($asset in @($mysqld,$mysql,$mysqladmin,$flyway,$fingerprintSql)) {
    if (-not (Test-Path -LiteralPath $asset)) { throw "Missing validation asset: $asset" }
}
if (Test-Path -LiteralPath $OutputRoot) { throw "Output root already exists: $OutputRoot" }
if (-not $OutputRoot.StartsWith('D:\codex-validation-v2614-')) {
    throw 'Output root must be an explicit V2.6.14 disposable path'
}
$v2614File = Join-Path $repoMigrations 'V2.6.14__create_role_runtime_binding_persistence_foundation.sql'
$actualSha = (Get-FileHash $v2614File -Algorithm SHA256).Hash.ToLowerInvariant()
if ($actualSha -ne $expectedSha) { throw 'V2.6.14 SHA-256 drift before validation' }

$evidence = Join-Path $OutputRoot 'evidence'
$baselineCopy = Join-Path $OutputRoot 'baseline'
$full = Join-Path $OutputRoot 'migrations-full'
$to2613 = Join-Path $OutputRoot 'migrations-to-2613'
New-Item -ItemType Directory -Path $evidence,$baselineCopy,$full,$to2613 -Force | Out-Null
foreach ($name in $baselineFiles) { Copy-Item (Join-Path $baselineSource $name) (Join-Path $baselineCopy $name) }
Get-ChildItem $repoMigrations -Filter 'V*.sql' -File | Copy-Item -Destination $full
Get-ChildItem $repoMigrations -Filter 'V*.sql' -File |
    Where-Object Name -ne 'V2.6.14__create_role_runtime_binding_persistence_foundation.sql' |
    Copy-Item -Destination $to2613

$servers = @()
$failures = [System.Collections.Generic.List[string]]::new()
$checks = [System.Collections.Generic.List[string]]::new()

function New-IsolatedMysql([string]$Name,[int]$Port) {
    if (Get-NetTCPConnection -LocalPort $Port -ErrorAction SilentlyContinue |
            Where-Object State -ne 'TimeWait') { throw "Port in use: $Port" }
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
        if ($code -eq 0) { return @{Name=$Name;Port=$Port;Root=$root;Process=$process} }
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
    $args=@('--protocol=TCP','--host=127.0.0.1',"--port=$($Server.Port)",
        '--user=root','--default-character-set=utf8mb4')
    if ($Database) { $args += "--database=$Database" }
    $old=$ErrorActionPreference; $ErrorActionPreference='Continue'
    $out=&$mysql @args "--execute=source $((Resolve-Path $File).Path.Replace('\','/'))" 2>&1
    $code=$LASTEXITCODE; $ErrorActionPreference=$old
    if ($code -ne 0) { throw "Baseline failed: $File $($out -join ' ')" }
}

function Initialize-Baseline($Server) {
    foreach ($name in $baselineFiles) {
        Invoke-MysqlFile $Server (Join-Path $baselineCopy $name) $(if($name -eq '01_database.sql'){''}else{'enterprise_platform'})
    }
}

function Invoke-FlywayRaw($Server,[string]$Location,[string]$Command,[string]$Target='') {
    $args=@("-url=jdbc:mysql://127.0.0.1:$($Server.Port)/enterprise_platform",
        '-user=root','-password=',"-locations=filesystem:$($Location.Replace('\','/'))",
        '-baselineOnMigrate=true','-baselineVersion=2.0.0','-validateMigrationNaming=true',
        '-cleanDisabled=true','-connectRetries=10')
    if ($Target) { $args += "-target=$Target" }; $args += $Command
    $old=$ErrorActionPreference; $ErrorActionPreference='Continue'
    $out=&$flyway @args 2>&1; $code=$LASTEXITCODE; $ErrorActionPreference=$old
    $log=Join-Path $evidence "$($Server.Name)-$Command-$([DateTime]::UtcNow.Ticks).log"
    $out | Set-Content $log -Encoding utf8
    return @{ExitCode=$code;Output=($out -join "`n");Log=$log}
}

function Invoke-Flyway($Server,[string]$Location,[string]$Command,[string]$Target='') {
    $r=Invoke-FlywayRaw $Server $Location $Command $Target
    if ($r.ExitCode -ne 0) { throw "Flyway $Command failed for $($Server.Name): $($r.Output)" }
    return $r
}

function Assert-Rejected($Server,[string]$Name,[string]$Sql) {
    $r=Invoke-Mysql $Server $Sql
    $pass=$r.ExitCode -ne 0
    $checks.Add("$Name`t$(if($pass){'PASS'}else{'FAIL_ACCEPTED'})`t$($r.Output.Replace("`n",' '))")
    if (-not $pass) { $failures.Add("Database accepted invalid case: $Name") }
    return $pass
}

function Get-Fingerprint($Server) {
    $args=@('--protocol=TCP','--host=127.0.0.1',"--port=$($Server.Port)",
        '--user=root','--database=enterprise_platform','--batch','--raw','--skip-column-names')
    $rows=Get-Content $fingerprintSql -Raw | &$mysql @args
    if ($LASTEXITCODE -ne 0) { throw 'Fingerprint query failed' }
    $triggers=&$mysql @args "--execute=SELECT CONCAT('TRIGGER|',trigger_name,'|',event_manipulation,'|',event_object_table,'|',action_timing,'|',REPLACE(REPLACE(action_statement,CHAR(13),' '),CHAR(10),' ')) FROM information_schema.triggers WHERE trigger_schema=DATABASE() ORDER BY trigger_name"
    $all=@($rows)+@($triggers)
    $workflow=$all | Where-Object {$_ -match '^\w+\|workflow_' -or $_ -match '^\w+\|role_runtime_' -or $_ -match '^TRIGGER\|.*\|(workflow_|role_runtime_)'}
    $binding=$all | Where-Object {$_ -match '^\w+\|workflow_role_binding_' -or $_ -match '^TRIGGER\|.*\|workflow_role_binding_'}
    $result=@{}
    foreach($pair in @(@('full',$all),@('workflow',$workflow),@('binding',$binding))) {
        $file=Join-Path $evidence "$($Server.Name)-$($pair[0])-schema.txt"
        [IO.File]::WriteAllText($file,(($pair[1] -join "`n")+"`n"),[Text.UTF8Encoding]::new($false))
        $result[$pair[0]]=(Get-FileHash $file -Algorithm SHA256).Hash.ToLowerInvariant()
    }
    return $result
}

function Get-TargetObjectSignature($Server) {
    $sql = @"
SELECT object_line FROM (
SELECT CONCAT('TABLE|',table_name,'|',engine,'|',table_collation) object_line
  FROM information_schema.tables
 WHERE table_schema=DATABASE() AND table_name IN ('workflow_role_binding_promotion','workflow_role_binding_candidate_snapshot','workflow_role_binding_snapshot_event')
UNION ALL
SELECT CONCAT('COLUMN|',table_name,'|',ordinal_position,'|',column_name,'|',column_type,'|',is_nullable,'|',COALESCE(column_default,'<NULL>'),'|',COALESCE(character_set_name,''),'|',COALESCE(collation_name,''))
  FROM information_schema.columns
 WHERE table_schema=DATABASE() AND table_name IN ('workflow_role_binding_promotion','workflow_role_binding_candidate_snapshot','workflow_role_binding_snapshot_event')
UNION ALL
SELECT CONCAT('INDEX|',table_name,'|',index_name,'|',non_unique,'|',seq_in_index,'|',column_name)
  FROM information_schema.statistics
 WHERE table_schema=DATABASE() AND table_name IN ('workflow_role_binding_promotion','workflow_role_binding_candidate_snapshot','workflow_role_binding_snapshot_event')
UNION ALL
SELECT CONCAT('CONSTRAINT|',table_name,'|',constraint_name,'|',constraint_type)
  FROM information_schema.table_constraints
 WHERE table_schema=DATABASE() AND table_name IN ('workflow_role_binding_promotion','workflow_role_binding_candidate_snapshot','workflow_role_binding_snapshot_event')
UNION ALL
SELECT CONCAT('TRIGGER|',trigger_name,'|',event_manipulation,'|',event_object_table,'|',action_timing)
  FROM information_schema.triggers
 WHERE trigger_schema=DATABASE() AND event_object_table IN ('workflow_role_binding_promotion','workflow_role_binding_candidate_snapshot','workflow_role_binding_snapshot_event')
) governed_objects ORDER BY object_line
"@
    $r = Invoke-Mysql $Server $sql -Raw
    if ($r.ExitCode -ne 0) { throw "Target signature query failed: $($r.Output)" }
    return $r.Output.Trim()
}

function Reset-To-V2613($Server) {
    $drop = Invoke-Mysql $Server 'DROP DATABASE IF EXISTS enterprise_platform' -Database ''
    if ($drop.ExitCode -ne 0) { throw "Schema reset failed: $($drop.Output)" }
    Initialize-Baseline $Server
    Invoke-Flyway $Server $to2613 migrate '2.6.13' | Out-Null
}

function Insert-ActivationRequest($Server,[long]$Id,[string]$Activation,[string]$Status='PERSISTED') {
    $sql="INSERT INTO role_runtime_activation_request(id,activation_id,resolver_code,resolver_version,contract_hash,binding_hash,candidate_hash,directory_contract_hash,directory_revision,activation_hash,approval_evidence_hash,business_scope,effective_at,requested_by,status,created_by,updated_by,deleted,delete_token,version) VALUES($Id,'$Activation','ROLE_DIRECTORY','ROLE_DIRECTORY_V1',REPEAT('a',64),REPEAT('b',64),REPEAT('c',64),REPEAT('d',64),17,REPEAT('e',64),REPEAT('f',64),'INVESTMENT_DECISION','2026-08-13 09:00:00.000','validator','$Status','validator','validator',0,0,0)"
    $r=Invoke-Mysql $Server $sql; if($r.ExitCode-ne0){throw "Request insert failed: $($r.Output)"}
}

function Insert-ActivationApproval($Server,[long]$Id,[string]$Activation,[string]$Role,[string]$Decision='APPROVE') {
    $sql="INSERT INTO role_runtime_activation_approval(id,activation_id,approver_type,approver_id,decision,reason,source_evidence_hash,decision_hash,activation_hash,contract_hash,binding_hash,decision_time,created_by,updated_by,deleted,delete_token,version) VALUES($Id,'$Activation','$Role','approver-$Id','$Decision','decision',SHA2(CONCAT('source',$Id),256),SHA2(CONCAT('decision',$Id),256),REPEAT('e',64),REPEAT('a',64),REPEAT('b',64),'2026-08-13 09:10:00.000','validator','validator',0,0,0)"
    $r=Invoke-Mysql $Server $sql; if($r.ExitCode-ne0){throw "Approval insert failed: $($r.Output)"}
}

function Insert-ActivationEvidence($Server,[long]$Id,[long]$ApprovalId,[string]$Activation,[string]$Type) {
    $hashExpression = switch($Type) {
        'ACTIVATION_APPROVAL' { "REPEAT('f',64)" }
        'RESOLVER_CONTRACT' { "REPEAT('a',64)" }
        'BINDING' { "REPEAT('b',64)" }
        'CANDIDATE' { "REPEAT('c',64)" }
        'DIRECTORY' { "REPEAT('d',64)" }
        default { throw "Unsupported evidence type: $Type" }
    }
    $sql="INSERT INTO role_runtime_activation_evidence(id,approval_id,activation_id,evidence_type,evidence_hash,activation_hash,contract_hash,binding_hash,canonical_version,created_by,updated_by,deleted,delete_token,version) VALUES($Id,$ApprovalId,'$Activation','$Type',$hashExpression,REPEAT('e',64),REPEAT('a',64),REPEAT('b',64),'ROLE_RUNTIME_ACTIVATION_CANONICAL_V1','validator','validator',0,0,0)"
    $r=Invoke-Mysql $Server $sql; if($r.ExitCode-ne0){throw "Evidence insert failed: $($r.Output)"}
}

function Insert-ActivationChain($Server,[long]$Base,[string]$Activation,[string]$ActivationHash='eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee') {
    $sql="INSERT INTO role_runtime_activation_request(id,activation_id,resolver_code,resolver_version,contract_hash,binding_hash,candidate_hash,directory_contract_hash,directory_revision,activation_hash,approval_evidence_hash,business_scope,effective_at,requested_by,status,created_by,updated_by,deleted,delete_token,version) VALUES($Base,'$Activation','ROLE_DIRECTORY','ROLE_DIRECTORY_V1',REPEAT('a',64),REPEAT('b',64),REPEAT('c',64),REPEAT('d',64),17,'$ActivationHash',REPEAT('f',64),'INVESTMENT_DECISION','2026-08-13 09:00:00.000','validator','PERSISTED','validator','validator',0,0,0)"
    $r=Invoke-Mysql $Server $sql; if($r.ExitCode-ne0){throw "Activation insert failed: $($r.Output)"}
    $roles=@('BUSINESS_OWNER','SECURITY_AUDIT','RELEASE_APPROVER')
    for($i=0;$i-lt3;$i++){
        $id=$Base+$i+1; $role=$roles[$i]
        $r=Invoke-Mysql $Server "INSERT INTO role_runtime_activation_approval(id,activation_id,approver_type,approver_id,decision,reason,source_evidence_hash,decision_hash,activation_hash,contract_hash,binding_hash,decision_time,created_by,updated_by,deleted,delete_token,version) VALUES($id,'$Activation','$role','approver-$i','APPROVE','approved',SHA2(CONCAT('source',$id),256),SHA2(CONCAT('decision',$id),256),'$ActivationHash',REPEAT('a',64),REPEAT('b',64),'2026-08-13 09:10:00.000','validator','validator',0,0,0)"
        if($r.ExitCode-ne0){throw "Approval insert failed: $($r.Output)"}
    }
    $types=@('ACTIVATION_APPROVAL','RESOLVER_CONTRACT','BINDING','CANDIDATE','DIRECTORY')
    for($i=0;$i-lt5;$i++){
        $id=$Base+10+$i; $type=$types[$i]
        $hashExpression = switch($type) {
            'ACTIVATION_APPROVAL' { "REPEAT('f',64)" }
            'RESOLVER_CONTRACT' { "REPEAT('a',64)" }
            'BINDING' { "REPEAT('b',64)" }
            'CANDIDATE' { "REPEAT('c',64)" }
            'DIRECTORY' { "REPEAT('d',64)" }
        }
        $r=Invoke-Mysql $Server "INSERT INTO role_runtime_activation_evidence(id,approval_id,activation_id,evidence_type,evidence_hash,activation_hash,contract_hash,binding_hash,canonical_version,created_by,updated_by,deleted,delete_token,version) VALUES($id,$($Base+3),'$Activation','$type',$hashExpression,'$ActivationHash',REPEAT('a',64),REPEAT('b',64),'ROLE_RUNTIME_ACTIVATION_CANONICAL_V1','validator','validator',0,0,0)"
        if($r.ExitCode-ne0){throw "Evidence insert failed: $($r.Output)"}
    }
}

function PromotionSql([long]$Id,[string]$Promotion,[string]$Activation,[string]$ActivationHash='eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee') {
    return "INSERT INTO workflow_role_binding_promotion(id,promotion_id,activation_id,activation_delete_token,activation_hash,activation_audit_hash,approval_evidence_hash,resolver_code,resolver_version,resolver_contract_hash,binding_hash,candidate_hash,directory_contract_hash,directory_revision,business_scope,effective_from,effective_until,permission_evidence_hash,promotion_evidence_hash,promotion_hash,status,promoted_at,promoted_by,canonical_version,created_by,updated_by,deleted,delete_token,version) VALUES($Id,'$Promotion','$Activation',0,'$ActivationHash',REPEAT('1',64),REPEAT('f',64),'ROLE_DIRECTORY','ROLE_DIRECTORY_V1',REPEAT('a',64),REPEAT('b',64),REPEAT('c',64),REPEAT('d',64),17,'INVESTMENT_DECISION','2026-08-13 09:00:00.000','2026-08-13 11:00:00.000',REPEAT('2',64),REPEAT('3',64),REPEAT('4',64),'PROMOTED','2026-08-13 10:00:00.000','validator','ROLE_RUNTIME_BINDING_PROMOTION_CANONICAL_V1','validator','validator',0,0,0)"
}

function SnapshotSql([long]$Id,[string]$Snapshot,[long]$PromotionRow,[string]$Promotion,[string]$Activation) {
    return "INSERT INTO workflow_role_binding_candidate_snapshot(id,snapshot_id,promotion_row_id,promotion_id,activation_id,activation_delete_token,activation_hash,promotion_hash,promotion_evidence_hash,promotion_reference_hash,resolver_code,resolver_version,resolver_contract_hash,binding_hash,candidate_hash,directory_contract_hash,directory_revision,business_scope,effective_from,effective_until,activation_audit_hash,approval_evidence_hash,permission_evidence_hash,evidence_set_hash,snapshot_hash,canonical_version,created_by,updated_by,deleted,delete_token,version) VALUES($Id,'$Snapshot',$PromotionRow,'$Promotion','$Activation',0,REPEAT('e',64),REPEAT('4',64),REPEAT('3',64),REPEAT('5',64),'ROLE_DIRECTORY','ROLE_DIRECTORY_V1',REPEAT('a',64),REPEAT('b',64),REPEAT('c',64),REPEAT('d',64),17,'INVESTMENT_DECISION','2026-08-13 09:00:00.000','2026-08-13 11:00:00.000',REPEAT('1',64),REPEAT('f',64),REPEAT('2',64),REPEAT('6',64),REPEAT('7',64),'ROLE_RUNTIME_BINDING_SNAPSHOT_CANONICAL_V1','validator','validator',0,0,0)"
}

function EventSql([long]$Id,[long]$SnapshotRow,[string]$Snapshot,[string]$Promotion,[string]$Activation,[long]$Sequence,[string]$From,[string]$To,[string]$Previous,[string]$Hash) {
    $fromValue=$(if($From){"'$From'"}else{'NULL'}); $previousValue=$(if($Previous){"'$Previous'"}else{'NULL'})
    return "INSERT INTO workflow_role_binding_snapshot_event(id,snapshot_row_id,snapshot_id,promotion_id,activation_id,sequence_no,from_status,to_status,reason_code,source_evidence_hash,previous_event_hash,event_hash,occurred_at,operator_id,canonical_version,created_by,updated_by,deleted,delete_token,version) VALUES($Id,$SnapshotRow,'$Snapshot','$Promotion','$Activation',$Sequence,$fromValue,'$To','TEST',REPEAT('7',64),$previousValue,'$Hash','2026-08-13 10:00:00.000','validator','ROLE_RUNTIME_BINDING_SNAPSHOT_CANONICAL_V1','validator','validator',0,0,0)"
}

try {
    $fresh=New-IsolatedMysql 'fresh' ($BasePort+1)
    $upgrade=New-IsolatedMysql 'upgrade' ($BasePort+2)
    $guard=New-IsolatedMysql 'guard' ($BasePort+3)
    $guardMissing=New-IsolatedMysql 'guard-missing-evidence' ($BasePort+4)
    foreach($server in @($fresh,$upgrade,$guard,$guardMissing)){Initialize-Baseline $server}

    Invoke-Flyway $fresh $full migrate '2.6.14' | Out-Null
    Invoke-Flyway $fresh $full validate | Out-Null
    $freshNoop=Invoke-Flyway $fresh $full migrate '2.6.14'
    Invoke-Flyway $fresh $full info | Out-Null

    Invoke-Flyway $upgrade $to2613 migrate '2.6.13' | Out-Null
    $before=[int](Invoke-Mysql $upgrade 'SELECT COUNT(*) FROM flyway_schema_history WHERE success=1' -Raw).Output.Trim()
    Invoke-Flyway $upgrade $full migrate '2.6.14' | Out-Null
    Invoke-Flyway $upgrade $full validate | Out-Null
    $upgradeNoop=Invoke-Flyway $upgrade $full migrate '2.6.14'
    Invoke-Flyway $upgrade $full info | Out-Null
    $after=[int](Invoke-Mysql $upgrade 'SELECT COUNT(*) FROM flyway_schema_history WHERE success=1' -Raw).Output.Trim()
    if($after-$before-ne1){$failures.Add('Upgrade did not apply exactly V2.6.14')}
    foreach($noop in @($freshNoop,$upgradeNoop)){if($noop.Output-notmatch'No migration necessary'){$failures.Add('Second migrate was not no-op')}}

    $history=Invoke-Mysql $upgrade "SELECT version,description,checksum,success FROM flyway_schema_history WHERE version='2.6.14'" -Raw
    $history.Output | Set-Content (Join-Path $evidence 'v2614-history.txt') -Encoding utf8
    $flywayChecksum=($history.Output -split "`t")[2]

    $fpFresh=Get-Fingerprint $fresh; $fpUpgrade=Get-Fingerprint $upgrade
    foreach($scope in @('full','workflow','binding')){if($fpFresh[$scope]-ne$fpUpgrade[$scope]){$failures.Add("Fingerprint mismatch: $scope")}}

    $structure=Invoke-Mysql $upgrade @"
SELECT 'TABLES',COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('workflow_role_binding_promotion','workflow_role_binding_candidate_snapshot','workflow_role_binding_snapshot_event')
UNION ALL SELECT 'COLUMNS',COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name IN ('workflow_role_binding_promotion','workflow_role_binding_candidate_snapshot','workflow_role_binding_snapshot_event')
UNION ALL SELECT 'INDEXES',COUNT(DISTINCT CONCAT(table_name,':',index_name)) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name IN ('workflow_role_binding_promotion','workflow_role_binding_candidate_snapshot','workflow_role_binding_snapshot_event')
UNION ALL SELECT 'FOREIGN_KEYS',COUNT(*) FROM information_schema.table_constraints WHERE table_schema=DATABASE() AND table_name IN ('workflow_role_binding_promotion','workflow_role_binding_candidate_snapshot','workflow_role_binding_snapshot_event') AND constraint_type='FOREIGN KEY'
UNION ALL SELECT 'CHECKS',COUNT(*) FROM information_schema.table_constraints WHERE table_schema=DATABASE() AND table_name IN ('workflow_role_binding_promotion','workflow_role_binding_candidate_snapshot','workflow_role_binding_snapshot_event') AND constraint_type='CHECK'
UNION ALL SELECT 'TRIGGERS',COUNT(*) FROM information_schema.triggers WHERE trigger_schema=DATABASE() AND event_object_table IN ('workflow_role_binding_promotion','workflow_role_binding_candidate_snapshot','workflow_role_binding_snapshot_event');
"@ -Raw
    $structure.Output | Set-Content (Join-Path $evidence 'structure-counts.txt') -Encoding utf8
    foreach($table in $targetTables){
        (Invoke-Mysql $upgrade "SHOW CREATE TABLE $table" -Raw).Output | Set-Content (Join-Path $evidence "$table-create.txt") -Encoding utf8
    }
    (Invoke-Mysql $upgrade "SELECT trigger_name,event_manipulation,event_object_table,action_timing FROM information_schema.triggers WHERE trigger_schema=DATABASE() AND event_object_table LIKE 'workflow_role_binding_%' ORDER BY trigger_name" -Raw).Output | Set-Content (Join-Path $evidence 'triggers.txt') -Encoding utf8

    # Every negative case is executed from a rebuilt V2.6.13 schema. The target-object
    # signature covers Table/Column/Index/Unique/FK/CHECK/Trigger metadata and must remain
    # byte-for-byte identical across the failed migration attempt.
    $guardMatrix = [System.Collections.Generic.List[object]]::new()
    function Invoke-GuardFailureScenario([string]$Name,[scriptblock]$Seed) {
        Reset-To-V2613 $guard
        & $Seed $guard
        $beforeSignature = Get-TargetObjectSignature $guard
        $beforeFile = Join-Path $evidence "guard-$Name-before.txt"
        [IO.File]::WriteAllText($beforeFile,$beforeSignature,[Text.UTF8Encoding]::new($false))
        $result = Invoke-FlywayRaw $guard $full migrate '2.6.14'
        $afterSignature = Get-TargetObjectSignature $guard
        $afterFile = Join-Path $evidence "guard-$Name-after.txt"
        [IO.File]::WriteAllText($afterFile,$afterSignature,[Text.UTF8Encoding]::new($false))
        $counts = (Invoke-Mysql $guard @"
SELECT
 (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('workflow_role_binding_promotion','workflow_role_binding_candidate_snapshot','workflow_role_binding_snapshot_event')),
 (SELECT COUNT(DISTINCT CONCAT(table_name,':',index_name)) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name IN ('workflow_role_binding_promotion','workflow_role_binding_candidate_snapshot','workflow_role_binding_snapshot_event')),
 (SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema=DATABASE() AND table_name IN ('workflow_role_binding_promotion','workflow_role_binding_candidate_snapshot','workflow_role_binding_snapshot_event') AND constraint_type='FOREIGN KEY'),
 (SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema=DATABASE() AND table_name IN ('workflow_role_binding_promotion','workflow_role_binding_candidate_snapshot','workflow_role_binding_snapshot_event') AND constraint_type='CHECK'),
 (SELECT COUNT(*) FROM information_schema.triggers WHERE trigger_schema=DATABASE() AND event_object_table IN ('workflow_role_binding_promotion','workflow_role_binding_candidate_snapshot','workflow_role_binding_snapshot_event'))
"@ -Raw).Output.Trim()
        $pass = $result.ExitCode -ne 0 -and $beforeSignature -ceq $afterSignature -and $counts -eq "0`t0`t0`t0`t0"
        if (-not $pass) { $failures.Add("Guard matrix failed: $Name exit=$($result.ExitCode) counts=$counts") }
        $record = [ordered]@{name=$Name;pass=$pass;exitCode=$result.ExitCode;
            permanentObjectCounts=$counts;signatureUnchanged=($beforeSignature -ceq $afterSignature)}
        $guardMatrix.Add($record)
        $checks.Add("guard_$Name`t$(if($pass){'PASS'}else{'FAIL'})`tcounts=$counts signatureUnchanged=$($record.signatureUnchanged)")
    }

    Invoke-GuardFailureScenario '01_request_without_approval' {
        param($s) Insert-ActivationRequest $s 201000 'GUARD-01'
    }
    Invoke-GuardFailureScenario '02_insufficient_approval_count' {
        param($s) Insert-ActivationRequest $s 202000 'GUARD-02';
        Insert-ActivationApproval $s 202001 'GUARD-02' 'BUSINESS_OWNER';
        Insert-ActivationApproval $s 202002 'GUARD-02' 'SECURITY_AUDIT'
    }
    Invoke-GuardFailureScenario '03_approval_complete_evidence_missing' {
        param($s) Insert-ActivationRequest $s 203000 'GUARD-03';
        Insert-ActivationApproval $s 203001 'GUARD-03' 'BUSINESS_OWNER';
        Insert-ActivationApproval $s 203002 'GUARD-03' 'SECURITY_AUDIT';
        Insert-ActivationApproval $s 203003 'GUARD-03' 'RELEASE_APPROVER'
    }
    Invoke-GuardFailureScenario '04_approval_other_activation' {
        param($s) Insert-ActivationRequest $s 204000 'GUARD-04';
        Insert-ActivationApproval $s 204001 'GUARD-04' 'BUSINESS_OWNER';
        Insert-ActivationApproval $s 204002 'GUARD-04' 'SECURITY_AUDIT';
        Insert-ActivationRequest $s 204100 'GUARD-04-OTHER' 'DRAFT';
        Insert-ActivationApproval $s 204101 'GUARD-04-OTHER' 'RELEASE_APPROVER'
    }
    Invoke-GuardFailureScenario '05_evidence_other_approval_or_activation' {
        param($s) Insert-ActivationChain $s 205000 'GUARD-05'; Insert-ActivationRequest $s 205100 'GUARD-05-OTHER' 'DRAFT';
        Invoke-Mysql $s 'DROP TRIGGER trg_role_activation_evidence_no_update' | Out-Null;
        Invoke-Mysql $s 'ALTER TABLE role_runtime_activation_evidence DROP FOREIGN KEY fk_role_activation_evidence_request, DROP FOREIGN KEY fk_role_activation_evidence_approval' | Out-Null;
        $r=Invoke-Mysql $s "UPDATE role_runtime_activation_evidence SET activation_id='GUARD-05-OTHER' WHERE activation_id='GUARD-05' AND evidence_type='DIRECTORY'";
        if($r.ExitCode-ne0){throw $r.Output}
    }
    Invoke-GuardFailureScenario '06_approval_not_approved' {
        param($s) Insert-ActivationChain $s 206000 'GUARD-06';
        Invoke-Mysql $s 'DROP TRIGGER trg_role_activation_approval_no_update' | Out-Null;
        $r=Invoke-Mysql $s "UPDATE role_runtime_activation_approval SET decision='REJECT' WHERE activation_id='GUARD-06' AND approver_type='RELEASE_APPROVER'";
        if($r.ExitCode-ne0){throw $r.Output}
    }
    Invoke-GuardFailureScenario '07_activation_hash_drift' {
        param($s) Insert-ActivationChain $s 207000 'GUARD-07';
        Invoke-Mysql $s 'DROP TRIGGER trg_role_activation_approval_no_update' | Out-Null;
        $r=Invoke-Mysql $s "UPDATE role_runtime_activation_approval SET activation_hash=REPEAT('0',64) WHERE activation_id='GUARD-07' AND approver_type='BUSINESS_OWNER'";
        if($r.ExitCode-ne0){throw $r.Output}
    }
    Invoke-GuardFailureScenario '08_contract_hash_drift' {
        param($s) Insert-ActivationChain $s 208000 'GUARD-08';
        Invoke-Mysql $s 'DROP TRIGGER trg_role_activation_approval_no_update' | Out-Null;
        $r=Invoke-Mysql $s "UPDATE role_runtime_activation_approval SET contract_hash=REPEAT('0',64) WHERE activation_id='GUARD-08' AND approver_type='BUSINESS_OWNER'";
        if($r.ExitCode-ne0){throw $r.Output}
    }
    Invoke-GuardFailureScenario '09_binding_hash_drift' {
        param($s) Insert-ActivationChain $s 209000 'GUARD-09';
        Invoke-Mysql $s 'DROP TRIGGER trg_role_activation_approval_no_update' | Out-Null;
        $r=Invoke-Mysql $s "UPDATE role_runtime_activation_approval SET binding_hash=REPEAT('0',64) WHERE activation_id='GUARD-09' AND approver_type='BUSINESS_OWNER'";
        if($r.ExitCode-ne0){throw $r.Output}
    }
    Invoke-GuardFailureScenario '10_candidate_hash_drift' {
        param($s) Insert-ActivationChain $s 210000 'GUARD-10';
        Invoke-Mysql $s 'DROP TRIGGER trg_role_activation_evidence_no_update' | Out-Null;
        $r=Invoke-Mysql $s "UPDATE role_runtime_activation_evidence SET evidence_hash=REPEAT('0',64) WHERE activation_id='GUARD-10' AND evidence_type='CANDIDATE'";
        if($r.ExitCode-ne0){throw $r.Output}
    }
    Invoke-GuardFailureScenario '11_directory_revision_drift' {
        param($s) Insert-ActivationChain $s 211000 'GUARD-11';
        Invoke-Mysql $s 'DROP TRIGGER trg_role_activation_request_no_update' | Out-Null;
        Invoke-Mysql $s 'ALTER TABLE role_runtime_activation_request DROP CHECK chk_role_activation_revision' | Out-Null;
        $r=Invoke-Mysql $s "UPDATE role_runtime_activation_request SET directory_revision=-1 WHERE activation_id='GUARD-11'";
        if($r.ExitCode-ne0){throw $r.Output}
    }
    Invoke-GuardFailureScenario '12_resolver_identity_invalid' {
        param($s) Insert-ActivationChain $s 212000 'GUARD-12';
        Invoke-Mysql $s 'DROP TRIGGER trg_role_activation_request_no_update' | Out-Null;
        Invoke-Mysql $s 'ALTER TABLE role_runtime_activation_request DROP CHECK chk_role_activation_resolver' | Out-Null;
        $r=Invoke-Mysql $s "UPDATE role_runtime_activation_request SET resolver_version='role_directory_v1' WHERE activation_id='GUARD-12'";
        if($r.ExitCode-ne0){throw $r.Output}
    }
    $guardMatrix | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $evidence 'guard-failure-matrix.json') -Encoding utf8

    # A complete Request -> 3 APPROVE -> 5 typed Evidence chain is the sole positive
    # PERSISTED case. It must pass the Guard and create all three governed tables.
    Reset-To-V2613 $guardMissing
    Insert-ActivationChain $guardMissing 220000 'GUARD-LEGAL'
    $legalGuardBefore=Get-TargetObjectSignature $guardMissing
    $legalGuardResult=Invoke-FlywayRaw $guardMissing $full migrate '2.6.14'
    $legalGuardTargets=(Invoke-Mysql $guardMissing "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('workflow_role_binding_promotion','workflow_role_binding_candidate_snapshot','workflow_role_binding_snapshot_event')" -Raw).Output.Trim()
    $legalGuardPass=$legalGuardResult.ExitCode-eq0-and$legalGuardBefore-eq''-and$legalGuardTargets-eq'3'
    if(-not$legalGuardPass){$failures.Add("Complete Activation evidence chain was not admitted: exit=$($legalGuardResult.ExitCode) targets=$legalGuardTargets")}
    $checks.Add("guard_legal_complete_chain`t$(if($legalGuardPass){'PASS'}else{'FAIL'})`ttargets=$legalGuardTargets")

    # Legal chain.
    Insert-ActivationChain $upgrade 100000 'ACT-LEGAL'
    $r=Invoke-Mysql $upgrade (PromotionSql 100100 'PROMO-LEGAL' 'ACT-LEGAL'); if($r.ExitCode-ne0){throw $r.Output}
    $r=Invoke-Mysql $upgrade (SnapshotSql 100200 'SNAP-LEGAL' 100100 'PROMO-LEGAL' 'ACT-LEGAL'); if($r.ExitCode-ne0){throw $r.Output}
    $r=Invoke-Mysql $upgrade (EventSql 100301 100200 'SNAP-LEGAL' 'PROMO-LEGAL' 'ACT-LEGAL' 1 '' 'CREATED' '' ('8'*64)); if($r.ExitCode-ne0){throw $r.Output}
    $r=Invoke-Mysql $upgrade (EventSql 100302 100200 'SNAP-LEGAL' 'PROMO-LEGAL' 'ACT-LEGAL' 2 'CREATED' 'VALIDATED' ('8'*64) ('9'*64)); if($r.ExitCode-ne0){throw $r.Output}
    $r=Invoke-Mysql $upgrade (EventSql 100303 100200 'SNAP-LEGAL' 'PROMO-LEGAL' 'ACT-LEGAL' 3 'VALIDATED' 'ACTIVE' ('9'*64) ('a'*64)); if($r.ExitCode-ne0){throw $r.Output}
    $legalCounts=(Invoke-Mysql $upgrade "SELECT (SELECT COUNT(*) FROM workflow_role_binding_promotion WHERE promotion_id='PROMO-LEGAL'),(SELECT COUNT(*) FROM workflow_role_binding_candidate_snapshot WHERE snapshot_id='SNAP-LEGAL'),(SELECT COUNT(*) FROM workflow_role_binding_snapshot_event WHERE snapshot_id='SNAP-LEGAL')" -Raw).Output.Trim()
    if($legalCounts-ne"1`t1`t3"){$failures.Add("Legal chain counts unexpected: $legalCounts")}

    # Strong ownership, uniqueness, state and append-only rejection matrix.
    Assert-Rejected $upgrade 'promotion_missing_activation' (PromotionSql 101100 'PROMO-MISSING' 'ACT-MISSING') | Out-Null
    Insert-ActivationChain $upgrade 102000 'ACT-OTHER' ('0'*64)
    Assert-Rejected $upgrade 'promotion_cross_activation_hash' (PromotionSql 102100 'PROMO-CROSS' 'ACT-LEGAL' ('0'*64)) | Out-Null
    Assert-Rejected $upgrade 'duplicate_promotion' (PromotionSql 102101 'PROMO-LEGAL' 'ACT-LEGAL') | Out-Null
    Assert-Rejected $upgrade 'snapshot_missing_promotion' (SnapshotSql 103200 'SNAP-MISSING' 999999 'PROMO-MISSING' 'ACT-LEGAL') | Out-Null
    Assert-Rejected $upgrade 'snapshot_cross_promotion' (SnapshotSql 103201 'SNAP-CROSS' 100100 'PROMO-OTHER' 'ACT-LEGAL') | Out-Null
    Assert-Rejected $upgrade 'snapshot_cross_activation' (SnapshotSql 103202 'SNAP-CROSS-ACT' 100100 'PROMO-LEGAL' 'ACT-OTHER') | Out-Null
    $crossContract=(SnapshotSql 103203 'SNAP-CROSS-CONTRACT' 100100 'PROMO-LEGAL' 'ACT-LEGAL').Replace("REPEAT('a',64),REPEAT('b',64),REPEAT('c',64)","REPEAT('0',64),REPEAT('b',64),REPEAT('c',64)")
    Assert-Rejected $upgrade 'snapshot_cross_contract' $crossContract | Out-Null
    Assert-Rejected $upgrade 'duplicate_snapshot' (SnapshotSql 103204 'SNAP-LEGAL' 100100 'PROMO-LEGAL' 'ACT-LEGAL') | Out-Null
    Assert-Rejected $upgrade 'event_missing_snapshot' (EventSql 104301 999999 'SNAP-MISSING' 'PROMO-LEGAL' 'ACT-LEGAL' 1 '' 'CREATED' '' ('b'*64)) | Out-Null
    Assert-Rejected $upgrade 'event_cross_promotion' (EventSql 104302 100200 'SNAP-LEGAL' 'PROMO-X' 'ACT-LEGAL' 4 'ACTIVE' 'REVOKED' ('a'*64) ('b'*64)) | Out-Null
    Assert-Rejected $upgrade 'event_cross_activation' (EventSql 104303 100200 'SNAP-LEGAL' 'PROMO-LEGAL' 'ACT-OTHER' 4 'ACTIVE' 'REVOKED' ('a'*64) ('c'*64)) | Out-Null
    Assert-Rejected $upgrade 'event_illegal_transition' (EventSql 104304 100200 'SNAP-LEGAL' 'PROMO-LEGAL' 'ACT-LEGAL' 4 'ACTIVE' 'VALIDATED' ('a'*64) ('d'*64)) | Out-Null
    Assert-Rejected $upgrade 'event_duplicate_sequence' (EventSql 104305 100200 'SNAP-LEGAL' 'PROMO-LEGAL' 'ACT-LEGAL' 3 'VALIDATED' 'ACTIVE' ('9'*64) ('e'*64)) | Out-Null
    foreach($case in @(
        @('promotion_update',"UPDATE workflow_role_binding_promotion SET promoted_by='x' WHERE id=100100"),
        @('promotion_delete',"DELETE FROM workflow_role_binding_promotion WHERE id=100100"),
        @('snapshot_update',"UPDATE workflow_role_binding_candidate_snapshot SET created_by='x' WHERE id=100200"),
        @('snapshot_delete',"DELETE FROM workflow_role_binding_candidate_snapshot WHERE id=100200"),
        @('event_update',"UPDATE workflow_role_binding_snapshot_event SET reason_code='x' WHERE id=100301"),
        @('event_delete',"DELETE FROM workflow_role_binding_snapshot_event WHERE id=100301"))) {
        Assert-Rejected $upgrade $case[0] $case[1] | Out-Null
    }
    $invalidHashes=@(('A'*64),('aA'+('a'*62)),('a'*63),('a'*65),('g'*64),'','NULL')
    foreach($replacement in $invalidHashes) {
        $sql=PromotionSql (105000+$checks.Count) "PROMO-HASH-$($checks.Count)" 'ACT-LEGAL'
        if($replacement-eq'NULL'){$sql=$sql.Replace("REPEAT('4',64),'PROMOTED'","NULL,'PROMOTED'")}
        else{$sql=$sql.Replace("REPEAT('4',64),'PROMOTED'","'$replacement','PROMOTED'")}
        Assert-Rejected $upgrade "promotion_hash_invalid_$($checks.Count)" $sql | Out-Null
    }
    foreach($resolver in @('role_directory_v1','Role_Directory_V1','','ROLE-DIRECTORY','NULL')){
        $sql=PromotionSql (106000+$checks.Count) "PROMO-RESOLVER-$($checks.Count)" 'ACT-LEGAL'
        if($resolver-eq'NULL'){$sql=$sql.Replace("'ROLE_DIRECTORY_V1',REPEAT('a',64)","NULL,REPEAT('a',64)")}
        else{$sql=$sql.Replace("'ROLE_DIRECTORY_V1',REPEAT('a',64)","'$resolver',REPEAT('a',64)")}
        Assert-Rejected $upgrade "resolver_invalid_$($checks.Count)" $sql | Out-Null
    }

    # Transaction rollback: a valid Promotion followed by an invalid Snapshot.
    Insert-ActivationChain $upgrade 110000 'ACT-TX'
    $txSql="START TRANSACTION; $(PromotionSql 110100 'PROMO-TX' 'ACT-TX'); $(SnapshotSql 110200 'SNAP-TX' 999999 'PROMO-TX' 'ACT-TX'); COMMIT;"
    $tx=Invoke-Mysql $upgrade $txSql
    $txCounts=(Invoke-Mysql $upgrade "SELECT (SELECT COUNT(*) FROM workflow_role_binding_promotion WHERE promotion_id='PROMO-TX'),(SELECT COUNT(*) FROM workflow_role_binding_candidate_snapshot WHERE snapshot_id='SNAP-TX'),(SELECT COUNT(*) FROM workflow_role_binding_snapshot_event WHERE snapshot_id='SNAP-TX')" -Raw).Output.Trim()
    if($tx.ExitCode-eq0-or$txCounts-ne"0`t0`t0"){$failures.Add("Transaction rollback failed: $txCounts")}

    # Two-session unique winner tests.
    Insert-ActivationChain $upgrade 120000 'ACT-CONCURRENT'
    function Invoke-Concurrent([string]$Name,[string]$Sql1,[string]$Sql2) {
        $out1=Join-Path $evidence "$Name-session1.out"; $err1=Join-Path $evidence "$Name-session1.err"
        $out2=Join-Path $evidence "$Name-session2.out"; $err2=Join-Path $evidence "$Name-session2.err"
        $in1=Join-Path $evidence "$Name-session1.sql"; $in2=Join-Path $evidence "$Name-session2.sql"
        [IO.File]::WriteAllText($in1,"$Sql1;`n",[Text.UTF8Encoding]::new($false))
        [IO.File]::WriteAllText($in2,"$Sql2;`n",[Text.UTF8Encoding]::new($false))
        $base=@('--protocol=TCP','--host=127.0.0.1',"--port=$($upgrade.Port)",'--user=root','--database=enterprise_platform')
        $p1=Start-Process $mysql -ArgumentList $base -WindowStyle Hidden -PassThru -RedirectStandardInput $in1 -RedirectStandardOutput $out1 -RedirectStandardError $err1
        $p2=Start-Process $mysql -ArgumentList $base -WindowStyle Hidden -PassThru -RedirectStandardInput $in2 -RedirectStandardOutput $out2 -RedirectStandardError $err2
        $p1.WaitForExit(); $p2.WaitForExit(); $p1.Refresh(); $p2.Refresh()
        $code1=[int]$p1.ExitCode; $code2=[int]$p2.ExitCode
        $error1=(Get-Content $err1 -ErrorAction SilentlyContinue)-join' '
        $error2=(Get-Content $err2 -ErrorAction SilentlyContinue)-join' '
        $all="$error1 $error2"
        $oneWinner=(@($error1,$error2)|Where-Object{$_-match'ERROR'}).Count-eq1
        if(-not$oneWinner-or$all-match'ERROR 1213|ERROR 1205'){$failures.Add("Concurrency failed: $Name exits=$code1,$code2 $all")}
        $checks.Add("$Name`t$(if($oneWinner){'PASS'}else{'FAIL'})`texits=$code1,$code2 $all")
    }
    $promotionConcurrent1=(PromotionSql 120100 'PROMO-CONCURRENT' 'ACT-CONCURRENT').Replace("REPEAT('4',64),'PROMOTED'","REPEAT('5',64),'PROMOTED'")
    $promotionConcurrent2=(PromotionSql 120101 'PROMO-CONCURRENT' 'ACT-CONCURRENT').Replace("REPEAT('4',64),'PROMOTED'","REPEAT('5',64),'PROMOTED'")
    Invoke-Concurrent 'concurrent_promotion' $promotionConcurrent1 $promotionConcurrent2
    $promotionRow=(Invoke-Mysql $upgrade "SELECT id FROM workflow_role_binding_promotion WHERE promotion_id='PROMO-CONCURRENT'" -Raw).Output.Trim()
    $snapshotConcurrent1=(SnapshotSql 120200 'SNAP-CONCURRENT' $promotionRow 'PROMO-CONCURRENT' 'ACT-CONCURRENT').Replace("REPEAT('4',64)","REPEAT('5',64)").Replace("REPEAT('7',64)","REPEAT('8',64)")
    $snapshotConcurrent2=(SnapshotSql 120201 'SNAP-CONCURRENT' $promotionRow 'PROMO-CONCURRENT' 'ACT-CONCURRENT').Replace("REPEAT('4',64)","REPEAT('5',64)").Replace("REPEAT('7',64)","REPEAT('8',64)")
    Invoke-Concurrent 'concurrent_snapshot' $snapshotConcurrent1 $snapshotConcurrent2
    $snapshotRow=(Invoke-Mysql $upgrade "SELECT id FROM workflow_role_binding_candidate_snapshot WHERE snapshot_id='SNAP-CONCURRENT'" -Raw).Output.Trim()
    Invoke-Concurrent 'concurrent_event' (EventSql 120301 $snapshotRow 'SNAP-CONCURRENT' 'PROMO-CONCURRENT' 'ACT-CONCURRENT' 1 '' 'CREATED' '' ('b'*64)) (EventSql 120302 $snapshotRow 'SNAP-CONCURRENT' 'PROMO-CONCURRENT' 'ACT-CONCURRENT' 1 '' 'CREATED' '' ('c'*64))

    $runtimeAssets=(Invoke-Mysql $upgrade "SELECT (SELECT COUNT(*) FROM workflow_instance WHERE business_key='ACT-LEGAL'),(SELECT COUNT(*) FROM workflow_task WHERE remark='ACT-LEGAL'),(SELECT COUNT(*) FROM workflow_task_candidate_pool WHERE remark='ACT-LEGAL'),(SELECT COUNT(*) FROM workflow_task_claim WHERE remark='ACT-LEGAL')" -Raw).Output.Trim()
    if($runtimeAssets-ne"0`t0`t0`t0"){$failures.Add("Runtime objects unexpectedly created: $runtimeAssets")}

    $checks | Set-Content (Join-Path $evidence 'negative-and-concurrency-matrix.txt') -Encoding utf8
    $summary=[ordered]@{
        outputRoot=$OutputRoot; mysqlVersion=(Invoke-Mysql $upgrade 'SELECT VERSION()' -Raw).Output.Trim()
        flywayVersion='13.0.0'; javaVersion='21.0.12'; mavenVersion='3.9.9'
        sha256=$actualSha; flywayChecksum=$flywayChecksum
        freshNoop=($freshNoop.Output-match'No migration necessary')
        upgradeApplied=($after-$before); upgradeNoop=($upgradeNoop.Output-match'No migration necessary')
        fingerprints=$fpFresh; structure=$structure.Output; legalCounts=$legalCounts
        guardMatrixTotal=$guardMatrix.Count
        guardMatrixPassed=@($guardMatrix | Where-Object pass).Count
        guardMatrix=@($guardMatrix)
        legalEvidenceGuardPass=$legalGuardPass; legalEvidenceGuardTargets=$legalGuardTargets
        transactionCounts=$txCounts; negativeChecks=$checks.Count; failures=@($failures)
    }
    $summary | ConvertTo-Json -Depth 8 | Set-Content (Join-Path $evidence 'summary.json') -Encoding utf8
    $summary | ConvertTo-Json -Depth 8
    if($failures.Count-gt0){exit 2}
}
finally {
    foreach($process in $servers){if($process-and-not$process.HasExited){Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue}}
    foreach($port in @(($BasePort+1),($BasePort+2),($BasePort+3),($BasePort+4))) {
        $old=$ErrorActionPreference; $ErrorActionPreference='Continue'
        & $mysqladmin --protocol=TCP --host=127.0.0.1 --port=$port --user=root shutdown 2>$null | Out-Null
        $ErrorActionPreference=$old
    }
}

param(
    [string]$OutputRoot = "D:\codex-validation-v2613-$((Get-Date).ToString('yyyyMMdd-HHmmss'))",
    [int]$BasePort = 36300
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
$expectedV2613Sha = 'da80b7d3483ba3f3ee6b4f938515e94730695e28fc9842b9e488eb8daa68fd17'
foreach ($asset in @($mysqld,$mysql,$mysqladmin,$flyway,$fingerprintSql)) {
    if (-not (Test-Path -LiteralPath $asset)) { throw "Missing validation asset: $asset" }
}
if (Test-Path -LiteralPath $OutputRoot) { throw "Output root already exists: $OutputRoot" }
if ((Get-FileHash (Join-Path $repoMigrations 'V2.6.13__create_role_runtime_activation_evidence.sql') -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expectedV2613Sha) {
    throw 'V2.6.13 SHA-256 drift before validation'
}

$evidence = Join-Path $OutputRoot 'evidence'
$baselineCopy = Join-Path $OutputRoot 'baseline'
$full = Join-Path $OutputRoot 'migrations-full'
$to2612 = Join-Path $OutputRoot 'migrations-to-2612'
$to268 = Join-Path $OutputRoot 'migrations-to-268'
New-Item -ItemType Directory -Path $evidence,$baselineCopy,$full,$to2612,$to268 -Force | Out-Null
foreach ($name in $baselineFiles) { Copy-Item (Join-Path $baselineSource $name) (Join-Path $baselineCopy $name) }
Get-ChildItem $repoMigrations -Filter 'V*.sql' -File | Copy-Item -Destination $full
Get-ChildItem $repoMigrations -Filter 'V*.sql' -File |
    Where-Object Name -ne 'V2.6.13__create_role_runtime_activation_evidence.sql' |
    Copy-Item -Destination $to2612
Get-ChildItem $repoMigrations -Filter 'V*.sql' -File |
    Where-Object { $_.Name -notin @(
        'V2.6.9__create_role_runtime_snapshot.sql',
        'V2.6.10__harden_role_runtime_persistence_integrity.sql',
        'V2.6.11__repair_role_runtime_historical_integrity_guard.sql',
        'V2.6.12__fix_role_runtime_resolver_version_collation.sql',
        'V2.6.13__create_role_runtime_activation_evidence.sql') } |
    Copy-Item -Destination $to268

$servers = @()
$failures = [System.Collections.Generic.List[string]]::new()
$checks = [System.Collections.Generic.List[string]]::new()

function New-IsolatedMysql([string]$Name,[int]$Port) {
    if (Get-NetTCPConnection -LocalPort $Port -ErrorAction SilentlyContinue | Where-Object State -ne 'TimeWait') { throw "Port in use: $Port" }
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
        if($code -eq 0){return @{Name=$Name;Port=$Port;Root=$root}}
        Start-Sleep -Milliseconds 300
    }
    throw "$Name did not become ready"
}

function Invoke-Mysql($Server,[string]$Sql,[switch]$Raw,[string]$Database='enterprise_platform') {
    $args=@('--protocol=TCP','--host=127.0.0.1',"--port=$($Server.Port)",'--user=root')
    if($Database){$args += "--database=$Database"}; if($Raw){$args += @('--batch','--raw','--skip-column-names')}
    $old=$ErrorActionPreference; $ErrorActionPreference='Continue'
    $out=&$mysql @args "--execute=$Sql" 2>&1; $code=$LASTEXITCODE; $ErrorActionPreference=$old
    return @{ExitCode=$code;Output=($out -join "`n")}
}

function Invoke-MysqlFile($Server,[string]$File,[string]$Database='') {
    $args=@('--protocol=TCP','--host=127.0.0.1',"--port=$($Server.Port)",'--user=root','--default-character-set=utf8mb4')
    if($Database){$args += "--database=$Database"}
    $old=$ErrorActionPreference; $ErrorActionPreference='Continue'
    $out=&$mysql @args "--execute=source $((Resolve-Path $File).Path.Replace('\','/'))" 2>&1
    $code=$LASTEXITCODE; $ErrorActionPreference=$old
    if($code -ne 0){throw "Baseline failed: $File $($out -join ' ')"}
}

function Initialize-Baseline($Server) {
    foreach($name in $baselineFiles){ Invoke-MysqlFile $Server (Join-Path $baselineCopy $name) $(if($name -eq '01_database.sql'){''}else{'enterprise_platform'}) }
}

function Invoke-FlywayRaw($Server,[string]$Location,[string]$Command,[string]$Target='') {
    $args=@("-url=jdbc:mysql://127.0.0.1:$($Server.Port)/enterprise_platform",'-user=root','-password=',
        "-locations=filesystem:$($Location.Replace('\','/'))",'-baselineOnMigrate=true','-baselineVersion=2.0.0',
        '-validateMigrationNaming=true','-cleanDisabled=true','-connectRetries=10')
    if($Target){$args += "-target=$Target"}; $args += $Command
    $old=$ErrorActionPreference; $ErrorActionPreference='Continue'; $out=&$flyway @args 2>&1; $code=$LASTEXITCODE; $ErrorActionPreference=$old
    $log=Join-Path $evidence "$($Server.Name)-$Command-$([DateTime]::UtcNow.Ticks).log"; $out | Set-Content $log -Encoding utf8
    return @{ExitCode=$code;Output=($out -join "`n");Log=$log}
}

function Invoke-Flyway($Server,[string]$Location,[string]$Command,[string]$Target='') {
    $r=Invoke-FlywayRaw $Server $Location $Command $Target
    if($r.ExitCode -ne 0){throw "Flyway $Command failed for $($Server.Name): $($r.Output)"}; return $r
}

function Get-Fingerprint($Server) {
    $args=@('--protocol=TCP','--host=127.0.0.1',"--port=$($Server.Port)",'--user=root','--database=enterprise_platform','--batch','--raw','--skip-column-names')
    $rows=Get-Content $fingerprintSql -Raw | &$mysql @args
    if($LASTEXITCODE -ne 0){throw 'Fingerprint query failed'}
    $triggers=&$mysql @args "--execute=SELECT CONCAT('TRIGGER|',trigger_name,'|',event_manipulation,'|',event_object_table,'|',action_timing,'|',REPLACE(REPLACE(action_statement,CHAR(13),' '),CHAR(10),' ')) FROM information_schema.triggers WHERE trigger_schema=DATABASE() ORDER BY trigger_name"
    $all=@($rows)+@($triggers)
    $workflow=$all | Where-Object {$_ -match '^\w+\|workflow_' -or $_ -match '^\w+\|role_runtime_' -or $_ -match '^TRIGGER\|.*\|(workflow_|role_runtime_)'}
    $role=$all | Where-Object {$_ -match '^\w+\|role_runtime_' -or $_ -match '^\w+\|workflow_role_runtime_' -or $_ -match '^TRIGGER\|.*\|(role_runtime_|workflow_role_runtime_)'}
    $result=@{}
    foreach($pair in @(@('full',$all),@('workflow',$workflow),@('role',$role))){
        $file=Join-Path $evidence "$($Server.Name)-$($pair[0])-schema.txt"
        [IO.File]::WriteAllText($file,(($pair[1] -join "`n")+"`n"),[Text.UTF8Encoding]::new($false))
        $result[$pair[0]]=(Get-FileHash $file -Algorithm SHA256).Hash.ToLowerInvariant()
    }; return $result
}

function Assert-Rejected($Server,[string]$Name,[string]$Sql) {
    $r=Invoke-Mysql $Server $Sql; $pass=$r.ExitCode -ne 0
    $checks.Add("$Name`t$(if($pass){'PASS'}else{'FAIL_ACCEPTED'})`t$($r.Output.Replace("`n",' '))")
    if(-not $pass){$failures.Add("Database accepted invalid case: $Name")}; return $pass
}

function RequestSql([long]$Id,[string]$Activation,[string]$Status='PERSISTED',[string]$Binding='') {
    if(-not $Binding){$Binding='b'*64}
    return "INSERT INTO role_runtime_activation_request(id,activation_id,resolver_code,resolver_version,contract_hash,binding_hash,candidate_hash,directory_contract_hash,directory_revision,activation_hash,approval_evidence_hash,business_scope,effective_at,requested_by,status,created_by,updated_by,deleted,delete_token,version) VALUES($Id,'$Activation','ROLE_DIRECTORY','ROLE_DIRECTORY_V1',REPEAT('a',64),'$Binding',REPEAT('c',64),REPEAT('d',64),12,REPEAT('e',64),REPEAT('f',64),'SANDBOX_ROLE_APPROVAL','2026-08-13 10:00:00.000','validator','$Status','validator','validator',0,0,0)"
}

try {
    $fresh=New-IsolatedMysql 'fresh' ($BasePort+1); $upgrade=New-IsolatedMysql 'upgrade' ($BasePort+2)
    $forward=New-IsolatedMysql 'forward' ($BasePort+3); $guard=New-IsolatedMysql 'guard' ($BasePort+4)
    foreach($server in @($fresh,$upgrade,$forward,$guard)){Initialize-Baseline $server}

    Invoke-Flyway $fresh $full migrate '2.6.13' | Out-Null; Invoke-Flyway $fresh $full validate | Out-Null
    $freshNoop=Invoke-Flyway $fresh $full migrate '2.6.13'; Invoke-Flyway $fresh $full info | Out-Null

    Invoke-Flyway $upgrade $to2612 migrate '2.6.12' | Out-Null
    $upgradeBefore=[int](Invoke-Mysql $upgrade 'SELECT COUNT(*) FROM flyway_schema_history WHERE success=1' -Raw).Output.Trim()
    Invoke-Flyway $upgrade $full migrate '2.6.13' | Out-Null; Invoke-Flyway $upgrade $full validate | Out-Null
    $upgradeNoop=Invoke-Flyway $upgrade $full migrate '2.6.13'; $upgradeAfter=[int](Invoke-Mysql $upgrade 'SELECT COUNT(*) FROM flyway_schema_history WHERE success=1' -Raw).Output.Trim()

    Invoke-Flyway $forward $to268 migrate '2.6.8' | Out-Null
    $forwardBefore=[int](Invoke-Mysql $forward 'SELECT COUNT(*) FROM flyway_schema_history WHERE success=1' -Raw).Output.Trim()
    Invoke-Flyway $forward $full migrate '2.6.13' | Out-Null; Invoke-Flyway $forward $full validate | Out-Null
    $forwardNoop=Invoke-Flyway $forward $full migrate '2.6.13'; $forwardAfter=[int](Invoke-Mysql $forward 'SELECT COUNT(*) FROM flyway_schema_history WHERE success=1' -Raw).Output.Trim()

    if($upgradeAfter-$upgradeBefore -ne 1){$failures.Add('Upgrade did not apply exactly V2.6.13')}
    if($forwardAfter-$forwardBefore -ne 5){$failures.Add('Forward did not apply exactly V2.6.9-V2.6.13')}
    foreach($noop in @($freshNoop,$upgradeNoop,$forwardNoop)){if($noop.Output -notmatch 'No migration necessary'){$failures.Add('Second migrate was not no-op')}}

    $fpFresh=Get-Fingerprint $fresh; $fpUpgrade=Get-Fingerprint $upgrade; $fpForward=Get-Fingerprint $forward
    foreach($scope in @('full','workflow','role')){if($fpFresh[$scope] -ne $fpUpgrade[$scope] -or $fpFresh[$scope] -ne $fpForward[$scope]){$failures.Add("Fingerprint mismatch: $scope")}}

    $metadata=Invoke-Mysql $upgrade @"
SELECT 'TABLES',COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('role_runtime_activation_request','role_runtime_activation_approval','role_runtime_activation_evidence')
UNION ALL SELECT 'COLUMNS',COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name IN ('role_runtime_activation_request','role_runtime_activation_approval','role_runtime_activation_evidence')
UNION ALL SELECT 'INDEXES',COUNT(DISTINCT CONCAT(table_name,':',index_name)) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name IN ('role_runtime_activation_request','role_runtime_activation_approval','role_runtime_activation_evidence')
UNION ALL SELECT 'FOREIGN_KEYS',COUNT(*) FROM information_schema.table_constraints WHERE table_schema=DATABASE() AND table_name IN ('role_runtime_activation_request','role_runtime_activation_approval','role_runtime_activation_evidence') AND constraint_type='FOREIGN KEY'
UNION ALL SELECT 'CHECKS',COUNT(*) FROM information_schema.table_constraints WHERE table_schema=DATABASE() AND table_name IN ('role_runtime_activation_request','role_runtime_activation_approval','role_runtime_activation_evidence') AND constraint_type='CHECK'
UNION ALL SELECT 'TRIGGERS',COUNT(*) FROM information_schema.triggers WHERE trigger_schema=DATABASE() AND event_object_table IN ('role_runtime_activation_request','role_runtime_activation_approval','role_runtime_activation_evidence');
"@ -Raw
    $metadata.Output | Set-Content (Join-Path $evidence 'schema-counts.txt') -Encoding utf8

    # Pre-DDL guard: emulate a partial prior installation after V2.6.12.
    Invoke-Flyway $guard $to2612 migrate '2.6.12' | Out-Null
    $partial=Invoke-Mysql $guard 'CREATE TABLE role_runtime_activation_request(id BIGINT PRIMARY KEY) ENGINE=InnoDB'
    if($partial.ExitCode-ne0){throw $partial.Output}
    $guardResult=Invoke-FlywayRaw $guard $full migrate '2.6.13'
    if($guardResult.ExitCode -eq 0){$failures.Add('Partial-install pre-DDL guard did not fail')}
    $partialObjects=(Invoke-Mysql $guard "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('role_runtime_activation_approval','role_runtime_activation_evidence')" -Raw).Output.Trim()
    $partialTriggers=(Invoke-Mysql $guard "SELECT COUNT(*) FROM information_schema.triggers WHERE trigger_schema=DATABASE() AND event_object_table LIKE 'role_runtime_activation_%'" -Raw).Output.Trim()
    if($partialObjects-ne'0' -or $partialTriggers-ne'0'){$failures.Add('Guard failure left permanent V2.6.13 objects')}

    # Database integrity matrix on the isolated Upgrade schema.
    Assert-Rejected $upgrade 'missing_activation_request_approval' "INSERT INTO role_runtime_activation_approval(id,activation_id,approver_type,approver_id,decision,reason,source_evidence_hash,decision_hash,activation_hash,contract_hash,binding_hash,decision_time,created_by,updated_by,deleted,delete_token,version) VALUES(9001,'MISSING','BUSINESS_OWNER','u1','APPROVE','ok',REPEAT('a',64),REPEAT('b',64),REPEAT('e',64),REPEAT('a',64),REPEAT('b',64),NOW(3),'v','v',0,0,0)" | Out-Null
    Invoke-Mysql $upgrade (RequestSql 9100 'ACT-NO-APPROVAL') | Out-Null
    $evidenceWithoutApproval=Assert-Rejected $upgrade 'evidence_without_approval' "INSERT INTO role_runtime_activation_evidence(id,approval_id,activation_id,evidence_type,evidence_hash,activation_hash,contract_hash,binding_hash,canonical_version,created_by,updated_by,deleted,delete_token,version) VALUES(9101,9999,'ACT-NO-APPROVAL','BINDING',REPEAT('b',64),REPEAT('e',64),REPEAT('a',64),REPEAT('b',64),'ROLE_RUNTIME_ACTIVATION_CANONICAL_V1','v','v',0,0,0)"
    Assert-Rejected $upgrade 'hash_length' (RequestSql 9200 'ACT-HASH-LEN' 'PERSISTED' ('b'*63)) | Out-Null
    Assert-Rejected $upgrade 'hash_uppercase' (RequestSql 9201 'ACT-HASH-UPPER' 'PERSISTED' ('B'*64)) | Out-Null
    Invoke-Mysql $upgrade (RequestSql 9300 'ACT-CONTRACT-DRIFT') | Out-Null
    $contractDrift=Assert-Rejected $upgrade 'resolver_contract_mismatch' "INSERT INTO role_runtime_activation_approval(id,activation_id,approver_type,approver_id,decision,reason,source_evidence_hash,decision_hash,activation_hash,contract_hash,binding_hash,decision_time,created_by,updated_by,deleted,delete_token,version) VALUES(9301,'ACT-CONTRACT-DRIFT','BUSINESS_OWNER','u1','APPROVE','ok',REPEAT('1',64),REPEAT('2',64),REPEAT('e',64),REPEAT('9',64),REPEAT('b',64),NOW(3),'v','v',0,0,0)"
    Invoke-Mysql $upgrade (RequestSql 9302 'ACT-BINDING-DRIFT') | Out-Null
    $bindingDrift=Assert-Rejected $upgrade 'binding_hash_drift' "INSERT INTO role_runtime_activation_approval(id,activation_id,approver_type,approver_id,decision,reason,source_evidence_hash,decision_hash,activation_hash,contract_hash,binding_hash,decision_time,created_by,updated_by,deleted,delete_token,version) VALUES(9303,'ACT-BINDING-DRIFT','BUSINESS_OWNER','u1','APPROVE','ok',REPEAT('1',64),REPEAT('2',64),REPEAT('e',64),REPEAT('a',64),REPEAT('9',64),NOW(3),'v','v',0,0,0)"
    Invoke-Mysql $upgrade (RequestSql 9400 'ACT-DUP') | Out-Null
    Assert-Rejected $upgrade 'duplicate_activation' (RequestSql 9401 'ACT-DUP') | Out-Null
    Assert-Rejected $upgrade 'illegal_status' (RequestSql 9500 'ACT-STATUS' 'ENABLED') | Out-Null
    $badToken=(RequestSql 9501 'ACT-TOKEN').Replace(',0,0,0)',',0,5,0)')
    Assert-Rejected $upgrade 'illegal_delete_token' $badToken | Out-Null

    # Legal append-only chain.
    Invoke-Mysql $upgrade (RequestSql 9600 'ACT-LEGAL') | Out-Null
    foreach($item in @(@(9601,'BUSINESS_OWNER','1'),@(9602,'SECURITY_AUDIT','2'),@(9603,'RELEASE_APPROVER','3'))){
        $r=Invoke-Mysql $upgrade "INSERT INTO role_runtime_activation_approval(id,activation_id,approver_type,approver_id,decision,reason,source_evidence_hash,decision_hash,activation_hash,contract_hash,binding_hash,decision_time,created_by,updated_by,deleted,delete_token,version) VALUES($($item[0]),'ACT-LEGAL','$($item[1])','$($item[1])','APPROVE','approved',REPEAT('$($item[2])',64),SHA2(CONCAT('decision','$($item[0])'),256),REPEAT('e',64),REPEAT('a',64),REPEAT('b',64),NOW(3),'v','v',0,0,0)"; if($r.ExitCode-ne0){$failures.Add("Legal approval insert failed: $($item[1])")}
    }
    $eid=9610; foreach($type in @('ACTIVATION_APPROVAL','RESOLVER_CONTRACT','BINDING','CANDIDATE','DIRECTORY')){
        $r=Invoke-Mysql $upgrade "INSERT INTO role_runtime_activation_evidence(id,approval_id,activation_id,evidence_type,evidence_hash,activation_hash,contract_hash,binding_hash,canonical_version,created_by,updated_by,deleted,delete_token,version) VALUES($eid,9603,'ACT-LEGAL','$type',SHA2('$type',256),REPEAT('e',64),REPEAT('a',64),REPEAT('b',64),'ROLE_RUNTIME_ACTIVATION_CANONICAL_V1','v','v',0,0,0)"; if($r.ExitCode-ne0){$failures.Add("Legal evidence insert failed: $type")}; $eid++
    }
    Assert-Rejected $upgrade 'approval_update' "UPDATE role_runtime_activation_approval SET reason='changed' WHERE id=9601" | Out-Null
    Assert-Rejected $upgrade 'approval_delete' 'DELETE FROM role_runtime_activation_approval WHERE id=9601' | Out-Null
    Assert-Rejected $upgrade 'evidence_update' "UPDATE role_runtime_activation_evidence SET evidence_hash=REPEAT('a',64) WHERE id=9610" | Out-Null
    Assert-Rejected $upgrade 'evidence_delete' 'DELETE FROM role_runtime_activation_evidence WHERE id=9610' | Out-Null

    # Transaction rollback after a rejected evidence row.
    $tx=Invoke-Mysql $upgrade "START TRANSACTION; $(RequestSql 9700 'ACT-ROLLBACK'); INSERT INTO role_runtime_activation_approval(id,activation_id,approver_type,approver_id,decision,reason,source_evidence_hash,decision_hash,activation_hash,contract_hash,binding_hash,decision_time,created_by,updated_by,deleted,delete_token,version) VALUES(9701,'ACT-ROLLBACK','RELEASE_APPROVER','u1','APPROVE','ok',REPEAT('1',64),REPEAT('2',64),REPEAT('e',64),REPEAT('a',64),REPEAT('b',64),NOW(3),'v','v',0,0,0); INSERT INTO role_runtime_activation_evidence(id,approval_id,activation_id,evidence_type,evidence_hash,activation_hash,contract_hash,binding_hash,canonical_version,created_by,updated_by,deleted,delete_token,version) VALUES(9702,9701,'ACT-ROLLBACK','BINDING','bad',REPEAT('e',64),REPEAT('a',64),REPEAT('b',64),'ROLE_RUNTIME_ACTIVATION_CANONICAL_V1','v','v',0,0,0); COMMIT;"
    if($tx.ExitCode-eq0){$failures.Add('Transaction invalid evidence unexpectedly succeeded')}
    $rollbackCount=(Invoke-Mysql $upgrade "SELECT (SELECT COUNT(*) FROM role_runtime_activation_request WHERE activation_id='ACT-ROLLBACK')+(SELECT COUNT(*) FROM role_runtime_activation_evidence WHERE activation_id='ACT-ROLLBACK')" -Raw).Output.Trim()
    if($rollbackCount-ne'0'){$failures.Add('Failed evidence transaction was not fully rolled back')}

    # Two isolated clients contend on the same stable activation key.
    $concurrentSql1="START TRANSACTION; $(RequestSql 9800 'ACT-CONCURRENT'); DO SLEEP(1); COMMIT;"
    $concurrentSql2="$(RequestSql 9801 'ACT-CONCURRENT');"
    $runner={param($client,$port,$sql) $output=&$client --protocol=TCP --host=127.0.0.1 "--port=$port" --user=root --database=enterprise_platform "--execute=$sql" 2>&1; [pscustomobject]@{ExitCode=$LASTEXITCODE;Output=($output-join"`n")}}
    $p1=Start-Job -ScriptBlock $runner -ArgumentList $mysql,$upgrade.Port,$concurrentSql1
    Start-Sleep -Milliseconds 150
    $p2=Start-Job -ScriptBlock $runner -ArgumentList $mysql,$upgrade.Port,$concurrentSql2
    Wait-Job $p1,$p2 | Out-Null
    $r1=Receive-Job $p1; $r2=Receive-Job $p2; Remove-Job $p1,$p2
    $r1.Output | Set-Content (Join-Path $evidence 'concurrent-1.err') -Encoding utf8
    $r2.Output | Set-Content (Join-Path $evidence 'concurrent-2.err') -Encoding utf8
    $winnerCount=(Invoke-Mysql $upgrade "SELECT COUNT(*) FROM role_runtime_activation_request WHERE activation_id='ACT-CONCURRENT'" -Raw).Output.Trim()
    $concurrencyErrors=(($r1.Output)+($r2.Output))
    if($winnerCount-ne'1'){$failures.Add('Concurrent activation did not produce one winner')}
    if($concurrencyErrors-match '1213|1205'){$failures.Add('Concurrent activation produced deadlock/lock timeout')}

    $history=Invoke-Mysql $upgrade "SELECT installed_rank,version,description,type,checksum,success FROM flyway_schema_history ORDER BY installed_rank" -Raw
    $history.Output | Set-Content (Join-Path $evidence 'flyway-history.txt') -Encoding utf8
    $checksum=(Invoke-Mysql $upgrade "SELECT checksum FROM flyway_schema_history WHERE version='2.6.13' AND success=1" -Raw).Output.Trim()
    $summary=[ordered]@{
        result=if($failures.Count-eq0){'PASS'}else{'FAIL'}; mysql='8.4.9'; flyway='13.0.0';
        v2613_sha256=$expectedV2613Sha; v2613_flyway_checksum=$checksum;
        fresh_fingerprint=$fpFresh; upgrade_fingerprint=$fpUpgrade; forward_fingerprint=$fpForward;
        schema_counts=$metadata.Output; guard_partial_objects=$partialObjects; guard_partial_triggers=$partialTriggers;
        evidence_without_approval_rejected=$evidenceWithoutApproval;
        resolver_contract_mismatch_rejected=$contractDrift; binding_hash_drift_rejected=$bindingDrift;
        transaction_rollback_count=$rollbackCount; concurrent_winner_count=$winnerCount;
        concurrent_errors=$concurrencyErrors; checks=$checks; failures=$failures
    }
    $summary | ConvertTo-Json -Depth 8 | Set-Content (Join-Path $evidence 'summary.json') -Encoding utf8
    if($failures.Count){throw "V2.6.13 validation failed: $($failures -join '; ')"}
}
finally {
    foreach($server in @($fresh,$upgrade,$forward,$guard)){
        if($server){&$mysqladmin --protocol=TCP --host=127.0.0.1 "--port=$($server.Port)" --user=root shutdown 2>$null | Out-Null}
    }
    foreach($process in $servers){if($process -and -not $process.HasExited){Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue}}
}

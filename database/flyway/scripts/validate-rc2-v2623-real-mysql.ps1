[CmdletBinding()]
param(
    [string]$OutputRoot = "D:\codex-rc2-v2623-$((Get-Date).ToString('yyyyMMdd-HHmmss'))",
    [int]$FreshPort = 38241,
    [int]$UpgradePort = 38242,
    [string]$MySqlHome = 'C:\Program Files\MySQL\MySQL Server 8.4',
    [string]$FlywayPath = $env:FLYWAY_COMMAND,
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$MavenPath = $env:MAVEN_CMD
)

$ErrorActionPreference = 'Stop'
$workspace = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$baselineSource = Join-Path $workspace 'database\mysql'
$migrationSource = Join-Path $workspace 'database\migration\mysql'
$inventoryPath = Join-Path $workspace 'database\flyway\migration-inventory.yml'
$manifestPath = Join-Path $migrationSource 'SHA256SUMS'
$backendPath = Join-Path $workspace 'backend'
$outputPath = [IO.Path]::GetFullPath($OutputRoot)
$expectedSha = '874427e1df075042485c9098ad2f1b79dbc632200f01614f85529b30765c169d'
$servers = [Collections.Generic.List[object]]::new()
$checks = [ordered]@{}
$failures = [Collections.Generic.List[string]]::new()
$baselineFiles = @(
    '01_database.sql','02_sys.sql','03_hr.sql','04_party.sql','05_project.sql',
    '06_operation.sql','07_investment.sql','08_data_asset.sql','09_risk.sql',
    '10_init_data.sql','11_sprint_1_user_permissions.sql','12_sprint_1_org_permissions.sql',
    '13_sprint_1_role_permissions.sql','14_sprint_1_rbac_acceptance.sql',
    '15_sprint_1_menu_center.sql','16_sprint_1_log_center.sql',
    'V1.1.0__investment_data_risk_bi.sql')

function Stop-Preflight([string]$Message) {
    [Console]::Error.WriteLine($Message)
    Write-Output 'RC2_S7=FAIL'
    exit 2
}
if ($outputPath -notmatch '^D:\\codex-rc2-v2623-[A-Za-z0-9._-]+$') {
    Stop-Preflight "Unsafe OutputRoot: $outputPath"
}
if (Test-Path -LiteralPath $outputPath) {
    Stop-Preflight "Disposable OutputRoot already exists: $outputPath"
}
if ($FreshPort -eq $UpgradePort -or $FreshPort -eq 34061 -or $UpgradePort -eq 34061) {
    Stop-Preflight 'Ports must be distinct and must not use RC1 port 34061'
}
foreach ($port in @($FreshPort,$UpgradePort)) {
    if ($port -lt 1024 -or $port -gt 65535 -or
            (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue)) {
        Stop-Preflight "Unsafe or occupied validation port: $port"
    }
}
$mysqld = Join-Path $MySqlHome 'bin\mysqld.exe'
$mysql = Join-Path $MySqlHome 'bin\mysql.exe'
$mysqladmin = Join-Path $MySqlHome 'bin\mysqladmin.exe'
foreach ($tool in @($mysqld,$mysql,$mysqladmin,$FlywayPath,$MavenPath,(Join-Path $JavaHome 'bin\java.exe'))) {
    if (-not (Test-Path -LiteralPath $tool -PathType Leaf)) {
        Stop-Preflight "Required tool missing: $tool"
    }
}

$baselineCopy = Join-Path $outputPath 'baseline'
$migrationCopy = Join-Path $outputPath 'migration'
$upgradeCopy = Join-Path $outputPath 'migration-v2622'
$evidenceRoot = Join-Path $outputPath 'evidence'
New-Item -ItemType Directory -Path $baselineCopy,$migrationCopy,$upgradeCopy,$evidenceRoot -Force | Out-Null

function Write-Utf8([string]$Path,[string]$Content) {
    [IO.File]::WriteAllText($Path,$Content,(New-Object Text.UTF8Encoding($false)))
}
function Set-Check([string]$Name,[bool]$Passed,[string]$Proof='') {
    $script:checks[$Name]=$Passed
    if(-not $Passed){$script:failures.Add("$Name :: $Proof")}
}
function Invoke-Native([string]$Executable,[string[]]$Arguments,[string]$WorkingDirectory='') {
    $old=$ErrorActionPreference;$ErrorActionPreference='Continue'
    try {
        if($WorkingDirectory){Push-Location $WorkingDirectory}
        $out=@(& $Executable @Arguments 2>&1);$code=$LASTEXITCODE
    } finally {
        if($WorkingDirectory){Pop-Location};$ErrorActionPreference=$old
    }
    [pscustomobject]@{ExitCode=$code;Output=($out -join "`n")}
}
function Invoke-Sql($Server,[string]$Sql,[switch]$NoHeaders,[switch]$WithoutDatabase) {
    $args=@('--protocol=TCP','--host=127.0.0.1',"--port=$($Server.Port)",'--user=root',
        '--default-character-set=utf8mb4','--batch','--raw')
    if($NoHeaders){$args+='--skip-column-names'}
    if(-not $WithoutDatabase){$args+='--database=enterprise_platform'}
    $args+="--execute=$Sql"
    Invoke-Native $mysql $args
}
function Invoke-SqlFile($Server,[string]$Path,[switch]$WithoutDatabase) {
    $args=@('--protocol=TCP','--host=127.0.0.1',"--port=$($Server.Port)",'--user=root',
        '--default-character-set=utf8mb4')
    if(-not $WithoutDatabase){$args+='--database=enterprise_platform'}
    $args+="--execute=source $($Path.Replace('\','/'))"
    Invoke-Native $mysql $args
}
function Invoke-Flyway($Server,[string]$Command,[string]$Evidence,[string]$Location=$migrationCopy,[string]$Target='') {
    $args=@("-url=jdbc:mysql://127.0.0.1:$($Server.Port)/enterprise_platform",'-user=root','-password=',
        "-locations=filesystem:$($Location.Replace('\','/'))",'-table=flyway_schema_history',
        '-baselineVersion=2.0.0','-baselineDescription=RC2 authoritative baseline',
        '-baselineOnMigrate=false','-validateMigrationNaming=true','-validateOnMigrate=true',
        '-cleanDisabled=true','-outOfOrder=false','-mixed=false','-connectRetries=10')
    if($Target){$args+="-target=$Target"};$args+=$Command
    $result=Invoke-Native $FlywayPath $args
    Write-Utf8 (Join-Path $evidenceRoot $Evidence) ($result.Output+"`n")
    $result
}
function Assert-AndCopyAssets {
    $actualBaseline=@(Get-ChildItem $baselineSource -File -Filter '*.sql'|Sort-Object Name|ForEach-Object Name)
    if(($actualBaseline-join"`n")-cne(($baselineFiles|Sort-Object)-join"`n")){throw '17-file Baseline drift'}
    foreach($name in $baselineFiles){Copy-Item (Join-Path $baselineSource $name) (Join-Path $baselineCopy $name)}
    $assets=@(Get-ChildItem $migrationSource -File -Filter 'V*.sql'|ForEach-Object{
        if($_.Name-notmatch'^V(\d+\.\d+\.\d+)__.+\.sql$'){throw "Bad Migration name: $($_.Name)"}
        [pscustomobject]@{File=$_;Version=[version]$Matches[1];Text=$Matches[1]}
    }|Sort-Object Version)
    if($assets.Count-ne45-or$assets[-1].Text-cne'2.6.23'){throw "Expected 45 through 2.6.23; got $($assets.Count)/$($assets[-1].Text)"}
    if(@($assets|Group-Object Text|Where-Object Count -gt 1).Count){throw 'Duplicate Migration version'}
    $lines=@(Get-Content $manifestPath|Where-Object{$_-match'^[0-9a-f]{64}\s{2}\S+$'})
    if($lines.Count-ne45){throw "SHA manifest count $($lines.Count)/45"}
    $map=@{};foreach($line in $lines){$p=$line-split'\s{2}',2;$map[$p[1]]=$p[0]}
    $inventory=Get-Content $inventoryPath -Raw
    foreach($asset in $assets){
        $name=$asset.File.Name;$hash=(Get-FileHash $asset.File.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        if($map[$name]-cne$hash-or-not$inventory.Contains("database/migration/mysql/$name")){throw "Ungoverned Migration: $name"}
        Copy-Item $asset.File.FullName (Join-Path $migrationCopy $name)
        if($asset.Version-le[version]'2.6.22'){Copy-Item $asset.File.FullName (Join-Path $upgradeCopy $name)}
    }
    $sha=(Get-FileHash (Join-Path $migrationCopy 'V2.6.23__add_workflow_realtime_eligibility_remark.sql') -Algorithm SHA256).Hash.ToLowerInvariant()
    if($sha-cne$expectedSha){throw "V2.6.23 SHA drift: $sha"}
}
function Start-Database([string]$Name,[int]$Port) {
    $root=Join-Path $outputPath $Name;$data=Join-Path $root 'data';New-Item -ItemType Directory $data -Force|Out-Null
    $ini=Join-Path $root 'my.ini';$log=Join-Path $root 'mysql.err'
    Write-Utf8 $ini @"
[mysqld]
basedir=$($MySqlHome.Replace('\','/'))
datadir=$($data.Replace('\','/'))
port=$Port
bind-address=127.0.0.1
mysqlx=0
skip-log-bin
character-set-server=utf8mb4
collation-server=utf8mb4_general_ci
log-error=$($log.Replace('\','/'))
pid-file=$((Join-Path $root 'mysql.pid').Replace('\','/'))
secure-file-priv=""
"@
    $init=Invoke-Native $mysqld @("--defaults-file=$ini",'--initialize-insecure')
    if($init.ExitCode){throw "MySQL initialize failed: $($init.Output)"}
    $process=Start-Process $mysqld -ArgumentList "--defaults-file=$ini" -WindowStyle Hidden -PassThru
    $server=[pscustomobject]@{Name=$Name;Port=$Port;Root=$root;Data=$data;Process=$process;Version=$null};$servers.Add($server)
    for($i=0;$i-lt120;$i++){
        $ping=Invoke-Native $mysqladmin @('--protocol=TCP','--host=127.0.0.1',"--port=$Port",'--user=root','ping')
        if(-not$ping.ExitCode){
            $id=Invoke-Sql $server "SELECT CONCAT(@@port,'|',@@datadir,'|',VERSION())" -NoHeaders -WithoutDatabase
            $parts=$id.Output.Trim()-split'\|',3
            $reported=[IO.Path]::GetFullPath(($parts[1]-replace'/','\')).TrimEnd('\')
            if([int]$parts[0]-ne$Port-or$reported-cne$data.TrimEnd('\')-or$parts[2]-notmatch'^8\.4\.'){throw "MySQL identity mismatch: $($id.Output)"}
            $server.Version=$parts[2];return $server
        };Start-Sleep -Milliseconds 250
    };throw "MySQL start timeout: $Name"
}
function Initialize-Baseline($Server) {
    $log=[Text.StringBuilder]::new()
    foreach($name in $baselineFiles){
        $r=Invoke-SqlFile $Server (Join-Path $baselineCopy $name) -WithoutDatabase:($name-eq'01_database.sql')
        [void]$log.AppendLine("$name exit=$($r.ExitCode)");[void]$log.AppendLine($r.Output)
        if($r.ExitCode){throw "Baseline failed: $name"}
    };Write-Utf8 (Join-Path $evidenceRoot "$($Server.Name)-baseline.log") $log.ToString()
}
function Get-History($Server) {
    $r=Invoke-Sql $Server "SELECT IFNULL((SELECT version FROM flyway_schema_history WHERE success=1 AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1),'<NULL>'),SUM(success=0),SUM(version='2.6.23' AND success=1),COUNT(*) FROM flyway_schema_history" -NoHeaders
    if($r.ExitCode){throw $r.Output};$p=$r.Output.Trim()-split"`t"
    [pscustomobject]@{Latest=$p[0];Failed=[int]$p[1];Candidate=[int]$p[2];Rows=[int]$p[3]}
}
function Get-Fingerprint($Server) {
    $queries=@(
      "SELECT CONCAT(table_name,'|',engine,'|',table_collation,'|',IFNULL(table_comment,'')) FROM information_schema.tables WHERE table_schema='enterprise_platform' AND table_type='BASE TABLE' ORDER BY table_name",
      "SELECT CONCAT(table_name,'|',ordinal_position,'|',column_name,'|',column_type,'|',is_nullable,'|',IFNULL(column_default,'<NULL>'),'|',IFNULL(collation_name,'<NULL>'),'|',extra,'|',IFNULL(column_comment,'')) FROM information_schema.columns WHERE table_schema='enterprise_platform' ORDER BY table_name,ordinal_position",
      "SELECT CONCAT(table_name,'|',index_name,'|',non_unique,'|',seq_in_index,'|',IFNULL(column_name,'<NULL>'),'|',IFNULL(expression,'<NULL>')) FROM information_schema.statistics WHERE table_schema='enterprise_platform' ORDER BY table_name,index_name,seq_in_index",
      "SELECT CONCAT(trigger_name,'|',event_manipulation,'|',event_object_table,'|',action_timing,'|',action_statement) FROM information_schema.triggers WHERE trigger_schema='enterprise_platform' ORDER BY trigger_name",
      "SELECT CONCAT(constraint_name,'|',table_name,'|',referenced_table_name,'|',unique_constraint_name,'|',update_rule,'|',delete_rule) FROM information_schema.referential_constraints WHERE constraint_schema='enterprise_platform' ORDER BY table_name,constraint_name",
      "SELECT CONCAT(tc.table_name,'|',tc.constraint_name,'|',cc.check_clause) FROM information_schema.table_constraints tc JOIN information_schema.check_constraints cc ON cc.constraint_schema=tc.constraint_schema AND cc.constraint_name=tc.constraint_name WHERE tc.constraint_schema='enterprise_platform' AND tc.constraint_type='CHECK' ORDER BY tc.table_name,tc.constraint_name")
    $text=[Text.StringBuilder]::new();foreach($q in $queries){$r=Invoke-Sql $Server $q -NoHeaders;if($r.ExitCode){throw$r.Output};[void]$text.AppendLine($r.Output)}
    $path=Join-Path $evidenceRoot "$($Server.Name)-schema.txt";Write-Utf8 $path $text.ToString()
    (Get-FileHash $path -Algorithm SHA256).Hash.ToLowerInvariant()
}
function Assert-RemarkSchema($Server) {
    $q=@"
SELECT table_name,column_type,is_nullable,IF(column_default IS NULL,'<NULL>',column_default),character_set_name,collation_name,
       ordinal_position-(SELECT ordinal_position FROM information_schema.columns d WHERE d.table_schema=c.table_schema AND d.table_name=c.table_name AND d.column_name='delete_token')
FROM information_schema.columns c
WHERE table_schema='enterprise_platform' AND column_name='remark'
  AND table_name IN ('workflow_role_realtime_eligibility_capability_evidence','workflow_role_realtime_eligibility_event','workflow_role_realtime_eligibility_validator_evidence')
ORDER BY table_name
"@
    $r=Invoke-Sql $Server $q -NoHeaders;if($r.ExitCode){throw$r.Output}
    Write-Utf8 (Join-Path $evidenceRoot "$($Server.Name)-remark-schema.txt") ($r.Output+"`n")
    $rows=@($r.Output.Trim()-split"`n")
    Set-Check "$($Server.Name)_REMARK_SCHEMA" ($rows.Count-eq3-and@($rows|Where-Object{$_-notmatch"`tvarchar\(500\)`tYES`t<NULL>`tutf8mb4`tutf8mb4_0900_ai_ci`t1$"}).Count-eq0) $r.Output
}
function Insert-HistoricalRows($Server) {
    $sql=@"
SET FOREIGN_KEY_CHECKS=0;
INSERT INTO workflow_role_realtime_eligibility_validator_evidence(id,evidence_id,validator_code,validator_order,status,reason_code,evidence_hash,checked_at,canonical_version,deleted,delete_token,version)
VALUES(9900101,9900001,'VALIDATOR_HIST',1,'PASS','RC2_S7',SHA2(CONCAT('ROLE_REALTIME_VALIDATOR_EVIDENCE_V1','1:1','14:VALIDATOR_HIST','4:PASS','6:RC2_S7','2026-08-24T01:00:00.000000Z'),256),'2026-08-24 01:00:00.000','ROLE_REALTIME_ELIGIBILITY_CANONICAL_V1',0,0,0);
INSERT INTO workflow_role_realtime_eligibility_capability_evidence(id,evidence_id,capability_code,validator_code,status,decision,provider_version,policy_version,evidence_hash,checked_at,canonical_version,deleted,delete_token,version)
VALUES(9900201,9900001,'ROLE_MEMBERSHIP','VALIDATOR_HIST','PASS','PASS','PROVIDER_V1','POLICY_V1',SHA2(CONCAT('ROLE_REALTIME_CAPABILITY_EVIDENCE_V1','15:ROLE_MEMBERSHIP','14:VALIDATOR_HIST','4:PASS','4:PASS','11:PROVIDER_V1','9:POLICY_V1','2026-08-24T01:00:00.000000Z','null'),256),'2026-08-24 01:00:00.000','ROLE_REALTIME_ELIGIBILITY_CANONICAL_V1',0,0,0);
INSERT INTO workflow_role_realtime_eligibility_event(id,evidence_id,sequence_no,event_type,claim_id,reason_code,previous_event_hash,event_hash,occurred_at,operator_id,idempotency_key,canonical_version,deleted,delete_token,version)
VALUES(9900301,9900001,1,'PREPARED',NULL,'RC2_S7',NULL,SHA2(CONCAT('ROLE_REALTIME_ELIGIBILITY_EVENT_V1','7:9900001','1:1','8:PREPARED','null','6:RC2_S7','null','2026-08-24T01:00:00.000000Z','6:RC2_S7','11:RC2-S7-HIST'),256),'2026-08-24 01:00:00.000','RC2_S7','RC2-S7-HIST','ROLE_REALTIME_ELIGIBILITY_PERSISTENCE_CANONICAL_V1',0,0,0);
SET FOREIGN_KEY_CHECKS=1;
"@
    $r=Invoke-Sql $Server $sql;if($r.ExitCode){throw "Historical fixture failed: $($r.Output)"}
}
function Stop-Databases {
    foreach($server in $servers){
        [void](Invoke-Native $mysqladmin @('--protocol=TCP','--host=127.0.0.1',"--port=$($server.Port)",'--user=root','shutdown'))
        if(-not$server.Process.HasExited){$server.Process.WaitForExit(10000)|Out-Null}
    }
}
function Remove-Datadirs {
    foreach($server in $servers){
        $resolved=[IO.Path]::GetFullPath($server.Root)
        if(-not$resolved.StartsWith($outputPath+'\',[StringComparison]::OrdinalIgnoreCase)){throw "Unsafe cleanup: $resolved"}
        if(Test-Path $resolved){Remove-Item -LiteralPath $resolved -Recurse -Force}
    }
}

$fresh=$null;$upgrade=$null;$freshState=$null;$upgradeState=$null;$sourceState=$null
$freshFp=$null;$upgradeFp=$null;$checksum=$null;$freshNoop='FAIL';$upgradeNoop='FAIL';$mapper='FAIL'
try {
    Assert-AndCopyAssets;Set-Check 'MIGRATION_SHA_45_OF_45' $true
    $fv=Invoke-Native $FlywayPath @('-v');if($fv.ExitCode-or$fv.Output-notmatch'13\.0\.0'){throw "Flyway mismatch: $($fv.Output)"}
    $fresh=Start-Database fresh $FreshPort;$upgrade=Start-Database upgrade $UpgradePort
    Initialize-Baseline $fresh;Initialize-Baseline $upgrade
    foreach($server in @($fresh,$upgrade)){ $b=Invoke-Flyway $server baseline "$($server.Name)-baseline-flyway.log";if($b.ExitCode){throw$b.Output} }

    $m=Invoke-Flyway $fresh migrate 'fresh-migrate.log';if($m.ExitCode){throw$m.Output}
    $v=Invoke-Flyway $fresh validate 'fresh-validate.log';Set-Check 'FRESH_STRICT_VALIDATE' (-not$v.ExitCode) $v.Output
    $n=Invoke-Flyway $fresh migrate 'fresh-noop.log';$freshNoop=if(-not$n.ExitCode-and$n.Output-match'(?i)No migration necessary|up to date'){'NO_OP'}else{'FAIL'};Set-Check 'FRESH_NOOP' ($freshNoop-eq'NO_OP') $n.Output
    $freshState=Get-History $fresh;Set-Check 'FRESH_HISTORY' ($freshState.Latest-ceq'2.6.23'-and$freshState.Failed-eq0-and$freshState.Candidate-eq1) ($freshState|ConvertTo-Json -Compress)
    Assert-RemarkSchema $fresh

    $um=Invoke-Flyway $upgrade migrate 'upgrade-to-v2622.log' $upgradeCopy;if($um.ExitCode){throw$um.Output}
    $uv=Invoke-Flyway $upgrade validate 'upgrade-source-validate.log' $upgradeCopy;Set-Check 'UPGRADE_SOURCE_VALIDATE' (-not$uv.ExitCode) $uv.Output
    $sourceState=Get-History $upgrade;Set-Check 'UPGRADE_SOURCE_VERSION' ($sourceState.Latest-ceq'2.6.22'-and$sourceState.Candidate-eq0) ($sourceState|ConvertTo-Json -Compress)
    Insert-HistoricalRows $upgrade
    $um2=Invoke-Flyway $upgrade migrate 'upgrade-v2623.log';if($um2.ExitCode){throw$um2.Output}
    $uv2=Invoke-Flyway $upgrade validate 'upgrade-validate.log';Set-Check 'UPGRADE_STRICT_VALIDATE' (-not$uv2.ExitCode) $uv2.Output
    $un=Invoke-Flyway $upgrade migrate 'upgrade-noop.log';$upgradeNoop=if(-not$un.ExitCode-and$un.Output-match'(?i)No migration necessary|up to date'){'NO_OP'}else{'FAIL'};Set-Check 'UPGRADE_NOOP' ($upgradeNoop-eq'NO_OP') $un.Output
    $upgradeState=Get-History $upgrade;Set-Check 'UPGRADE_HISTORY' ($upgradeState.Latest-ceq'2.6.23'-and$upgradeState.Failed-eq0-and$upgradeState.Candidate-eq1) ($upgradeState|ConvertTo-Json -Compress)
    Assert-RemarkSchema $upgrade
    $historyNull=Invoke-Sql $upgrade "SELECT (SELECT COUNT(*) FROM workflow_role_realtime_eligibility_validator_evidence WHERE id=9900101 AND remark IS NULL)+(SELECT COUNT(*) FROM workflow_role_realtime_eligibility_capability_evidence WHERE id=9900201 AND remark IS NULL)+(SELECT COUNT(*) FROM workflow_role_realtime_eligibility_event WHERE id=9900301 AND remark IS NULL)" -NoHeaders
    Set-Check 'UPGRADE_HISTORY_REMARK_NULL' ($historyNull.Output.Trim()-eq'3') $historyNull.Output

    $checksumResult=Invoke-Sql $fresh "SELECT checksum FROM flyway_schema_history WHERE version='2.6.23' AND success=1" -NoHeaders
    if($checksumResult.Output.Trim()-notmatch'^[-]?\d+$'){throw "Checksum unreadable: $($checksumResult.Output)"};$checksum=[int]$checksumResult.Output.Trim()
    $freshFp=Get-Fingerprint $fresh;$upgradeFp=Get-Fingerprint $upgrade;Set-Check 'FRESH_UPGRADE_SCHEMA_MATCH' ($freshFp-ceq$upgradeFp) "$freshFp/$upgradeFp"

    $oldJava=$env:JAVA_HOME;$oldMavenOpts=$env:MAVEN_OPTS;$env:JAVA_HOME=$JavaHome
    try {
        $env:MAVEN_OPTS=$oldMavenOpts
        $args=@('-Dtest=V2623RealtimeEligibilityRemarkMysqlMappingTest',
            "-Drc2.v2623.mysql.url=jdbc:mysql://127.0.0.1:$FreshPort/enterprise_platform",
            '-Drc2.v2623.mysql.user=root','-Drc2.v2623.mysql.password=','test')
        $mr=Invoke-Native $MavenPath $args $backendPath;Write-Utf8 (Join-Path $evidenceRoot 'real-mapper-test.log') ($mr.Output+"`n")
        $mapper=if(-not$mr.ExitCode-and$mr.Output-match'Tests run: 1, Failures: 0, Errors: 0, Skipped: 0'){'PASS'}else{'FAIL'}
        Set-Check 'REAL_MYSQL_MAPPER_REMARK' ($mapper-eq'PASS') $mr.Output
        $targetDrift=$mr.Output-match'Database mapping report: entity=.*WorkflowRoleRealtimeEligibility(CapabilityEvidence|Event|ValidatorEvidence)Entity.*entityOnly='
        Set-Check 'WORKFLOW_MAPPING_DRIFT_ZERO' (-not$targetDrift) 'Target entity mapping drift found in Spring output'
    } finally {$env:JAVA_HOME=$oldJava;$env:MAVEN_OPTS=$oldMavenOpts}
} catch {$failures.Add($_.Exception.Message)}
finally {
    Stop-Databases;Remove-Datadirs
    $result=if($failures.Count-eq0-and@($checks.Values|Where-Object{-not$_}).Count-eq0){'PASS'}else{'FAIL'}
    $summary=[ordered]@{result=$result;output_root=$outputPath;fresh_mysql_version=if($fresh){$fresh.Version}else{$null};upgrade_mysql_version=if($upgrade){$upgrade.Version}else{$null};fresh_latest=if($freshState){$freshState.Latest}else{$null};upgrade_source=if($sourceState){$sourceState.Latest}else{$null};upgrade_target=if($upgradeState){$upgradeState.Latest}else{$null};fresh_noop=$freshNoop;upgrade_noop=$upgradeNoop;fresh_fingerprint=$freshFp;upgrade_fingerprint=$upgradeFp;v2623_sha256=$expectedSha;v2623_flyway_checksum=$checksum;mapper=$mapper;checks=$checks;failures=@($failures);rc1_test_database_touched=$false;role_runtime='DISABLED';canary='NOT_AUTHORIZED_NOT_ENABLED';kill_switch='STOP_NEW_AND_CLAIM'}
    Write-Utf8 (Join-Path $evidenceRoot 'summary.json') (($summary|ConvertTo-Json -Depth 8)+"`n")
    $checks.GetEnumerator()|ForEach-Object{Write-Output "$($_.Key)=$(if($_.Value){'PASS'}else{'FAIL'})"}
    Write-Output "V2623_SHA256=$expectedSha";Write-Output "V2623_FLYWAY_CHECKSUM=$checksum"
    Write-Output "FRESH_MYSQL_VERSION=$(if($fresh){$fresh.Version}else{''})";Write-Output "FRESH_LATEST_MIGRATION=$(if($freshState){$freshState.Latest}else{''})";Write-Output "FRESH_FAILED_MIGRATIONS=$(if($freshState){$freshState.Failed}else{''})";Write-Output "FRESH_SECOND_MIGRATE=$freshNoop";Write-Output "FRESH_SCHEMA_FINGERPRINT=$freshFp"
    Write-Output "UPGRADE_MYSQL_VERSION=$(if($upgrade){$upgrade.Version}else{''})";Write-Output 'UPGRADE_SOURCE_VERSION=2.6.22';Write-Output "UPGRADE_TARGET_VERSION=$(if($upgradeState){$upgradeState.Latest}else{''})";Write-Output "UPGRADE_FAILED_MIGRATIONS=$(if($upgradeState){$upgradeState.Failed}else{''})";Write-Output "UPGRADE_SECOND_MIGRATE=$upgradeNoop";Write-Output "UPGRADE_SCHEMA_FINGERPRINT=$upgradeFp";Write-Output "EVIDENCE_ROOT=$evidenceRoot";Write-Output "RC2_S7=$result"
    if($result-ne'PASS'){$failures|ForEach-Object{Write-Error $_};exit 2};exit 0
}

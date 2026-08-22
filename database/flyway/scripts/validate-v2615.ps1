param(
    [string]$OutputRoot = "D:\codex-validation-v2615-$((Get-Date).ToString('yyyyMMdd-HHmmss'))",
    [int]$BasePort = 36510
)

$ErrorActionPreference = 'Stop'
$workspace = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$mysqlHome = 'C:\Program Files\MySQL\MySQL Server 8.4'
$mysqld = Join-Path $mysqlHome 'bin\mysqld.exe'
$mysql = Join-Path $mysqlHome 'bin\mysql.exe'
$mysqladmin = Join-Path $mysqlHome 'bin\mysqladmin.exe'
$flyway = 'C:\Users\WUKONG\AppData\Local\Temp\enterprise-v246-acceptance-68bf692b16d34ee191946695ae0bef9b\flyway-fresh\flyway-13.0.0\flyway.cmd'
$migrations = Join-Path $workspace 'database\migration\mysql'
$baselineSource = Join-Path $workspace 'database\mysql'
$fingerprintSql = Join-Path $workspace 'database\mysql\verification\schema_fingerprint.sql'
$expectedSha = 'db1aff944bc4bd1be037292f4cee6502929687b25a1f637301efe80bb768fa44'
$baselineFiles = @('01_database.sql','02_sys.sql','03_hr.sql','04_party.sql','05_project.sql',
    '06_operation.sql','07_investment.sql','08_data_asset.sql','09_risk.sql','10_init_data.sql',
    '11_sprint_1_user_permissions.sql','12_sprint_1_org_permissions.sql','13_sprint_1_role_permissions.sql',
    '14_sprint_1_rbac_acceptance.sql','15_sprint_1_menu_center.sql','16_sprint_1_log_center.sql',
    'V1.1.0__investment_data_risk_bi.sql')

foreach ($asset in @($mysqld,$mysql,$mysqladmin,$flyway,$fingerprintSql)) {
    if (-not (Test-Path -LiteralPath $asset)) { throw "Missing validation asset: $asset" }
}
if (Test-Path -LiteralPath $OutputRoot) { throw "Disposable root already exists: $OutputRoot" }
if (-not $OutputRoot.StartsWith('D:\codex-validation-v2615-')) { throw 'Unsafe validation root' }
$actualSha=(Get-FileHash (Join-Path $migrations 'V2.6.15__create_role_runtime_execution_admission_persistence.sql') -Algorithm SHA256).Hash.ToLowerInvariant()
if ($actualSha -ne $expectedSha) { throw "V2.6.15 asset drift: $actualSha" }

$evidence=Join-Path $OutputRoot 'evidence'; $full=Join-Path $OutputRoot 'migrations-full'; $to2614=Join-Path $OutputRoot 'migrations-to-2614'; $baselineCopy=Join-Path $OutputRoot 'baseline'
New-Item -ItemType Directory -Path $evidence,$full,$to2614,$baselineCopy -Force | Out-Null
Get-ChildItem $migrations -Filter 'V*.sql' -File | Copy-Item -Destination $full
Get-ChildItem $migrations -Filter 'V*.sql' -File | Where-Object Name -ne 'V2.6.15__create_role_runtime_execution_admission_persistence.sql' | Copy-Item -Destination $to2614
foreach($name in $baselineFiles){Copy-Item (Join-Path $baselineSource $name) (Join-Path $baselineCopy $name)}
$servers=@(); $checks=[System.Collections.Generic.List[string]]::new(); $failures=[System.Collections.Generic.List[string]]::new()

function New-IsolatedMysql([string]$Name,[int]$Port) {
    if (Get-NetTCPConnection -LocalPort $Port -ErrorAction SilentlyContinue | Where-Object State -ne 'TimeWait') { throw "Port in use: $Port" }
    $root=Join-Path $OutputRoot $Name; $data=Join-Path $root 'data'; $logs=Join-Path $root 'logs'
    New-Item -ItemType Directory -Path $data,$logs -Force | Out-Null
    $ini=Join-Path $root 'my.ini'
    [IO.File]::WriteAllText($ini,@"
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
"@,[Text.UTF8Encoding]::new($false))
    & $mysqld "--defaults-file=$ini" --initialize-insecure
    if ($LASTEXITCODE -ne 0) { throw "MySQL initialize failed: $Name" }
    $process=Start-Process $mysqld -ArgumentList "--defaults-file=$ini" -WindowStyle Hidden -PassThru
    $script:servers += $process
    foreach($attempt in 1..100){$old=$ErrorActionPreference;$ErrorActionPreference='Continue';&$mysqladmin --protocol=TCP --host=127.0.0.1 --port=$Port --user=root ping 2>$null|Out-Null;$code=$LASTEXITCODE;$ErrorActionPreference=$old;if($code-eq0){return @{Name=$Name;Port=$Port;Root=$root}};Start-Sleep -Milliseconds 300}
    throw "MySQL not ready: $Name"
}

function Invoke-Mysql($Server,[string]$Sql,[string]$Database='enterprise_platform',[switch]$Raw){
    $args=@('--protocol=TCP','--host=127.0.0.1',"--port=$($Server.Port)",'--user=root')
    if($Database){$args+="--database=$Database"};if($Raw){$args+=@('--batch','--raw','--skip-column-names')}
    $old=$ErrorActionPreference;$ErrorActionPreference='Continue';$out=&$mysql @args "--execute=$Sql" 2>&1;$code=$LASTEXITCODE;$ErrorActionPreference=$old
    return @{ExitCode=$code;Output=($out-join"`n")}
}

function Initialize-Baseline($Server){
    foreach($name in $baselineFiles){$file=Join-Path $baselineCopy $name;$args=@('--protocol=TCP','--host=127.0.0.1',"--port=$($Server.Port)",'--user=root','--default-character-set=utf8mb4');if($name-ne'01_database.sql'){$args+='--database=enterprise_platform'};$out=&$mysql @args "--execute=source $($file.Replace('\','/'))" 2>&1;if($LASTEXITCODE-ne0){throw "Baseline failed ${name}: $($out-join' ')"}}
}

function Invoke-Flyway($Server,[string]$Location,[string]$Command,[string]$Target=''){
    $args=@("-url=jdbc:mysql://127.0.0.1:$($Server.Port)/enterprise_platform",'-user=root','-password=',"-locations=filesystem:$($Location.Replace('\','/'))",'-baselineOnMigrate=true','-baselineVersion=2.0.0','-validateMigrationNaming=true','-cleanDisabled=true','-connectRetries=10');if($Target){$args+="-target=$Target"};$args+=$Command
    $old=$ErrorActionPreference;$ErrorActionPreference='Continue';$out=&$flyway @args 2>&1;$code=$LASTEXITCODE;$ErrorActionPreference=$old
    $out|Set-Content (Join-Path $evidence "$($Server.Name)-$Command-$([DateTime]::UtcNow.Ticks).log") -Encoding utf8
    return @{ExitCode=$code;Output=($out-join"`n")}
}

function Assert-Flyway($Server,[string]$Location,[string]$Command,[string]$Target=''){$r=Invoke-Flyway $Server $Location $Command $Target;if($r.ExitCode-ne0){throw "Flyway $Command failed for $($Server.Name): $($r.Output)"};return $r}
function Assert-Rejected($Server,[string]$Name,[string]$Sql){$r=Invoke-Mysql $Server $Sql;$pass=$r.ExitCode-ne0;$checks.Add("$Name`t$(if($pass){'PASS'}else{'FAIL_ACCEPTED'})`t$($r.Output.Replace("`n",' '))");if(-not$pass){$failures.Add("Accepted invalid database case: $Name")};return $pass}

function Get-Fingerprints($Server){
    $args=@('--protocol=TCP','--host=127.0.0.1',"--port=$($Server.Port)",'--user=root','--database=enterprise_platform','--batch','--raw','--skip-column-names')
    $rows=Get-Content $fingerprintSql -Raw|&$mysql @args
    $triggers=&$mysql @args "--execute=SELECT CONCAT('TRIGGER|',trigger_name,'|',event_manipulation,'|',event_object_table,'|',action_timing,'|',REPLACE(REPLACE(action_statement,CHAR(13),' '),CHAR(10),' ')) FROM information_schema.triggers WHERE trigger_schema=DATABASE() ORDER BY trigger_name"
    $all=@($rows)+@($triggers);$workflow=$all|Where-Object{$_-match '^\w+\|workflow_' -or $_-match '^\w+\|role_runtime_' -or $_-match '^TRIGGER\|.*\|(workflow_|role_runtime_)'};$admission=$all|Where-Object{$_-match 'role_runtime_execution_admission'}
    $result=@{};foreach($pair in @(@('full',$all),@('workflow',$workflow),@('admission',$admission))){$file=Join-Path $evidence "$($Server.Name)-$($pair[0])-schema.txt";[IO.File]::WriteAllText($file,(($pair[1]-join"`n")+"`n"),[Text.UTF8Encoding]::new($false));$result[$pair[0]]=(Get-FileHash $file -Algorithm SHA256).Hash.ToLowerInvariant()};return $result
}

try {
    $fresh=New-IsolatedMysql 'fresh' ($BasePort+1);$upgrade=New-IsolatedMysql 'upgrade' ($BasePort+2);$guard=New-IsolatedMysql 'guard' ($BasePort+3)
    Initialize-Baseline $fresh;Initialize-Baseline $upgrade;Initialize-Baseline $guard

    $freshMigrate=Assert-Flyway $fresh $full migrate;Assert-Flyway $fresh $full validate|Out-Null;$freshNoop=Assert-Flyway $fresh $full migrate
    $checks.Add("FRESH_MIGRATE`tPASS");$checks.Add("FRESH_NOOP`t$($freshNoop.Output -match 'No migration necessary')")

    Assert-Flyway $upgrade $to2614 migrate '2.6.14'|Out-Null
    # A canonical legacy row is inserted before V2.6.15; its new columns must remain NULL after upgrade.
    $legacyBefore=(Invoke-Mysql $upgrade "SELECT COUNT(*) FROM workflow_role_binding_candidate_snapshot" -Raw).Output
    $upgradeMigrate=Assert-Flyway $upgrade $full migrate;Assert-Flyway $upgrade $full validate|Out-Null;$upgradeNoop=Assert-Flyway $upgrade $full migrate
    $legacyNull=(Invoke-Mysql $upgrade "SELECT COUNT(*) FROM workflow_role_binding_candidate_snapshot WHERE directory_result_hash IS NOT NULL OR definition_id IS NOT NULL OR node_id IS NOT NULL" -Raw).Output
    $checks.Add("UPGRADE_ONLY_V2615`t$($upgradeMigrate.Output -match 'Migrating schema.*2.6.15')");$checks.Add("UPGRADE_NOOP`t$($upgradeNoop.Output -match 'No migration necessary')");$checks.Add("LEGACY_NOT_BACKFILLED`t$($legacyNull -eq '0')")

    $freshFp=Get-Fingerprints $fresh;$upgradeFp=Get-Fingerprints $upgrade
    foreach($key in @('full','workflow','admission')){$checks.Add("FINGERPRINT_$($key.ToUpper())`t$($freshFp[$key]-eq$upgradeFp[$key])`t$($freshFp[$key])`t$($upgradeFp[$key])")}

    $history=Invoke-Mysql $fresh "SELECT version,description,checksum,success FROM flyway_schema_history WHERE version='2.6.15'" -Raw
    $history.Output|Set-Content (Join-Path $evidence 'v2615-history.txt') -Encoding utf8
    $checksum=(Invoke-Mysql $fresh "SELECT checksum FROM flyway_schema_history WHERE version='2.6.15' AND success=1" -Raw).Output.Trim()
    $structure=Invoke-Mysql $fresh "SELECT table_name,COUNT(*) columns_count FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name IN ('workflow_role_runtime_execution_admission','workflow_role_runtime_execution_admission_evidence','workflow_role_runtime_execution_admission_event','workflow_role_runtime_execution_admission_slot') GROUP BY table_name ORDER BY table_name" -Raw
    $structure.Output|Set-Content (Join-Path $evidence 'structure-counts.txt') -Encoding utf8

    # Guard-failure path: install only through V2.6.14, add a target-name collision, and prove no V2.6.15 ALTER occurred.
    Assert-Flyway $guard $to2614 migrate '2.6.14'|Out-Null
    $r=Invoke-Mysql $guard 'CREATE TABLE workflow_role_runtime_execution_admission(id BIGINT PRIMARY KEY) ENGINE=InnoDB';if($r.ExitCode-ne0){throw $r.Output}
    $before=(Invoke-Mysql $guard "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='workflow_role_binding_candidate_snapshot' AND column_name='directory_result_hash'" -Raw).Output
    $guardRun=Invoke-Flyway $guard $full migrate
    $after=(Invoke-Mysql $guard "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='workflow_role_binding_candidate_snapshot' AND column_name='directory_result_hash'" -Raw).Output
    $guardTargets=(Invoke-Mysql $guard "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('workflow_role_runtime_execution_admission_evidence','workflow_role_runtime_execution_admission_event','workflow_role_runtime_execution_admission_slot')" -Raw).Output
    $guardPass=$guardRun.ExitCode-ne0 -and $before-eq'0' -and $after-eq'0' -and $guardTargets-eq'0';$checks.Add("GUARD_PARTIAL_INSTALL_ZERO_DDL`t$guardPass")
    if(-not$guardPass){$failures.Add('Guard partial-install case did not fail before permanent V2.6.15 DDL')}

    $summary=[ordered]@{result=$(if($failures.Count-eq0){'PASS'}else{'FAIL'});mysql=(&$mysql --version);flyway=((&$flyway -v 2>&1)-join' ');sha256=$actualSha;flywayChecksum=$checksum;freshFingerprint=$freshFp;upgradeFingerprint=$upgradeFp;checks=$checks;failures=$failures}
    $summary|ConvertTo-Json -Depth 6|Set-Content (Join-Path $evidence 'summary.json') -Encoding utf8
    $checks|Set-Content (Join-Path $evidence 'checks.tsv') -Encoding utf8
    $summary|ConvertTo-Json -Depth 6
} finally {
    foreach($server in @($fresh,$upgrade,$guard)){if($null-ne$server){$old=$ErrorActionPreference;$ErrorActionPreference='Continue';&$mysqladmin --protocol=TCP --host=127.0.0.1 --port=$server.Port --user=root shutdown 2>$null|Out-Null;$ErrorActionPreference=$old}}
    foreach($process in $servers){if(-not$process.HasExited){Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue}}
}

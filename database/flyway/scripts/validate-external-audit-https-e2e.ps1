param([string]$OutputRoot="D:\codex-validation-wf-audit-https-$((Get-Date).ToString('yyyyMMdd-HHmmss'))",[int]$MySqlPort=37241,[int]$HttpsPort=38443)
$ErrorActionPreference='Stop';$workspace=(Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
if(Test-Path $OutputRoot){throw 'Disposable root already exists'};if(!$OutputRoot.StartsWith('D:\codex-validation-wf-audit-https-')){throw 'Unsafe root'}
New-Item -ItemType Directory -Path $OutputRoot -Force|Out-Null
$mysqlHome='C:\Program Files\MySQL\MySQL Server 8.4';$mysqld=Join-Path $mysqlHome 'bin\mysqld.exe';$mysql=Join-Path $mysqlHome 'bin\mysql.exe';$mysqladmin=Join-Path $mysqlHome 'bin\mysqladmin.exe'
$flyway='C:\Users\WUKONG\AppData\Local\Temp\enterprise-v246-acceptance-68bf692b16d34ee191946695ae0bef9b\flyway-fresh\flyway-13.0.0\flyway.cmd'
$javaHome='C:\Users\WUKONG\.codex\tmp\wf522-tools\jdk-21.0.12+8';$java=Join-Path $javaHome 'bin\java.exe';$keytool=Join-Path $javaHome 'bin\keytool.exe';$mvn='C:\Users\WUKONG\.codex\tmp\wf522-tools\apache-maven-3.9.9\bin\mvn.cmd'
$data=Join-Path $OutputRoot mysql;$logs=Join-Path $OutputRoot logs;New-Item -ItemType Directory $data,$logs -Force|Out-Null;$ini=Join-Path $OutputRoot my.ini
[IO.File]::WriteAllText($ini,@"
[mysqld]
basedir=C:/Program Files/MySQL/MySQL Server 8.4
datadir=$($data.Replace('\','/'))
port=$MySqlPort
bind-address=127.0.0.1
mysqlx=0
skip-log-bin
character-set-server=utf8mb4
collation-server=utf8mb4_general_ci
log-error=$((Join-Path $logs 'mysql.err').Replace('\','/'))
pid-file=$((Join-Path $OutputRoot 'mysql.pid').Replace('\','/'))
secure-file-priv=""
"@,[Text.UTF8Encoding]::new($false))
$mysqlProcess=$null;$appProcess=$null;$token=[Guid]::NewGuid().ToString('N')+[Guid]::NewGuid().ToString('N');$storePassword=[Guid]::NewGuid().ToString('N')
try{
 &$mysqld "--defaults-file=$ini" --initialize-insecure;if($LASTEXITCODE){throw 'mysql initialize failed'};$mysqlProcess=Start-Process $mysqld -ArgumentList "--defaults-file=$ini" -WindowStyle Hidden -PassThru
 foreach($i in 1..100){$old=$ErrorActionPreference;$ErrorActionPreference='Continue';&$mysqladmin --protocol=TCP --host=127.0.0.1 --port=$MySqlPort --user=root ping 2>$null|Out-Null;$c=$LASTEXITCODE;$ErrorActionPreference=$old;if(!$c){break};Start-Sleep -Milliseconds 300};if($c){throw 'mysql not ready'}
 $baseSource=Join-Path $workspace 'database\mysql';$base=Join-Path $OutputRoot baseline;$migrationCopy=Join-Path $OutputRoot migrations;New-Item -ItemType Directory $base,$migrationCopy -Force|Out-Null;$files=@('01_database.sql','02_sys.sql','03_hr.sql','04_party.sql','05_project.sql','06_operation.sql','07_investment.sql','08_data_asset.sql','09_risk.sql','10_init_data.sql','11_sprint_1_user_permissions.sql','12_sprint_1_org_permissions.sql','13_sprint_1_role_permissions.sql','14_sprint_1_rbac_acceptance.sql','15_sprint_1_menu_center.sql','16_sprint_1_log_center.sql','V1.1.0__investment_data_risk_bi.sql');foreach($n in $files){Copy-Item (Join-Path $baseSource $n) (Join-Path $base $n)};Get-ChildItem (Join-Path $workspace 'database\migration\mysql') -Filter 'V*.sql' -File|Copy-Item -Destination $migrationCopy
 foreach($n in $files){$f=Join-Path $base $n;$a=@('--protocol=TCP','--host=127.0.0.1',"--port=$MySqlPort",'--user=root','--default-character-set=utf8mb4');if($n-ne'01_database.sql'){$a+='--database=enterprise_platform'};&$mysql @a "--execute=source $($f.Replace('\','/'))"|Out-Null;if($LASTEXITCODE){throw "baseline failed $n"}}
 $loc=$migrationCopy.Replace('\','/');$flyArgs=@("-url=jdbc:mysql://127.0.0.1:$MySqlPort/enterprise_platform",'-user=root','-password=',"-locations=filesystem:$loc",'-baselineOnMigrate=true','-baselineVersion=2.0.0','-validateMigrationNaming=true','-cleanDisabled=true','migrate');&$flyway @flyArgs|Set-Content (Join-Path $logs flyway-migrate.log);if($LASTEXITCODE){throw 'flyway failed'}
 $fixture=Join-Path $OutputRoot wf-audit-fixture.sql;Copy-Item (Join-Path $workspace 'database\mysql\manual\wf-audit-final-outbox-fixture.sql') $fixture;&$mysql --protocol=TCP --host=127.0.0.1 --port=$MySqlPort --user=root --database=enterprise_platform "--execute=source $($fixture.Replace('\','/'))"|Out-Null;if($LASTEXITCODE){throw 'audit outbox fixture failed'}
 $serverStore=Join-Path $OutputRoot server.p12;$trustStore=Join-Path $OutputRoot trust.p12;$certificate=Join-Path $OutputRoot localhost.pem
 &$keytool -genkeypair -alias audit-sink -keyalg RSA -keysize 3072 -validity 30 -dname 'CN=localhost,OU=TEST,O=Enterprise Governance,C=CN' -ext 'SAN=dns:localhost' -storetype PKCS12 -keystore $serverStore -storepass $storePassword -keypass $storePassword -noprompt|Out-Null
 &$keytool -exportcert -alias audit-sink -keystore $serverStore -storepass $storePassword -rfc -file $certificate|Out-Null
 &$keytool -importcert -alias audit-sink -file $certificate -keystore $trustStore -storetype PKCS12 -storepass $storePassword -noprompt|Out-Null
 $env:DB_URL="jdbc:mysql://127.0.0.1:$MySqlPort/enterprise_platform?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false";$env:DB_USERNAME='root';$env:DB_PASSWORD='';$env:JWT_SECRET='test-only-jwt-secret-with-more-than-thirty-two-bytes';$env:DB_MAPPING_CHECK_ENABLED='true';$env:GOVERNANCE_AUDIT_SINK_ENABLED='true';$env:GOVERNANCE_AUDIT_ENVIRONMENT='TEST';$env:GOVERNANCE_AUDIT_SERVICE_TOKEN=$token;$env:GOVERNANCE_AUDIT_CLIENT_IDENTITY='workflow-role-audit-client';$env:SPRING_PROFILES_ACTIVE='test'
 $jar=Join-Path $workspace 'backend\target\enterprise-platform.jar';$appOut=Join-Path $logs app.out;$appErr=Join-Path $logs app.err
 $args=@('-jar',$jar,"--server.port=$HttpsPort",'--server.ssl.enabled=true',"--server.ssl.key-store=file:$serverStore",'--server.ssl.key-store-type=PKCS12',"--server.ssl.key-store-password=$storePassword","--server.ssl.key-password=$storePassword")
 $appProcess=Start-Process $java -ArgumentList $args -WindowStyle Hidden -RedirectStandardOutput $appOut -RedirectStandardError $appErr -PassThru
 $health="https://localhost:$HttpsPort/api/internal/governance-audit-sink/health";foreach($i in 1..120){$old=$ErrorActionPreference;$ErrorActionPreference='Continue';&curl.exe --silent --fail --cacert $certificate -H "Authorization: Bearer $token" -H 'X-Service-Identity: workflow-role-audit-client' $health 2>$null|Out-Null;$ready=$LASTEXITCODE;$ErrorActionPreference=$old;if(!$ready){break};if($appProcess.HasExited){throw "app exited: $(Get-Content $appErr -Raw)"};Start-Sleep -Milliseconds 500};if($ready){throw 'HTTPS sink not ready'}
 $env:AUDIT_LIVE_ENDPOINT="https://localhost:$HttpsPort/api/internal/governance-audit-sink/events";$env:AUDIT_TRUSTSTORE=$trustStore;$env:AUDIT_TRUSTSTORE_PASSWORD=$storePassword;$env:AUDIT_SERVICE_TOKEN=$token;$env:AUDIT_CLIENT_IDENTITY='workflow-role-audit-client';$env:AUDIT_DB_URL="jdbc:mysql://127.0.0.1:$MySqlPort/enterprise_platform"
 $env:JAVA_HOME=$javaHome;$env:Path="$javaHome\bin;$env:Path";&$mvn -q '-Dtest=ExternalAuditLiveHttpsValidationTest' test -f (Join-Path $workspace 'backend\pom.xml')|Set-Content (Join-Path $logs live-test.log);if($LASTEXITCODE){throw 'live HTTPS validation failed'}
 $counts=&$mysql --protocol=TCP --host=127.0.0.1 --port=$MySqlPort --user=root --database=enterprise_platform --batch --raw --skip-column-names '--execute=SELECT (SELECT COUNT(*) FROM governance_external_audit_event),(SELECT COUNT(*) FROM governance_external_audit_receipt);'
 [ordered]@{result='PASS';environment='TEST';tls='TLSv1.3/JDK hostname verification';authentication='service credential + service identity';eventReceiptCounts=$counts;evidenceRoot=$OutputRoot}|ConvertTo-Json|Set-Content (Join-Path $OutputRoot summary.json);Get-Content (Join-Path $OutputRoot summary.json)
}finally{
 foreach($name in @('AUDIT_SERVICE_TOKEN','AUDIT_TRUSTSTORE_PASSWORD','GOVERNANCE_AUDIT_SERVICE_TOKEN')){Remove-Item "Env:$name" -ErrorAction SilentlyContinue}
 if($appProcess-and!$appProcess.HasExited){Stop-Process -Id $appProcess.Id -Force -ErrorAction SilentlyContinue};if($mysqlProcess-and!$mysqlProcess.HasExited){$old=$ErrorActionPreference;$ErrorActionPreference='Continue';&$mysqladmin --protocol=TCP --host=127.0.0.1 --port=$MySqlPort --user=root shutdown 2>$null|Out-Null;$ErrorActionPreference=$old;if(!$mysqlProcess.HasExited){Stop-Process -Id $mysqlProcess.Id -Force -ErrorAction SilentlyContinue}}
}

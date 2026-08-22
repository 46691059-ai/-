param(
    [Parameter(Mandatory=$true)][string]$ValidationRoot,
    [string]$InstanceName = 'upgrade',
    [int]$Port = 37612
)

$ErrorActionPreference = 'Stop'
$mysqlHome = 'C:\Program Files\MySQL\MySQL Server 8.4'
$mysqld = Join-Path $mysqlHome 'bin\mysqld.exe'
$mysql = Join-Path $mysqlHome 'bin\mysql.exe'
$mysqladmin = Join-Path $mysqlHome 'bin\mysqladmin.exe'
$serverRoot = Join-Path $ValidationRoot $InstanceName
$ini = Join-Path $serverRoot 'my.ini'
$evidenceRoot = Join-Path $ValidationRoot 'evidence'
$resultFile = Join-Path $evidenceRoot 'final-runtime-matrix.tsv'
$summaryFile = Join-Path $evidenceRoot 'final-runtime-summary.json'
$checks = [System.Collections.Generic.List[object]]::new()
$failures = [System.Collections.Generic.List[string]]::new()

if (-not (Test-Path -LiteralPath $ini)) { throw "Unknown disposable instance: $ini" }
if (-not $ValidationRoot.StartsWith('D:\codex-validation-v2615-')) { throw 'Unsafe validation root' }
if (Get-NetTCPConnection -LocalPort $Port -ErrorAction SilentlyContinue | Where-Object State -ne 'TimeWait') {
    throw "Port already in use: $Port"
}

function Invoke-Db([string]$Sql,[switch]$Raw) {
    $args = @('--protocol=TCP','--host=127.0.0.1',"--port=$Port",'--user=root','--database=enterprise_platform')
    if ($Raw) { $args += @('--batch','--raw','--skip-column-names') }
    $old=$ErrorActionPreference; $ErrorActionPreference='Continue'
    $out=&$mysql @args "--execute=$Sql" 2>&1; $code=$LASTEXITCODE
    $ErrorActionPreference=$old
    return @{ExitCode=$code;Output=($out -join "`n")}
}

function Add-Check([string]$Name,[bool]$Pass,[string]$Evidence='') {
    $checks.Add([ordered]@{name=$Name;pass=$Pass;evidence=$Evidence})
    if (-not $Pass) { $failures.Add("$Name :: $Evidence") }
}

function Assert-Accepted([string]$Name,[string]$Sql) {
    $r=Invoke-Db $Sql; Add-Check $Name ($r.ExitCode -eq 0) $r.Output; return $r
}

function Assert-Rejected([string]$Name,[string]$Sql,[string]$Expected='') {
    $r=Invoke-Db $Sql; $pass=$r.ExitCode -ne 0 -and (!$Expected -or $r.Output.Contains($Expected))
    Add-Check $Name $pass $r.Output; return $r
}

function Insert-ActivationChain([long]$Base,[string]$Activation) {
    $r=Invoke-Db "INSERT INTO role_runtime_activation_request(id,activation_id,resolver_code,resolver_version,contract_hash,binding_hash,candidate_hash,directory_contract_hash,directory_revision,activation_hash,approval_evidence_hash,business_scope,effective_at,requested_by,status,created_by,updated_by,deleted,delete_token,version) VALUES($Base,'$Activation','ROLE_DIRECTORY','ROLE_DIRECTORY_V1',REPEAT('a',64),REPEAT('b',64),REPEAT('c',64),REPEAT('d',64),17,REPEAT('e',64),REPEAT('f',64),'INVESTMENT_DECISION','2026-08-18 08:00:00.000','validator','PERSISTED','validator','validator',0,0,0)"; if($r.ExitCode){throw $r.Output}
    $roles=@('BUSINESS_OWNER','SECURITY_AUDIT','RELEASE_APPROVER')
    for($i=0;$i-lt3;$i++){$id=$Base+$i+1;$role=$roles[$i];$r=Invoke-Db "INSERT INTO role_runtime_activation_approval(id,activation_id,approver_type,approver_id,decision,reason,source_evidence_hash,decision_hash,activation_hash,contract_hash,binding_hash,decision_time,created_by,updated_by,deleted,delete_token,version) VALUES($id,'$Activation','$role','approver-$id','APPROVE','approved',SHA2(CONCAT('source',$id),256),SHA2(CONCAT('decision',$id),256),REPEAT('e',64),REPEAT('a',64),REPEAT('b',64),'2026-08-18 08:10:00.000','validator','validator',0,0,0)";if($r.ExitCode){throw $r.Output}}
    $types=@('ACTIVATION_APPROVAL','RESOLVER_CONTRACT','BINDING','CANDIDATE','DIRECTORY')
    for($i=0;$i-lt5;$i++){$id=$Base+10+$i;$type=$types[$i];$hex=@('f','a','b','c','d')[$i];$r=Invoke-Db "INSERT INTO role_runtime_activation_evidence(id,approval_id,activation_id,evidence_type,evidence_hash,activation_hash,contract_hash,binding_hash,canonical_version,created_by,updated_by,deleted,delete_token,version) VALUES($id,$($Base+3),'$Activation','$type',REPEAT('$hex',64),REPEAT('e',64),REPEAT('a',64),REPEAT('b',64),'ROLE_RUNTIME_ACTIVATION_CANONICAL_V1','validator','validator',0,0,0)";if($r.ExitCode){throw $r.Output}}
}

function Insert-Candidate([long]$Base,[string]$Tag) {
    $activation="ACT-$Tag";$promotion="PROMO-$Tag";$snapshot="SNAP-$Tag"
    $promotionHash=(Invoke-Db "SELECT SHA2('promotion-$Tag',256)" -Raw).Output.Trim()
    Insert-ActivationChain $Base $activation
    $r=Invoke-Db "INSERT INTO workflow_role_binding_promotion(id,promotion_id,activation_id,activation_delete_token,activation_hash,activation_audit_hash,approval_evidence_hash,resolver_code,resolver_version,resolver_contract_hash,binding_hash,candidate_hash,directory_contract_hash,directory_revision,business_scope,effective_from,effective_until,permission_evidence_hash,promotion_evidence_hash,promotion_hash,status,promoted_at,promoted_by,canonical_version,created_by,updated_by,deleted,delete_token,version) VALUES($($Base+100),'$promotion','$activation',0,REPEAT('e',64),REPEAT('1',64),REPEAT('f',64),'ROLE_DIRECTORY','ROLE_DIRECTORY_V1',REPEAT('a',64),REPEAT('b',64),REPEAT('c',64),REPEAT('d',64),17,'INVESTMENT_DECISION','2026-08-18 08:00:00.000','2099-08-18 12:00:00.000',REPEAT('2',64),REPEAT('3',64),'$promotionHash','PROMOTED','2026-08-18 08:20:00.000','validator','ROLE_RUNTIME_BINDING_PROMOTION_CANONICAL_V1','validator','validator',0,0,0)";if($r.ExitCode){throw $r.Output}
    $candidate=$Base+200
    $r=Invoke-Db "INSERT INTO workflow_role_binding_candidate_snapshot(id,snapshot_id,promotion_row_id,promotion_id,activation_id,activation_delete_token,activation_hash,promotion_hash,promotion_evidence_hash,promotion_reference_hash,resolver_code,resolver_version,resolver_contract_hash,binding_hash,candidate_hash,directory_contract_hash,directory_revision,business_scope,effective_from,effective_until,activation_audit_hash,approval_evidence_hash,permission_evidence_hash,evidence_set_hash,snapshot_hash,canonical_version,created_by,updated_by,deleted,delete_token,version,directory_result_hash,directory_fence_token_hash,directory_fence_expires_at,enterprise_id,definition_release_id,definition_id,definition_version_id,node_id,node_binding_hash,graph_hash) VALUES($candidate,'$snapshot',$($Base+100),'$promotion','$activation',0,REPEAT('e',64),'$promotionHash',REPEAT('3',64),SHA2(CONCAT('reference','$Tag'),256),'ROLE_DIRECTORY','ROLE_DIRECTORY_V1',REPEAT('a',64),REPEAT('b',64),REPEAT('c',64),REPEAT('d',64),17,'INVESTMENT_DECISION','2026-08-18 08:00:00.000','2099-08-18 12:00:00.000',REPEAT('1',64),REPEAT('f',64),REPEAT('2',64),SHA2(CONCAT('set','$Tag'),256),SHA2(CONCAT('snapshot','$Tag'),256),'ROLE_RUNTIME_BINDING_SNAPSHOT_CANONICAL_V1','validator','validator',0,0,0,REPEAT('1',64),REPEAT('2',64),'2099-08-18 11:00:00.000','ENT-1',910004,910001,910002,910003,REPEAT('3',64),REPEAT('4',64))";if($r.ExitCode){throw $r.Output}
    $h1=(Invoke-Db "SELECT SHA2('$Tag-event-1',256)" -Raw).Output.Trim();$h2=(Invoke-Db "SELECT SHA2('$Tag-event-2',256)" -Raw).Output.Trim();$h3=(Invoke-Db "SELECT SHA2('$Tag-event-3',256)" -Raw).Output.Trim()
    foreach($event in @(@(1,'NULL','CREATED','NULL',$h1),@(2,"'CREATED'",'VALIDATED',"'$h1'",$h2),@(3,"'VALIDATED'",'ACTIVE',"'$h2'",$h3))){$id=$Base+300+[int]$event[0];$r=Invoke-Db "INSERT INTO workflow_role_binding_snapshot_event(id,snapshot_row_id,snapshot_id,promotion_id,activation_id,sequence_no,from_status,to_status,reason_code,source_evidence_hash,previous_event_hash,event_hash,occurred_at,operator_id,canonical_version,created_by,updated_by,deleted,delete_token,version) VALUES($id,$candidate,'$snapshot','$promotion','$activation',$($event[0]),$($event[1]),'$($event[2])','TEST',REPEAT('7',64),$($event[3]),'$($event[4])','2026-08-18 08:30:00.000','validator','ROLE_RUNTIME_BINDING_SNAPSHOT_CANONICAL_V1','validator','validator',0,0,0)";if($r.ExitCode){throw $r.Output}}
    $r=Invoke-Db "INSERT INTO workflow_role_runtime_execution_admission_slot(candidate_snapshot_row_id,snapshot_id,source_delete_token,active_token,slot_status,version,updated_by) VALUES($candidate,'$snapshot',0,SHA2(CONCAT('slot','$Tag'),256),'VACANT',0,'validator')";if($r.ExitCode){throw $r.Output}
    return [ordered]@{base=$Base;candidate=$candidate;activation=$activation;promotion=$promotion;promotionHash=$promotionHash;snapshot=$snapshot}
}

function Insert-Admission($Candidate,[long]$Id,[string]$Suffix,[string]$Root=('9f73f6861c4969751365f82d540715ec272f336b193d0f95a41de903b9f1e5cb')) {
    $admission="ADM-$Suffix";$persistence=(Invoke-Db "SELECT SHA2('PERSIST-$admission',256)" -Raw).Output.Trim()
    $sql="INSERT INTO workflow_role_runtime_execution_admission(id,admission_id,request_id,idempotency_key,candidate_snapshot_row_id,snapshot_id,promotion_id,activation_id,source_delete_token,activation_hash,promotion_hash,binding_hash,candidate_hash,execution_admission_hash,capability_evidence_root_hash,persistence_hash,resolver_code,resolver_version,resolver_contract_hash,directory_revision,directory_result_hash,directory_fence_token_hash,directory_fence_expires_at,directory_verified_at,enterprise_id,business_scope,definition_release_id,definition_id,definition_version_id,node_id,node_binding_hash,graph_hash,decision,policy_version,effective_at,admission_expires_at,executed_check_count,last_check_sequence,feature_flag_evidence_hash,canary_evidence_hash,kill_switch_evidence_hash,requested_by,decided_by,decided_at,created_by,updated_by,deleted,delete_token,version) VALUES($Id,'$admission','REQ-$Suffix','IDEM-$Suffix',$($Candidate.candidate),'$($Candidate.snapshot)','$($Candidate.promotion)','$($Candidate.activation)',0,REPEAT('e',64),'$($Candidate.promotionHash)',REPEAT('b',64),REPEAT('c',64),REPEAT('8',64),'$Root','$persistence','ROLE_DIRECTORY','ROLE_DIRECTORY_V1',REPEAT('a',64),17,REPEAT('1',64),REPEAT('2',64),'2099-08-18 11:00:00.000','2026-08-18 08:40:00.000','ENT-1','INVESTMENT_DECISION',910004,910001,910002,910003,REPEAT('3',64),REPEAT('4',64),'APPROVED_FOR_EXECUTION','POLICY_V1','2026-08-18 08:40:00.000','2099-08-18 10:00:00.000',28,28,REPEAT('5',64),REPEAT('6',64),REPEAT('7',64),'requester','approver','2026-08-18 08:50:00.000','validator','validator',0,0,0)"
    $r=Invoke-Db $sql;if($r.ExitCode){throw $r.Output};return [ordered]@{id=$Id;admission=$admission;persistence=$persistence;root=$Root;candidate=$Candidate}
}

function Insert-Evidence($Admission,[int]$Count=28,[int]$FailSequence=0,[int]$StatusSequence=0,[string]$Status='READY') {
    $codes=@('PERSISTED_CANDIDATE_EXISTS','CANDIDATE_SNAPSHOT_STATUS','PROMOTION_EVIDENCE_COMPLETE','ACTIVATION_EVIDENCE_COMPLETE','ACTIVATION_NOT_REVOKED','PROMOTION_NOT_REVOKED','RESOLVER_CODE','RESOLVER_VERSION','RESOLVER_CONTRACT_HASH','ACTIVATION_HASH','PROMOTION_HASH','BINDING_HASH','CANDIDATE_HASH','DIRECTORY_REVISION_AND_RESULT_HASH','EFFECTIVE_AT','BUSINESS_SCOPE','DEFINITION_VERSION','NODE_BINDING','RESOLVER_DESCRIPTOR','RESOLVER_ADMISSION_STATUS','DIRECTORY_READY','REALTIME_ELIGIBILITY_READY','DATA_SCOPE_READY','SOD_READY','AUDIT_READY','FEATURE_FLAG_READY','KILL_SWITCH_READY','CANARY_SCOPE_READY')
    $caps=@('DIRECTORY','REALTIME_ELIGIBILITY','DATA_SCOPE','SOD','AUDIT','FEATURE_FLAG','KILL_SWITCH','CANARY_SCOPE')
    for($seq=1;$seq-le$Count;$seq++){$result=$(if($seq-eq$FailSequence){'FAIL'}else{'PASS'});$reason=$(if($result-eq'FAIL'){"'NEGATIVE'"}else{'NULL'});$cap='NULL';$capStatus='NULL';$provider='NULL';if($seq-ge21){$cap="'$($caps[$seq-21])'";$s=$(if($seq-eq$StatusSequence){$Status}else{'READY'});$capStatus="'$s'";$provider="'FAKE_V1'"};$hash=('{0:x64}' -f $seq);$id=$Admission.id*100+$seq;$r=Invoke-Db "INSERT INTO workflow_role_runtime_execution_admission_evidence(id,admission_row_id,admission_id,sequence_no,validator_code,evidence_type,result,block_reason,capability_code,capability_status,provider_version,policy_version,observed_value_code,scope_type,scope_enterprise_id,scope_definition_id,scope_definition_version_id,scope_node_id,checked_at,subject_hash,evidence_hash,canonical_version,created_by,updated_by,deleted,delete_token,version) VALUES($id,$($Admission.id),'$($Admission.admission)',$seq,'$($codes[$seq-1])','$(if($seq-ge21){'CAPABILITY'}else{'VALIDATOR'})','$result',$reason,$cap,$capStatus,$provider,'POLICY_V1','READY','ENTERPRISE','ENT-1',910001,910002,910003,'2026-08-18 09:00:00.000',REPEAT('a',64),'$hash','EVIDENCE_V1','validator','validator',0,0,0)";if($r.ExitCode){throw $r.Output}}
}

function Event-Sql($Admission,[long]$Id,[long]$Seq,[string]$Type,[string]$From,[string]$To,[string]$Previous,[string]$Root='',[string]$Persistence='') {
    if(!$Root){$Root=$Admission.root};if(!$Persistence){$Persistence=$Admission.persistence};$fromSql=$(if($From){"'$From'"}else{'NULL'});$prevSql=$(if($Previous){"'$Previous'"}else{'NULL'});$hash=(Invoke-Db "SELECT SHA2(CONCAT('$($Admission.admission)','$Seq','$Type','$Id'),256)" -Raw).Output.Trim()
    return "INSERT INTO workflow_role_runtime_execution_admission_event(id,admission_row_id,admission_id,candidate_snapshot_row_id,sequence_no,event_type,from_status,to_status,reason_code,source_evidence_root_hash,source_persistence_hash,previous_event_hash,event_hash,occurred_at,operator_id,operator_role,idempotency_key,canonical_version,created_by,updated_by,deleted,delete_token,version) VALUES($Id,$($Admission.id),'$($Admission.admission)',$($Admission.candidate.candidate),$Seq,'$Type',$fromSql,'$To','TEST','$Root','$Persistence',$prevSql,'$hash','2026-08-18 09:10:00.000','validator','RELEASE_APPROVER','EV-$Id','ROLE_EXECUTION_ADMISSION_EVENT_CANONICAL_V1','validator','validator',0,0,0)"
}

function Prepare-Eligible($Admission,[long]$EventBase) {
    $createdHash=(Invoke-Db "SELECT SHA2(CONCAT('$($Admission.admission)','1','ADMISSION_CREATED','$($EventBase+1)'),256)" -Raw).Output.Trim()
    $eligibleHash=(Invoke-Db "SELECT SHA2(CONCAT('$($Admission.admission)','2','ELIGIBLE','$($EventBase+2)'),256)" -Raw).Output.Trim()
    $r=Assert-Accepted "event_created_$($Admission.admission)" (Event-Sql $Admission ($EventBase+1) 1 'ADMISSION_CREATED' '' 'CREATED' '');if($r.ExitCode){throw $r.Output}
    $r=Assert-Accepted "event_eligible_$($Admission.admission)" (Event-Sql $Admission ($EventBase+2) 2 'ELIGIBLE' 'CREATED' 'ELIGIBLE' $createdHash);if($r.ExitCode){throw $r.Output}
    return $eligibleHash
}

function Invoke-Concurrent([string]$Name,[string]$Sql1,[string]$Sql2) {
    $dir=$evidenceRoot;$in1=Join-Path $dir "$Name-1.sql";$in2=Join-Path $dir "$Name-2.sql";$out1=Join-Path $dir "$Name-1.out";$out2=Join-Path $dir "$Name-2.out";$err1=Join-Path $dir "$Name-1.err";$err2=Join-Path $dir "$Name-2.err"
    [IO.File]::WriteAllText($in1,"$Sql1;`n",[Text.UTF8Encoding]::new($false));[IO.File]::WriteAllText($in2,"$Sql2;`n",[Text.UTF8Encoding]::new($false))
    $args=@('--protocol=TCP','--host=127.0.0.1',"--port=$Port",'--user=root','--database=enterprise_platform','--force')
    $p1=Start-Process $mysql -ArgumentList $args -WindowStyle Hidden -PassThru -RedirectStandardInput $in1 -RedirectStandardOutput $out1 -RedirectStandardError $err1
    $p2=Start-Process $mysql -ArgumentList $args -WindowStyle Hidden -PassThru -RedirectStandardInput $in2 -RedirectStandardOutput $out2 -RedirectStandardError $err2
    $p1.WaitForExit();$p2.WaitForExit();$e1=(Get-Content $err1 -ErrorAction SilentlyContinue)-join' ';$e2=(Get-Content $err2 -ErrorAction SilentlyContinue)-join' ';return @{e1=$e1;e2=$e2;errors=@($e1,$e2).Where({$_-match'ERROR'}).Count;deadlock=("$e1 $e2"-match'ERROR 1213|ERROR 1205')}
}

$process=$null
try {
    $process=Start-Process $mysqld -ArgumentList "--defaults-file=$ini" -WindowStyle Hidden -PassThru
    foreach($i in 1..100){$old=$ErrorActionPreference;$ErrorActionPreference='Continue';&$mysqladmin --protocol=TCP --host=127.0.0.1 --port=$Port --user=root ping 2>$null|Out-Null;$code=$LASTEXITCODE;$ErrorActionPreference=$old;if($code-eq0){break};Start-Sleep -Milliseconds 300}
    if((Invoke-Db 'SELECT VERSION()' -Raw).ExitCode){throw 'Disposable MySQL failed to start'}

    # Stable definition/version/node/release facts required by the admission ownership chain.
    $seed=@"
INSERT INTO workflow_definition(id,definition_code,definition_name,business_type,enterprise_id,status,created_by,updated_by,deleted,delete_token,version) VALUES(910001,'WF-ADMISSION-TEST','Admission Test','TEST',1,'ACTIVE','validator','validator',0,0,0);
INSERT INTO workflow_version(id,definition_id,version_no,status,schema_version,content_hash,effective_from,published_by,published_time,created_by,updated_by,deleted,delete_token,version) VALUES(910002,910001,1,'PUBLISHED','V1',REPEAT('4',64),'2026-08-18 08:00:00.000',1,'2026-08-18 08:00:00.000','validator','validator',0,0,0);
UPDATE workflow_definition SET current_version_id=910002 WHERE id=910001;
INSERT INTO workflow_node(id,version_id,node_code,node_name,node_type,node_order,governance_node_type,approval_mode,assignment_rule_type,assignment_rule_config,withdraw_allowed,enabled,created_by,updated_by,deleted,delete_token,version) VALUES(910003,910002,'ROLE_APPROVAL','Role Approval','APPROVAL',1,'GENERAL_APPROVAL','SINGLE','USER','{}',0,1,'validator','validator',0,0,0);
INSERT INTO workflow_version_release(id,definition_id,previous_version_id,published_version_id,published_version_no,content_hash,operator_user_id,operator_org_id,published_time,created_by,updated_by,deleted,delete_token,version) VALUES(910004,910001,NULL,910002,1,REPEAT('4',64),1,1,'2026-08-18 08:00:00.000','validator','validator',0,0,0);
"@
    $r=Invoke-Db $seed;if($r.ExitCode){throw $r.Output}

    $c1=Insert-Candidate 920000 'PRIMARY'
    # Exact evidence matrix.
    $a0=Insert-Admission $c1 930000 'ZERO';$prev=Prepare-Eligible $a0 940000;Assert-Rejected 'evidence_0_of_28' (Event-Sql $a0 940003 3 'APPROVED_FOR_EXECUTION' 'ELIGIBLE' 'APPROVED_FOR_EXECUTION' $prev) 'ROLE_ADMISSION_EVIDENCE_INCOMPLETE'|Out-Null
    $a27=Insert-Admission $c1 930001 'TWENTYSEVEN';Insert-Evidence $a27 27;$prev=Prepare-Eligible $a27 940010;Assert-Rejected 'evidence_27_of_28' (Event-Sql $a27 940013 3 'APPROVED_FOR_EXECUTION' 'ELIGIBLE' 'APPROVED_FOR_EXECUTION' $prev) 'ROLE_ADMISSION_EVIDENCE_INCOMPLETE'|Out-Null
    $afail=Insert-Admission $c1 930002 'ONEFAIL';Insert-Evidence $afail 28 20;$prev=Prepare-Eligible $afail 940020;Assert-Rejected 'validator_27_pass_1_fail' (Event-Sql $afail 940023 3 'APPROVED_FOR_EXECUTION' 'ELIGIBLE' 'APPROVED_FOR_EXECUTION' $prev) 'ROLE_ADMISSION_VALIDATOR_CONTRACT_MISMATCH'|Out-Null
    $statusCases=@(@(21,'NOT_READY','DIRECTORY'),@(22,'DEGRADED','REALTIME'),@(23,'BLOCKED','DATA_SCOPE'),@(24,'NOT_READY','SOD'),@(25,'DEGRADED','AUDIT'),@(26,'BLOCKED','FEATURE_FLAG'),@(27,'NOT_READY','KILL_SWITCH'),@(28,'DEGRADED','CANARY_SCOPE'))
    $n=0;foreach($case in $statusCases){$a=Insert-Admission $c1 (930100+$n) "CAP-$n";Insert-Evidence $a 28 0 $case[0] $case[1];$prev=Prepare-Eligible $a (941000+$n*10);Assert-Rejected "capability_$($case[2])_$($case[1])" (Event-Sql $a (941003+$n*10) 3 'APPROVED_FOR_EXECUTION' 'ELIGIBLE' 'APPROVED_FOR_EXECUTION' $prev) 'ROLE_ADMISSION_CAPABILITY_NOT_READY'|Out-Null;$n++}

    # Fixed Java vector and DB canonical root.
    $apos=Insert-Admission $c1 930500 'POSITIVE';Insert-Evidence $apos 28
    $rootSql=@"
SET SESSION group_concat_max_len=65535;
SELECT SHA2(CONCAT('9:canonical50:ROLE_RUNTIME_CAPABILITY_EVIDENCE_ROOT_CANONICAL_V1',GROUP_CONCAT(CONCAT('8:sequence',CHAR_LENGTH(CAST(sequence_no AS CHAR)),':',sequence_no,'13:validatorCode',CHAR_LENGTH(validator_code),':',validator_code,'14:capabilityCode',CHAR_LENGTH(capability_code),':',capability_code,'16:capabilityStatus',CHAR_LENGTH(capability_status),':',capability_status,'6:result',CHAR_LENGTH(result),':',result,'12:evidenceHash',CHAR_LENGTH(evidence_hash),':',evidence_hash,'15:providerVersion',CHAR_LENGTH(provider_version),':',provider_version,'13:policyVersion',CHAR_LENGTH(policy_version),':',policy_version) ORDER BY capability_code SEPARATOR '')),256) FROM workflow_role_runtime_execution_admission_evidence WHERE admission_row_id=930500 AND sequence_no BETWEEN 21 AND 28
"@
    $dbRoot=(Invoke-Db $rootSql -Raw).Output.Trim();Add-Check 'db_root_fixed_vector' ($dbRoot-eq$apos.root) "db=$dbRoot java=$($apos.root)"
    $prev=Prepare-Eligible $apos 945000
    Assert-Rejected 'root_event_wrong' (Event-Sql $apos 945003 3 'APPROVED_FOR_EXECUTION' 'ELIGIBLE' 'APPROVED_FOR_EXECUTION' $prev ('0'*64)) 'ROLE_ADMISSION_CAPABILITY_ROOT_MISMATCH'|Out-Null
    Assert-Rejected 'persistence_event_wrong' (Event-Sql $apos 945004 3 'APPROVED_FOR_EXECUTION' 'ELIGIBLE' 'APPROVED_FOR_EXECUTION' $prev '' ('0'*64)) 'ROLE_ADMISSION_PERSISTENCE_HASH_MISMATCH'|Out-Null
    $slotToken=(Invoke-Db "SELECT active_token FROM workflow_role_runtime_execution_admission_slot WHERE candidate_snapshot_row_id=$($c1.candidate)" -Raw).Output.Trim()
    Assert-Accepted 'slot_legal_occupy' "UPDATE workflow_role_runtime_execution_admission_slot SET active_admission_row_id=$($apos.id),active_admission_id='$($apos.admission)',active_token=SHA2('occupied-primary',256),slot_status='OCCUPIED',version=version+1,updated_by='validator' WHERE candidate_snapshot_row_id=$($c1.candidate) AND version=0 AND active_token='$slotToken'"|Out-Null
    Assert-Rejected 'slot_delete_forbidden' "DELETE FROM workflow_role_runtime_execution_admission_slot WHERE candidate_snapshot_row_id=$($c1.candidate)" 'ROLE_RUNTIME_ADMISSION_SLOT_DELETE_FORBIDDEN'|Out-Null
    Assert-Rejected 'slot_non_cas_version_jump' "UPDATE workflow_role_runtime_execution_admission_slot SET active_token=SHA2('bad',256),version=version+2 WHERE candidate_snapshot_row_id=$($c1.candidate)" 'ROLE_ADMISSION_SLOT_CAS_ONLY'|Out-Null
    Assert-Rejected 'slot_ownership_mutation' "UPDATE workflow_role_runtime_execution_admission_slot SET snapshot_id='BAD',active_token=SHA2('bad2',256),version=version+1 WHERE candidate_snapshot_row_id=$($c1.candidate)" 'ROLE_ADMISSION_SLOT_CAS_ONLY'|Out-Null
    Assert-Accepted 'legal_final_approval' (Event-Sql $apos 945005 3 'APPROVED_FOR_EXECUTION' 'ELIGIBLE' 'APPROVED_FOR_EXECUTION' $prev)|Out-Null
    $approvedHash=(Invoke-Db "SELECT event_hash FROM workflow_role_runtime_execution_admission_event WHERE id=945005" -Raw).Output.Trim()
    Assert-Rejected 'decision_terminal_conflict' (Event-Sql $apos 945006 4 'REJECTED' 'APPROVED_FOR_EXECUTION' 'REJECTED' $approvedHash) | Out-Null
    Assert-Accepted 'legal_revoke' (Event-Sql $apos 945007 4 'REVOKED' 'APPROVED_FOR_EXECUTION' 'REVOKED' $approvedHash)|Out-Null
    $revokedHash=(Invoke-Db "SELECT event_hash FROM workflow_role_runtime_execution_admission_event WHERE id=945007" -Raw).Output.Trim()
    Assert-Rejected 'closure_terminal_conflict' (Event-Sql $apos 945008 5 'EXPIRED' 'REVOKED' 'EXPIRED' $revokedHash)|Out-Null
    Assert-Accepted 'slot_legal_release' "UPDATE workflow_role_runtime_execution_admission_slot SET active_admission_row_id=NULL,active_admission_id=NULL,active_token=SHA2('released-primary',256),slot_status='VACANT',version=version+1,updated_by='validator' WHERE candidate_snapshot_row_id=$($c1.candidate) AND version=1"|Out-Null

    # Final-event two-session winner.
    $c2=Insert-Candidate 950000 'FINAL-CONCURRENT';$a2=Insert-Admission $c2 951000 'FINAL-CONCURRENT';Insert-Evidence $a2 28;$prev2=Prepare-Eligible $a2 952000;$token2=(Invoke-Db "SELECT active_token FROM workflow_role_runtime_execution_admission_slot WHERE candidate_snapshot_row_id=$($c2.candidate)" -Raw).Output.Trim();Assert-Accepted 'concurrent_final_slot_occupy' "UPDATE workflow_role_runtime_execution_admission_slot SET active_admission_row_id=$($a2.id),active_admission_id='$($a2.admission)',active_token=SHA2('occupied-final',256),slot_status='OCCUPIED',version=1 WHERE candidate_snapshot_row_id=$($c2.candidate) AND version=0 AND active_token='$token2'"|Out-Null
    $con=Invoke-Concurrent 'final-approval' (Event-Sql $a2 952003 3 'APPROVED_FOR_EXECUTION' 'ELIGIBLE' 'APPROVED_FOR_EXECUTION' $prev2) (Event-Sql $a2 952004 3 'APPROVED_FOR_EXECUTION' 'ELIGIBLE' 'APPROVED_FOR_EXECUTION' $prev2)
    $approvedCount=(Invoke-Db "SELECT COUNT(*) FROM workflow_role_runtime_execution_admission_event WHERE admission_row_id=$($a2.id) AND event_type='APPROVED_FOR_EXECUTION'" -Raw).Output.Trim();Add-Check 'concurrent_final_single_winner' ($con.errors-eq1-and-not$con.deadlock-and$approvedCount-eq'1') "errors=$($con.errors) deadlock=$($con.deadlock) approved=$approvedCount $($con.e1) $($con.e2)"

    # Slot two-session CAS winner with two eligible admissions.
    $c3=Insert-Candidate 960000 'SLOT-CONCURRENT';$sa=Insert-Admission $c3 961000 'SLOT-A';$sb=Insert-Admission $c3 961001 'SLOT-B';$pa=Prepare-Eligible $sa 962000;$pb=Prepare-Eligible $sb 962010;$token3=(Invoke-Db "SELECT active_token FROM workflow_role_runtime_execution_admission_slot WHERE candidate_snapshot_row_id=$($c3.candidate)" -Raw).Output.Trim()
    $u1="UPDATE workflow_role_runtime_execution_admission_slot SET active_admission_row_id=$($sa.id),active_admission_id='$($sa.admission)',active_token=SHA2('slot-a',256),slot_status='OCCUPIED',version=1 WHERE candidate_snapshot_row_id=$($c3.candidate) AND version=0 AND active_token='$token3'"
    $u2="UPDATE workflow_role_runtime_execution_admission_slot SET active_admission_row_id=$($sb.id),active_admission_id='$($sb.admission)',active_token=SHA2('slot-b',256),slot_status='OCCUPIED',version=1 WHERE candidate_snapshot_row_id=$($c3.candidate) AND version=0 AND active_token='$token3'"
    $sc=Invoke-Concurrent 'slot-cas' $u1 $u2;$slotState=(Invoke-Db "SELECT COUNT(*),version FROM workflow_role_runtime_execution_admission_slot WHERE candidate_snapshot_row_id=$($c3.candidate) AND slot_status='OCCUPIED' GROUP BY version" -Raw).Output.Trim();Add-Check 'slot_concurrent_single_winner' (-not$sc.deadlock-and$slotState-eq"1`t1") "deadlock=$($sc.deadlock) state=$slotState errors=$($sc.errors)"

    # Evidence unique winner and idempotency probes.
    $ec=Insert-Admission $c3 961100 'EVID-CONCURRENT';$code='PERSISTED_CANDIDATE_EXISTS';$baseEv="INSERT INTO workflow_role_runtime_execution_admission_evidence(id,admission_row_id,admission_id,sequence_no,validator_code,evidence_type,result,block_reason,capability_code,capability_status,provider_version,policy_version,checked_at,subject_hash,evidence_hash,canonical_version,created_by,updated_by,deleted,delete_token,version) VALUES({0},961100,'ADM-EVID-CONCURRENT',1,'$code','VALIDATOR','PASS',NULL,NULL,NULL,NULL,'POLICY_V1','2026-08-18 09:00:00.000',REPEAT('a',64),REPEAT('{1}',64),'EVIDENCE_V1','validator','validator',0,0,0)";$evc=Invoke-Concurrent 'evidence-concurrent' ($baseEv-f970001,'1') ($baseEv-f970002,'2');$evCount=(Invoke-Db "SELECT COUNT(*) FROM workflow_role_runtime_execution_admission_evidence WHERE admission_row_id=961100 AND sequence_no=1" -Raw).Output.Trim();Add-Check 'evidence_concurrent_single_winner' ($evc.errors-eq1-and-not$evc.deadlock-and$evCount-eq'1') "errors=$($evc.errors) deadlock=$($evc.deadlock) count=$evCount"
    Assert-Rejected 'admission_request_idempotency' "INSERT INTO workflow_role_runtime_execution_admission SELECT 999001,admission_id,'REQ-POSITIVE','OTHER-IDEM',candidate_snapshot_row_id,snapshot_id,promotion_id,activation_id,source_delete_token,activation_hash,promotion_hash,binding_hash,candidate_hash,execution_admission_hash,capability_evidence_root_hash,SHA2('other-persist',256),resolver_code,resolver_version,resolver_contract_hash,directory_revision,directory_result_hash,directory_fence_token_hash,directory_fence_expires_at,directory_verified_at,enterprise_id,business_scope,definition_release_id,definition_id,definition_version_id,node_id,node_binding_hash,graph_hash,decision,policy_version,effective_at,admission_expires_at,executed_check_count,last_check_sequence,feature_flag_evidence_hash,canary_evidence_hash,kill_switch_evidence_hash,requested_by,decided_by,decided_at,created_by,created_time,updated_by,updated_time,deleted,delete_token,remark,version FROM workflow_role_runtime_execution_admission WHERE id=930500"|Out-Null

    # Append-only and hash/collation probes.
    foreach($probe in @(@('admission_update',"UPDATE workflow_role_runtime_execution_admission SET requested_by='x' WHERE id=930500"),@('admission_delete',"DELETE FROM workflow_role_runtime_execution_admission WHERE id=930500"),@('evidence_update',"UPDATE workflow_role_runtime_execution_admission_evidence SET created_by='x' WHERE admission_row_id=930500 LIMIT 1"),@('evidence_delete',"DELETE FROM workflow_role_runtime_execution_admission_evidence WHERE admission_row_id=930500 LIMIT 1"),@('event_update',"UPDATE workflow_role_runtime_execution_admission_event SET reason_code='x' WHERE id=945001"),@('event_delete',"DELETE FROM workflow_role_runtime_execution_admission_event WHERE id=945001"))){Assert-Rejected $probe[0] $probe[1]|Out-Null}
    $fkCount=(Invoke-Db "SELECT COUNT(*) FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE() AND constraint_name IN ('fk_role_binding_snapshot_definition_version','fk_role_binding_snapshot_node','fk_role_binding_snapshot_release','fk_role_admission_candidate','fk_role_admission_definition_version','fk_role_admission_node','fk_role_admission_release','fk_role_admission_slot_candidate','fk_role_admission_slot_active','fk_role_admission_evidence_owner','fk_role_admission_event_owner')" -Raw).Output.Trim();Add-Check 'ownership_fk_11_of_11' ($fkCount-eq'11') "count=$fkCount"
    $runtime=(Invoke-Db "SELECT (SELECT COUNT(*) FROM workflow_instance WHERE business_key LIKE 'ACT-%'),(SELECT COUNT(*) FROM workflow_task WHERE remark LIKE 'ACT-%'),(SELECT COUNT(*) FROM workflow_task_candidate_pool WHERE remark LIKE 'ACT-%'),(SELECT COUNT(*) FROM workflow_task_claim WHERE remark LIKE 'ACT-%')" -Raw).Output.Trim();Add-Check 'runtime_objects_not_created' ($runtime-eq"0`t0`t0`t0") $runtime

    $checks | ForEach-Object {"$($_.name)`t$($_.pass)`t$($_.evidence.Replace("`n",' '))"} | Set-Content $resultFile -Encoding utf8
    $summary=[ordered]@{result=$(if($failures.Count){'FAIL'}else{'PASS'});checks=$checks.Count;passed=@($checks|Where-Object pass).Count;failures=@($failures);dbRoot=$dbRoot;javaRoot=$apos.root;fkCount=$fkCount;runtimeObjects=$runtime}
    $summary|ConvertTo-Json -Depth 7|Set-Content $summaryFile -Encoding utf8
    $summary|ConvertTo-Json -Depth 7
    if($failures.Count){exit 2}
}
finally {
    $old=$ErrorActionPreference;$ErrorActionPreference='Continue';&$mysqladmin --protocol=TCP --host=127.0.0.1 --port=$Port --user=root shutdown 2>$null|Out-Null;$ErrorActionPreference=$old
    if($process-and-not$process.HasExited){Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue}
}

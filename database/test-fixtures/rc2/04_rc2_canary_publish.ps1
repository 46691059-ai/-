[CmdletBinding()]
param(
    [ValidateSet('DryRun','Execute','Decommission')]
    [string]$Action = 'DryRun',
    [string]$HostName = '127.0.0.1',
    [int]$Port = 34062,
    [string]$ExpectedDataDir,
    [string]$EvidencePath,
    [string]$AttestationResultPath,
    [string]$Username = 'enterprise_rc2',
    [string]$Acknowledgement = '',
    [string]$MavenPath = $env:MAVEN_CMD,
    [string]$JavaHome = $env:JAVA_HOME
)

$ErrorActionPreference = 'Stop'
$legacyGuard = Join-Path $PSScriptRoot '00_rc2_canary_guard.ps1'
$attestationVerifier = Join-Path $PSScriptRoot 'verify-rc2-environment-attestation.ps1'
$fixtureContractVerifier = Join-Path $PSScriptRoot 'verify-rc2-fixture-contract.ps1'
$repositoryRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'))
$backend = Join-Path $repositoryRoot 'backend'
function Read-StableJson([string]$Text) {
    if($PSVersionTable.PSVersion.Major-le5){
        Add-Type -AssemblyName System.Web.Extensions
        $serializer=New-Object System.Web.Script.Serialization.JavaScriptSerializer
        return $serializer.DeserializeObject($Text)
    }
    function Convert-JsonElement($Element){
        switch([string]$Element.ValueKind){
            'Object'{$result=@{};foreach($property in $Element.EnumerateObject()){$result[$property.Name]=Convert-JsonElement $property.Value};return $result}
            'Array'{return @($Element.EnumerateArray()|ForEach-Object{Convert-JsonElement $_})}
            'String'{return [string]$Element.GetString()}
            'Number'{[long]$integer=0;if($Element.TryGetInt64([ref]$integer)){return $integer};return [decimal]$Element.GetDecimal()}
            'True'{return $true};'False'{return $false};'Null'{return $null}
        }
    }
    $document=[Text.Json.JsonDocument]::Parse($Text)
    try{return Convert-JsonElement $document.RootElement}finally{$document.Dispose()}
}
$fixtureManifestText = Get-Content -LiteralPath (Join-Path $PSScriptRoot `
    'rc2-canary-fixture-manifest.json') -Raw
$directoryEffectiveAtMatch = [regex]::Matches($fixtureManifestText,
    '"directoryEffectiveAt"\s*:\s*"(?<value>[^"\\]*)"')
if ($directoryEffectiveAtMatch.Count -ne 1) {
    [Console]::Error.WriteLine(
        'REFUSE_TO_EXECUTE: fixture directoryEffectiveAt must be one unescaped JSON string')
    exit 2
}
$directoryEffectiveAt = $directoryEffectiveAtMatch[0].Groups['value'].Value

if ($Action -eq 'DryRun') {
    & $legacyGuard -Action DryRun
    Write-Output 'FIXTURE_EXECUTION_STRATEGY=HYBRID_APPLICATION_SEEDER'
    Write-Output 'DIRECTORY_HASH_SOURCE=ApprovalRoleCanonical/ApprovalRoleCommandService'
    Write-Output 'WORKFLOW_HASH_SOURCE=WorkflowVersionContentHasher/WorkflowCombinedContentHasher'
    Write-Output 'VERSION_BINDING_HASH_SOURCE=VersionNodeResolverBindingCanonical'
    Write-Output 'MANIFEST_HASH_SOURCE=ResolverBindingManifestCanonical'
    Write-Output 'PLANNED_STAGES=ENVIRONMENT>TEST_ORGANIZATION>TEST_USERS>APPROVAL_ROLE>ROLE_ASSIGNMENTS>DIRECTORY_REVISION>WORKFLOW_DEFINITION>WORKFLOW_VERSION>WORKFLOW_NODE>VERSION_ROLE_BINDING>WORKFLOW_PUBLISH>FINAL_VERIFY'
    Write-Output 'PLANNED_OBJECTS=1_ORG,2_USERS,1_ROLE,2_ASSIGNMENTS,1_REVISION_HEAD,1_REVISION,1_DEFINITION,1_VERSION,1_APPROVAL_NODE,1_VERSION_BINDING,1_MANIFEST,1_RELEASE'
    Write-Output 'APPLICATION_SERVICES=ApprovalRoleCommandService,ApprovalRoleDirectoryService,WorkflowDefinitionApplicationService,VersionResolverBindingApplicationService'
    Write-Output 'BUSINESS_KEYS=RC2_TEST_CANARY_ORG,RC2_TEST_CANARY_USER_01,RC2_TEST_CANARY_USER_02,RC1_TEST_CANARY_APPROVER,RC2_TEST_CANARY_ROLE_APPROVAL,RC2_TEST_ROLE_APPROVAL'
    Write-Output 'ROLE_BINDING=ROLE_DIRECTORY/ROLE_DIRECTORY_V1/ROLE/FIXED_ORG/990101/NODE_ACTIVATED_AT'
    Write-Output ("DIRECTORY_EFFECTIVE_AT={0}" -f $directoryEffectiveAt)
    Write-Output ("REVISION_HASH_EFFECTIVE_AT={0}" -f $directoryEffectiveAt)
    Write-Output ("DIRECTORY_QUERY_EFFECTIVE_AT={0}" -f $directoryEffectiveAt)
    Write-Output 'DIRECTORY_EFFECTIVE_AT_MATCH=YES'
    Write-Output 'DIRECTORY_HASH_PLAN=REVISION_HEAD_AND_DISCOVERY_RESULT_USE_AUTHORITATIVE_FIXTURE_EFFECTIVE_AT'
    Write-Output 'PUBLISH_PLAN=GRAPH_VALIDATION>BINDING_VALIDATION>CONTRACT_VALIDATION>MANIFEST>COMBINED_CONTENT_HASH>VERSION_PUBLISHED>DEFINITION_ACTIVE>VERSION_RELEASE'
    Write-Output 'FIXTURE_DRY_RUN=PASS'
    exit 0
}

if ($Acknowledgement -cne 'RC2_TEST_FIXTURE_EXECUTION') {
    [Console]::Error.WriteLine('REFUSE_TO_EXECUTE: exact RC2 TEST acknowledgement is required')
    exit 2
}
if (-not (Test-Path -LiteralPath $MavenPath -PathType Leaf) -or
        -not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin\java.exe') -PathType Leaf)) {
    [Console]::Error.WriteLine('REFUSE_TO_EXECUTE: Java 21/Maven toolchain missing')
    exit 2
}

if ([string]::IsNullOrWhiteSpace($EvidencePath) -or
        -not (Test-Path -LiteralPath $EvidencePath -PathType Leaf)) {
    [Console]::Error.WriteLine('REFUSE_TO_EXECUTE: Collector environment evidence is required')
    exit 2
}
if ([string]::IsNullOrWhiteSpace($AttestationResultPath)) {
    $AttestationResultPath = Join-Path (Split-Path -Parent $EvidencePath) `
        'environment-attestation.json'
}
& $attestationVerifier -EvidencePath $EvidencePath -ResultPath $AttestationResultPath
& $fixtureContractVerifier
$attestation = Read-StableJson ([IO.File]::ReadAllText($AttestationResultPath))
$attestationId = [string]$attestation['attestationId']
if ([string]$attestation['result'] -cne 'PASS' -or $attestationId -notmatch '^[0-9a-f]{64}$') {
    [Console]::Error.WriteLine('REFUSE_TO_EXECUTE: passed Environment Attestation ID is required')
    exit 2
}
$environmentEvidence = Read-StableJson ([IO.File]::ReadAllText($EvidencePath))
$attestedDataDir = ([IO.Path]::GetFullPath([string]$environmentEvidence['actualMysqlDatadir']) `
    -replace '/','\').TrimEnd([char[]]@('\','/'))
$requestedDataDir = ([IO.Path]::GetFullPath($ExpectedDataDir) `
    -replace '/','\').TrimEnd([char[]]@('\','/'))
if ([string]$environmentEvidence['host'] -cne $HostName -or
        [int]$environmentEvidence['actualListeningPort'] -ne $Port -or
        -not [StringComparer]::OrdinalIgnoreCase.Equals($attestedDataDir,$requestedDataDir)) {
    [Console]::Error.WriteLine('REFUSE_TO_EXECUTE: execution target differs from attested environment')
    exit 2
}
$password = Read-Host 'RC2 TEST database password' -AsSecureString

$pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($password)
try { $plain = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
$previous = @{
    JAVA_HOME=$env:JAVA_HOME; DB_URL=$env:RC2_FIXTURE_DB_URL;
    DB_USERNAME=$env:RC2_FIXTURE_DB_USERNAME; DB_PASSWORD=$env:RC2_FIXTURE_DB_PASSWORD;
    AUTH=$env:RC2_FIXTURE_EXECUTION_AUTHORIZED; JWT=$env:JWT_SECRET
    ATTESTATION_ID=$env:RC2_ENVIRONMENT_ATTESTATION_ID
    ATTESTATION_RESULT=$env:RC2_ENVIRONMENT_ATTESTATION_RESULT_PATH
}
try {
    $env:JAVA_HOME = $JavaHome
    $env:RC2_FIXTURE_DB_URL = "jdbc:mysql://127.0.0.1:$Port/enterprise_platform?allowPublicKeyRetrieval=true&useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false"
    $env:RC2_FIXTURE_DB_USERNAME = $Username
    $env:RC2_FIXTURE_DB_PASSWORD = $plain
    $env:RC2_FIXTURE_EXECUTION_AUTHORIZED = 'RC2_TEST_FIXTURE_EXECUTION'
    $env:RC2_ENVIRONMENT_ATTESTATION_ID = $attestationId
    $env:RC2_ENVIRONMENT_ATTESTATION_RESULT_PATH = [IO.Path]::GetFullPath($AttestationResultPath)
    $env:JWT_SECRET = ([guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N'))
    $mode = if ($Action -eq 'Execute') {'seed'} else {'decommission'}
    Push-Location $backend
    try {
        & $MavenPath '-Dtest=Rc2ControlledCanaryFixtureSeederTest' `
            '-Drc2.fixture.execute=true' "-Drc2.fixture.action=$mode" test
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    } finally { Pop-Location }
} finally {
    $env:JAVA_HOME=$previous.JAVA_HOME; $env:RC2_FIXTURE_DB_URL=$previous.DB_URL
    $env:RC2_FIXTURE_DB_USERNAME=$previous.DB_USERNAME; $env:RC2_FIXTURE_DB_PASSWORD=$previous.DB_PASSWORD
    $env:RC2_FIXTURE_EXECUTION_AUTHORIZED=$previous.AUTH; $env:JWT_SECRET=$previous.JWT
    $env:RC2_ENVIRONMENT_ATTESTATION_ID=$previous.ATTESTATION_ID
    $env:RC2_ENVIRONMENT_ATTESTATION_RESULT_PATH=$previous.ATTESTATION_RESULT
    $plain=$null
}

[CmdletBinding()]
param(
    [string]$ManifestPath = (Join-Path $PSScriptRoot 'rc2-canary-fixture-manifest.json'),
    [string]$ResultPath
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'))

function Read-StableJson([string]$Text) {
    if ($PSVersionTable.PSVersion.Major -le 5) {
        Add-Type -AssemblyName System.Web.Extensions
        $serializer=New-Object System.Web.Script.Serialization.JavaScriptSerializer
        $serializer.MaxJsonLength=[int]::MaxValue
        return $serializer.DeserializeObject($Text)
    }
    function Convert-JsonElement($Element) {
        switch([string]$Element.ValueKind){
            'Object' {$result=@{};foreach($property in $Element.EnumerateObject()){$result[$property.Name]=Convert-JsonElement $property.Value};return $result}
            'Array' {return @($Element.EnumerateArray()|ForEach-Object{Convert-JsonElement $_})}
            'String' {return [string]$Element.GetString()}
            'Number' {[long]$integer=0;if($Element.TryGetInt64([ref]$integer)){return $integer};return [decimal]$Element.GetDecimal()}
            'True' {return $true}
            'False' {return $false}
            'Null' {return $null}
            default {throw "Unsupported JSON token: $($Element.ValueKind)"}
        }
    }
    $document=[Text.Json.JsonDocument]::Parse($Text)
    try{return Convert-JsonElement $document.RootElement}finally{$document.Dispose()}
}
function Get-RawJsonString([string]$Text,[string]$PropertyName) {
    $pattern = '"' + [regex]::Escape($PropertyName) + '"\s*:\s*"(?<value>[^"\\]*)"'
    $matches = [regex]::Matches($Text,$pattern)
    if ($matches.Count -ne 1) { return $null }
    $matches[0].Groups['value'].Value
}
function Add-Check([Collections.Generic.List[string]]$Failures,[Collections.Generic.List[string]]$Checks,
        [bool]$Condition,[string]$Code) {
    $Checks.Add($Code)
    if (-not $Condition) { $Failures.Add($Code) }
}
function Write-Utf8NoBom([string]$Path,[string]$Content) {
    $parent=Split-Path -Parent $Path
    if($parent-and-not(Test-Path -LiteralPath $parent)){New-Item -ItemType Directory -Path $parent -Force|Out-Null}
    [IO.File]::WriteAllText($Path,$Content,(New-Object Text.UTF8Encoding($false)))
}

if (-not (Test-Path -LiteralPath $ManifestPath -PathType Leaf)) { throw 'fixture manifest missing' }
$raw = [IO.File]::ReadAllText($ManifestPath)
$manifest = Read-StableJson $raw
$scope = $manifest['scope']; $workflow = $manifest['workflow']; $window = $manifest['effectiveWindow']
$failures=New-Object Collections.Generic.List[string]
$checks=New-Object Collections.Generic.List[string]
$effectiveAt=Get-RawJsonString $raw 'directoryEffectiveAt'
$effectiveTo=Get-RawJsonString $raw 'assignmentEffectiveTo'

Add-Check $failures $checks ([string]$manifest['fixtureContract'] -ceq 'RC2_CONTROLLED_CANARY_FIXTURE_V1') 'FIXTURE_CONTRACT_VERSION'
Add-Check $failures $checks ([string]$manifest['environmentIdentity'] -ceq 'RC2_TEST') 'FIXTURE_ENVIRONMENT_IDENTITY'
Add-Check $failures $checks ($effectiveAt -is [string] -and $effectiveAt -ceq '2026-01-01T00:00:00Z') 'AUTHORITATIVE_EFFECTIVE_AT'
$validWindow=$false
try{$validWindow=[DateTimeOffset]::Parse($effectiveTo)-gt[DateTimeOffset]::Parse($effectiveAt)}catch{}
Add-Check $failures $checks $validWindow 'ASSIGNMENT_EFFECTIVE_WINDOW'
$users=@($scope['candidateUserIds'])
Add-Check $failures $checks ($users.Count -eq 2) 'EXPECTED_CANDIDATE_COUNT'
Add-Check $failures $checks ($users.Count -ge 1 -and [long]$users[0] -eq 990201) 'ASSIGNMENT_U1_CONTRACT'
Add-Check $failures $checks ($users.Count -ge 2 -and [long]$users[1] -eq 990202) 'ASSIGNMENT_U2_CONTRACT'
Add-Check $failures $checks ([long]$scope['enterpriseId'] -eq 990001 -and [long]$scope['organizationId'] -eq 990101) 'ENTERPRISE_ORGANIZATION_SCOPE'
Add-Check $failures $checks ([string]$scope['roleCode'] -ceq 'RC1_TEST_CANARY_APPROVER') 'APPROVAL_ROLE_CODE'
$reserved=@([long]$scope['enterpriseId'],[long]$scope['organizationId'],[long]$scope['definitionId'],[long]$scope['definitionVersionId'],[long]$scope['roleNodeId'])
Add-Check $failures $checks (@($reserved|Sort-Object -Unique).Count -eq $reserved.Count) 'RESERVED_IDS_UNIQUE'
Add-Check $failures $checks ([string]$workflow['topology'] -ceq 'SINGLE_APPROVAL_NODE' -and [string]$workflow['engineMode'] -ceq 'SINGLE_NODE_LEGACY') 'WORKFLOW_TOPOLOGY'
Add-Check $failures $checks ([string]$workflow['resolverBindingModel'] -ceq 'VERSION_RESOLVER_BINDING_CAPABLE') 'RESOLVER_BINDING_MODEL'
Add-Check $failures $checks ([string]$workflow['resolverCode'] -ceq 'ROLE_DIRECTORY' -and [string]$workflow['resolverVersion'] -ceq 'ROLE_DIRECTORY_V1') 'RESOLVER_DESCRIPTOR'
Add-Check $failures $checks ([string]$workflow['organizationScopeType'] -ceq 'FIXED_ORG' -and [string]$workflow['effectiveTimePolicy'] -ceq 'NODE_ACTIVATED_AT') 'RESOLVER_SCOPE_POLICY'
$contractSource=[IO.File]::ReadAllText((Join-Path $repositoryRoot 'backend\src\test\java\cn\gov\enterprise\modules\workflow\fixture\Rc2ControlledCanaryFixtureContract.java'))
$seederSource=[IO.File]::ReadAllText((Join-Path $repositoryRoot 'backend\src\test\java\cn\gov\enterprise\modules\workflow\fixture\Rc2ControlledCanaryFixtureSeederTest.java'))
Add-Check $failures $checks ($contractSource -match 'directoryEffectiveAt' -and $seederSource -match 'Rc2ControlledCanaryFixtureContract\.DIRECTORY_EFFECTIVE_AT') 'SINGLE_EFFECTIVE_AT_CONTRACT_SOURCE'
Add-Check $failures $checks ($seederSource -notmatch 'ROLE_DIRECTORY_V1",\s*"ACTIVE' -and $seederSource -notmatch 'CandidatePool') 'NO_RUNTIME_ENABLEMENT'

$passed=$failures.Count-eq0
$result=[ordered]@{contractVersion='RC2_FIXTURE_CONTRACT_ATTESTATION_V1';result=$(if($passed){'PASS'}else{'FAIL'});checkCount=$checks.Count;passedCount=$checks.Count-$failures.Count;failedCount=$failures.Count;failedAssertions=@($failures);authoritativeEffectiveAt=$effectiveAt;expectedCandidateCount=2}
if(-not[string]::IsNullOrWhiteSpace($ResultPath)){Write-Utf8NoBom $ResultPath ($result|ConvertTo-Json -Depth 6)}
Write-Output "FIXTURE_CONTRACT=$($result.result)"
Write-Output "FIXTURE_CONTRACT_TESTS=$($result.checkCount)"
Write-Output "FIXTURE_CONTRACT_TESTS_PASS=$($result.passedCount)"
Write-Output "FIXTURE_CONTRACT_TESTS_FAIL=$($result.failedCount)"
Write-Output "FIXTURE_CONTRACT_FAILED_ASSERTIONS=$($failures-join',')"
if(-not$passed){exit 2}

[CmdletBinding()]
param(
    [ValidateSet('DryRun','ContractTest','Preflight','VerifyExisting')]
    [string]$Action = 'DryRun',
    [string]$EvidencePath,
    [string]$ResultPath
)

$ErrorActionPreference = 'Stop'
$collector = Join-Path $PSScriptRoot 'collect-rc2-environment-evidence.ps1'
$verifier = Join-Path $PSScriptRoot 'verify-rc2-environment-attestation.ps1'
$fixtureVerifier = Join-Path $PSScriptRoot 'verify-rc2-fixture-contract.ps1'
$contractTests = Join-Path $PSScriptRoot 'test-rc2-environment-attestation.ps1'

Write-Warning 'DEPRECATED: 00_rc2_canary_guard.ps1 is a compatibility wrapper. Environment Attestation Verifier is the only authoritative eligibility engine.'

if ($Action -eq 'ContractTest') {
    & $contractTests
    exit 0
}
if ($Action -eq 'DryRun') {
    $temporaryRoot = Join-Path ([IO.Path]::GetTempPath()) ('rc2-attestation-dry-run-' + [guid]::NewGuid())
    try {
        New-Item -ItemType Directory -Path $temporaryRoot -Force | Out-Null
        $mockEvidence = Join-Path $temporaryRoot 'environment-evidence.json'
        $mockResult = Join-Path $temporaryRoot 'environment-attestation.json'
        $fixtureResult = Join-Path $temporaryRoot 'fixture-contract.json'
        & $collector -Action Mock -EvidencePath $mockEvidence `
            -MigrationSnapshotDirectory (Join-Path $temporaryRoot 'migration-snapshot')
        & $verifier -EvidencePath $mockEvidence -ResultPath $mockResult
        & $fixtureVerifier -ResultPath $fixtureResult
        Write-Output 'ENVIRONMENT_ATTESTATION_DRY_RUN=PASS'
        Write-Output 'FIXTURE_CONTRACT_DRY_RUN=PASS'
        Write-Output 'READY_TO_EXECUTE_FIXTURE_DRY_RUN=YES'
        Write-Output 'OLD_GUARD_USED_AS_SECOND_AUTHORITY=NO'
        exit 0
    } finally {
        if (Test-Path -LiteralPath $temporaryRoot) {
            [IO.Directory]::Delete($temporaryRoot,$true)
        }
    }
}

if ([string]::IsNullOrWhiteSpace($EvidencePath)) {
    [Console]::Error.WriteLine('REFUSE_TO_EXECUTE: deprecated Guard requires Collector evidence')
    exit 2
}
if ([string]::IsNullOrWhiteSpace($ResultPath)) {
    $ResultPath = Join-Path (Split-Path -Parent $EvidencePath) 'environment-attestation.json'
}
& $verifier -EvidencePath $EvidencePath -ResultPath $ResultPath
& $fixtureVerifier
Write-Output 'GUARD=PASS'
Write-Output 'ENVIRONMENT_ATTESTATION_COMPATIBILITY_GATE=PASS'

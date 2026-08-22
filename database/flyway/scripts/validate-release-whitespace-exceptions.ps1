[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$repositoryRoot = (Resolve-Path (Join-Path $scriptRoot '..\..\..')).Path
$manifestPath = Join-Path $repositoryRoot 'docs\workflow-v1-release-whitespace-exception-manifest.md'

function Fail([string]$message) {
    Write-Error $message
    exit 1
}

Push-Location $repositoryRoot
try {
    if (-not (Test-Path -LiteralPath $manifestPath)) {
        Fail "Exception manifest is missing: $manifestPath"
    }

    $approved = @()
    foreach ($line in Get-Content -LiteralPath $manifestPath) {
        if ($line -match '^\|\s*`([^`]+)`\s*\|\s*(\d+)\s*\|\s*`([^`]+)`\s*\|') {
            $approved += [pscustomobject]@{
                Path = ($Matches[1] -replace '\\', '/')
                Line = [int]$Matches[2]
                Type = $Matches[3]
            }
        }
    }

    if ($approved.Count -ne 19) {
        Fail "Approved exception count drift: expected 19, actual $($approved.Count)"
    }

    $approvedKeys = @($approved | ForEach-Object { "$($_.Path)|$($_.Line)|$($_.Type)" })
    if (@($approvedKeys | Sort-Object -Unique).Count -ne 19) {
        Fail 'Exception manifest contains duplicate path/line/type entries'
    }

    # Gate A: strict whitespace validation of every staged path except the 19
    # explicitly named immutable files. Exclusions are literal and generated
    # from the manifest; there is no wildcard exception.
    $strictArgs = @('diff', '--cached', '--check', '--', '.')
    foreach ($path in @($approved.Path | Sort-Object -Unique)) {
        $strictArgs += ":(exclude,literal)$path"
    }
    $strictOutput = @(& git @strictArgs 2>&1)
    $strictExit = $LASTEXITCODE
    if ($strictExit -ne 0 -or $strictOutput.Count -ne 0) {
        $strictOutput | ForEach-Object { Write-Error $_ }
        Fail 'STRICT_NEW_ASSET_WHITESPACE failed'
    }

    # Gate B: retain the full native check and compare every warning exactly.
    $fullOutput = @(& git diff --cached --check 2>&1)
    $fullExit = $LASTEXITCODE
    $actual = @()
    foreach ($line in $fullOutput) {
        if ($line -notmatch '^(.*):(\d+): (.+)$') {
            Fail "Unrecognized git diff --check output: $line"
        }
        $warningType = switch ($Matches[3]) {
            'new blank line at EOF.' { 'NEW_BLANK_LINE_AT_EOF' }
            default { "UNRECOGNIZED:$($Matches[3])" }
        }
        $actual += [pscustomobject]@{
            Path = ($Matches[1] -replace '\\', '/')
            Line = [int]$Matches[2]
            Type = $warningType
        }
    }

    if ($fullExit -eq 0 -or $actual.Count -ne 19) {
        Fail "Full warning count/exit drift: expected exit non-zero and 19 warnings; exit=$fullExit count=$($actual.Count)"
    }

    $actualKeys = @($actual | ForEach-Object { "$($_.Path)|$($_.Line)|$($_.Type)" })
    $missing = @($approvedKeys | Where-Object { $_ -notin $actualKeys })
    $unapproved = @($actualKeys | Where-Object { $_ -notin $approvedKeys })
    if ($missing.Count -ne 0 -or $unapproved.Count -ne 0) {
        $missing | ForEach-Object { Write-Error "Missing approved warning: $_" }
        $unapproved | ForEach-Object { Write-Error "Unapproved warning: $_" }
        Fail 'Approved exception set does not exactly match git diff --cached --check'
    }

    $canonicalPath = 'database/migration/mysql/V2.6.10__harden_role_runtime_persistence_integrity.sql'
    $expectedSha = '2fad3f67a4a487568f5fc29f40cec73894d89fff65564212a75be98da186f7d6'
    & git diff --quiet -- $canonicalPath
    if ($LASTEXITCODE -ne 0) {
        Fail 'V2.6.10 working tree differs from the staged canonical asset'
    }
    $actualSha = (Get-FileHash -LiteralPath (Join-Path $repositoryRoot $canonicalPath) -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actualSha -ne $expectedSha) {
        Fail "V2.6.10 SHA drift: expected=$expectedSha actual=$actualSha"
    }

    Write-Output 'STRICT_NEW_ASSET_WHITESPACE = PASS'
    Write-Output 'APPROVED_HISTORICAL_WHITESPACE_EXCEPTIONS = 19/19 MATCH'
    Write-Output 'UNAPPROVED_WHITESPACE = 0'
    Write-Output "V2.6.10_SHA256 = $actualSha / PASS"
    Write-Output 'RELEASE_WHITESPACE_EXCEPTION_GOVERNANCE_PASS'
    exit 0
}
finally {
    Pop-Location
}

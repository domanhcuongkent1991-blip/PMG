param(
    [Parameter(Mandatory = $false)]
    [string]$ProjectRoot = ".",

    [Parameter(Mandatory = $false)]
    [switch]$EnforceSourceApproval = $true
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Resolve-Artifact {
    param(
        [Parameter(Mandatory = $true)][string]$Base,
        [Parameter(Mandatory = $true)][string[]]$Candidates
    )
    foreach ($candidate in $Candidates) {
        $path = Join-Path $Base $candidate
        if (Test-Path -LiteralPath $path) {
            return (Resolve-Path -LiteralPath $path).Path
        }
    }
    return $null
}

function Get-FileSha256 {
    param([Parameter(Mandatory = $true)][string]$Path)
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Get-PhasePlanHash {
    param([Parameter(Mandatory = $false)][string[]]$PlanFiles = @())
    if ($null -eq $PlanFiles -or $PlanFiles.Count -eq 0) {
        return $null
    }
    $pairs = @()
    foreach ($path in ($PlanFiles | Sort-Object)) {
        $pairs += "$(Split-Path -Leaf $path):$(Get-FileSha256 -Path $path)"
    }
    $joined = ($pairs -join "|")
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($joined)
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        $digest = $sha.ComputeHash($bytes)
        return [System.BitConverter]::ToString($digest).Replace("-", "").ToLowerInvariant()
    }
    finally {
        $sha.Dispose()
    }
}

function Get-PhaseApprovalRecord {
    param(
        [Parameter(Mandatory = $false)]$State,
        [Parameter(Mandatory = $true)][string]$PhaseName
    )
    if ($null -eq $State) { return $null }
    if ($null -eq $State.approvals) { return $null }
    if ($null -eq $State.approvals.phase_plan) { return $null }
    $property = $State.approvals.phase_plan.PSObject.Properties[$PhaseName]
    if ($null -eq $property) { return $null }
    return $property.Value
}

function Read-LedgerEntries {
    param([Parameter(Mandatory = $true)][string]$LedgerPath)
    $rows = @()
    if (-not (Test-Path -LiteralPath $LedgerPath)) {
        return $rows
    }
    $lines = @(Get-Content -LiteralPath $LedgerPath | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    foreach ($line in $lines) {
        try {
            $rows += @($line | ConvertFrom-Json)
        }
        catch {
            # Keep fail-closed behavior in caller.
        }
    }
    return $rows
}

function Test-McpApprovalEvidence {
    param(
        [Parameter(Mandatory = $false)]$State,
        [Parameter(Mandatory = $true)][string]$LedgerPath,
        [Parameter(Mandatory = $true)][string]$RiskPath
    )

    if (-not (Test-Path -LiteralPath $RiskPath)) {
        return $false
    }
    $riskHash = Get-FileSha256 -Path $RiskPath
    $stateApproved = $false
    if ($null -ne $State -and $null -ne $State.approvals -and $null -ne $State.approvals.mcp) {
        $approval = $State.approvals.mcp
        if ([bool]$approval.approved -and [string]$approval.report_sha256 -eq [string]$riskHash) {
            $stateApproved = $true
        }
    }
    if (-not $stateApproved) {
        return $false
    }

    $ledgerEntries = Read-LedgerEntries -LedgerPath $LedgerPath
    foreach ($entry in $ledgerEntries) {
        if ([string]$entry.type -eq "mcp_approval" -and [bool]$entry.approved -and [string]$entry.report_sha256 -eq [string]$riskHash) {
            return $true
        }
    }
    return $false
}

function Test-PhaseApprovalEvidence {
    param(
        [Parameter(Mandatory = $false)]$State,
        [Parameter(Mandatory = $true)][string]$LedgerPath,
        [Parameter(Mandatory = $true)][string]$PhaseName,
        [Parameter(Mandatory = $false)][string]$PlanHash = ""
    )

    if ([string]::IsNullOrWhiteSpace($PlanHash)) {
        return $false
    }

    $approval = Get-PhaseApprovalRecord -State $State -PhaseName $PhaseName
    if ($null -eq $approval -or -not [bool]$approval.approved) {
        return $false
    }
    if ([string]$approval.plan_sha256 -ne [string]$PlanHash) {
        return $false
    }

    $ledgerEntries = Read-LedgerEntries -LedgerPath $LedgerPath
    foreach ($entry in $ledgerEntries) {
        if ([string]$entry.type -eq "phase_approval" -and [bool]$entry.approved -and [string]$entry.phase_id -eq [string]$PhaseName -and [string]$entry.plan_sha256 -eq [string]$PlanHash) {
            return $true
        }
    }
    return $false
}

function Is-GovernanceOnlyPath {
    param([Parameter(Mandatory = $true)][string]$Path)
    $normalized = $Path.Replace("\", "/").TrimStart("./")
    if ($normalized.StartsWith(".planning/")) { return $true }
    if ($normalized.StartsWith(".workflow-gate/")) { return $true }
    if ($normalized.StartsWith("planning/")) { return $true }
    if ($normalized.StartsWith("reports/")) { return $true }
    if ($normalized.StartsWith(".github/workflows/workflow-order-gate.yml")) { return $true }
    if ($normalized.EndsWith(".md")) { return $true }
    return $false
}

function Get-ChangedFiles {
    param([Parameter(Mandatory = $true)][string]$RepoRoot)
    if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
        return @()
    }
    Push-Location $RepoRoot
    try {
        $inside = (& git rev-parse --is-inside-work-tree 2>$null)
        if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($inside)) {
            return @()
        }

        if (-not [string]::IsNullOrWhiteSpace($env:GITHUB_BASE_REF)) {
            & git fetch --no-tags --depth=1 origin $env:GITHUB_BASE_REF 2>$null | Out-Null
            $baseRef = "origin/$($env:GITHUB_BASE_REF)"
            & git rev-parse --verify $baseRef 2>$null | Out-Null
            if ($LASTEXITCODE -eq 0) {
                $mergeBase = (& git merge-base HEAD $baseRef 2>$null).Trim()
                if (-not [string]::IsNullOrWhiteSpace($mergeBase)) {
                    return @((& git diff --name-only "$mergeBase..HEAD" 2>$null) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
                }
            }
        }

        & git rev-parse --verify HEAD~1 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) {
            return @((& git diff --name-only HEAD~1..HEAD 2>$null) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
        }
        return @()
    }
    finally {
        Pop-Location
    }
}

$root = (Resolve-Path -LiteralPath $ProjectRoot).Path
$gateDir = Join-Path $root ".workflow-gate"

$approvedPlan = Resolve-Artifact -Base $root -Candidates @("planning/11_APPROVED_PROJECT_PLAN.md", "11_APPROVED_PROJECT_PLAN.md")
$skillReport = Resolve-Artifact -Base $root -Candidates @("SKILL_SELECTION_REPORT.md", "reports/SKILL_SELECTION_REPORT.md")
$mcpPlan = Resolve-Artifact -Base $root -Candidates @(".workflow-gate/MCP_EXECUTION_PLAN.json", "MCP_EXECUTION_PLAN.json")
$mcpRisk = Resolve-Artifact -Base $root -Candidates @(".workflow-gate/MCP_RISK_REPORT.md", "MCP_RISK_REPORT.md")

$violations = @()
$state = $null
$statePath = Join-Path $gateDir "state.json"
if (Test-Path -LiteralPath $statePath) {
    try {
        $state = (Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json)
    }
    catch {
        $violations += "RULE_6: state.json is unreadable, cannot verify phase approval gate."
    }
}

if ($null -ne $skillReport -and $null -eq $approvedPlan) {
    $violations += "RULE_2: Skill selection exists before approved project plan."
}

if ($null -ne $mcpPlan -and $null -eq $skillReport) {
    $violations += "RULE_3: MCP planning exists before skill selection report."
}

if ($null -ne $mcpPlan) {
    if ($null -eq $mcpRisk) {
        $violations += "RULE_4: MCP planning exists without MCP risk report."
    }
    $ledgerPath = Join-Path $gateDir "approval-ledger.jsonl"
    $validApproval = $false
    if ($null -ne $mcpRisk) {
        $validApproval = Test-McpApprovalEvidence -State $state -LedgerPath $ledgerPath -RiskPath $mcpRisk
    }
    if (-not $validApproval) {
        $violations += "RULE_4: MCP approval evidence is missing or stale."
    }
}

$phaseRoot = Join-Path $root ".planning/phases"
if (Test-Path -LiteralPath $phaseRoot) {
    $phaseDirs = @(Get-ChildItem -LiteralPath $phaseRoot -Directory | Sort-Object Name)
    $phaseRows = @()
    $ledgerPath = Join-Path $gateDir "approval-ledger.jsonl"
    foreach ($dir in $phaseDirs) {
        $planFiles = @(Get-ChildItem -LiteralPath $dir.FullName -Filter "*-PLAN.md" -File | Sort-Object Name | ForEach-Object { $_.FullName })
        $planCount = $planFiles.Count
        $summaryCount = @(Get-ChildItem -LiteralPath $dir.FullName -Filter "*-SUMMARY.md" -File).Count
        $verificationCount = @(Get-ChildItem -LiteralPath $dir.FullName -Filter "*-VERIFICATION.md" -File).Count
        $planHash = Get-PhasePlanHash -PlanFiles $planFiles
        $approval = Get-PhaseApprovalRecord -State $state -PhaseName $dir.Name
        $approvalPresent = ($null -ne $approval -and [bool]$approval.approved)
        $approvalValid = Test-PhaseApprovalEvidence -State $state -LedgerPath $ledgerPath -PhaseName $dir.Name -PlanHash ([string]$planHash)
        $approvalStale = $false
        if ($approvalPresent -and $null -ne $planHash -and $null -ne $approval.plan_sha256) {
            if ([string]$approval.plan_sha256 -ne [string]$planHash) {
                $approvalStale = $true
            }
        }
        $phaseRows += [ordered]@{
            phase = $dir.Name
            plan_count = $planCount
            summary_count = $summaryCount
            verification_count = $verificationCount
            approval_present = $approvalPresent
            approval_valid = $approvalValid
            approval_stale = $approvalStale
        }
        if ($summaryCount -gt 0 -and $planCount -eq 0) {
            $violations += "RULE_5: Phase '$($dir.Name)' has summary but no plan."
        }
        if ($summaryCount -gt 0 -and -not $approvalValid) {
            $violations += "RULE_6: Phase '$($dir.Name)' executed before valid phase plan approval evidence."
        }
        if ($summaryCount -gt 0 -and $approvalStale) {
            $violations += "RULE_6: Phase '$($dir.Name)' approval is stale after plan changed."
        }
        if ($summaryCount -gt 0 -and $verificationCount -eq 0) {
            $violations += "RULE_7: Phase '$($dir.Name)' has summary but no verification."
        }
    }
    for ($i = 1; $i -lt $phaseRows.Count; $i++) {
        $prev = $phaseRows[$i - 1]
        $curr = $phaseRows[$i]
        if ($curr.summary_count -gt 0 -and $prev.summary_count -gt 0 -and $prev.verification_count -eq 0) {
            $violations += "RULE_8: '$($curr.phase)' progressed before '$($prev.phase)' verification."
        }
    }
}

if ($EnforceSourceApproval) {
    $changedFiles = Get-ChangedFiles -RepoRoot $root
    $sourceChanges = @($changedFiles | Where-Object { -not (Is-GovernanceOnlyPath -Path $_) })
    if ($sourceChanges.Count -gt 0) {
        $validPhaseApproval = $false
        if (Test-Path -LiteralPath $phaseRoot) {
            foreach ($row in $phaseRows) {
                if ($row.plan_count -gt 0 -and [bool]$row.approval_valid -and -not [bool]$row.approval_stale) {
                    $validPhaseApproval = $true
                    break
                }
            }
        }
        if (-not $validPhaseApproval) {
            $violations += "RULE_6: Source files changed but no valid phase approval for current plan hash."
        }
    }
}

$result = [ordered]@{
    status = if ($violations.Count -eq 0) { "PASS" } else { "FAIL" }
    violations = ($violations | Sort-Object -Unique)
}

$result | ConvertTo-Json -Depth 10

if ($violations.Count -gt 0) {
    exit 2
}

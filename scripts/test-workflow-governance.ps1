param(
  [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Assert-True {
  param(
    [Parameter(Mandatory = $true)][bool]$Condition,
    [Parameter(Mandatory = $true)][string]$Message
  )
  if (-not $Condition) {
    throw $Message
  }
}

function Assert-Equals {
  param(
    [Parameter(Mandatory = $true)]$Expected,
    [Parameter(Mandatory = $true)]$Actual,
    [Parameter(Mandatory = $true)][string]$Message
  )
  if ($Expected -ne $Actual) {
    throw "$Message Expected=[$Expected] Actual=[$Actual]"
  }
}

function Invoke-ExpectFailure {
  param(
    [Parameter(Mandatory = $true)][scriptblock]$Action,
    [Parameter(Mandatory = $true)][string]$Message
  )

  $failed = $false
  try {
    & $Action | Out-Null
    if ($global:LASTEXITCODE -ne 0) {
      $failed = $true
    }
  } catch {
    $failed = $true
  }

  Assert-True $failed $Message
}

function Reset-GateState {
  param([Parameter(Mandatory = $true)][string]$Root)

  @{
    version = "1.0.0"
    current_level = "0"
    updated_at_utc = "2026-05-06T00:00:00Z"
    transitions = @()
    approvals = @{
      mcp = $null
      phase_plan = @{}
    }
  } | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $Root ".workflow-gate/state.json") -Encoding UTF8
  "" | Set-Content -LiteralPath (Join-Path $Root ".workflow-gate/approval-ledger.jsonl") -Encoding UTF8
}

function Invoke-ApprovalPairConcurrently {
  param(
    [Parameter(Mandatory = $true)][string]$Root,
    [Parameter(Mandatory = $true)][string]$McpScript,
    [Parameter(Mandatory = $true)][string]$PhaseScript
  )

  $mcpJob = Start-Job -ScriptBlock {
    param($Script, $RootPath)
    $output = & powershell -NoProfile -ExecutionPolicy Bypass -File $Script -ProjectRoot $RootPath -ApprovedBy "test" -Reason "concurrency test" -Json 2>&1
    if ($LASTEXITCODE -ne 0) {
      throw (($output | Out-String).Trim())
    }
    $output
  } -ArgumentList $McpScript, $Root

  $phaseJob = Start-Job -ScriptBlock {
    param($Script, $RootPath)
    $output = & powershell -NoProfile -ExecutionPolicy Bypass -File $Script -ProjectRoot $RootPath -PhaseName "workflow-hardening" -ApprovedBy "test" -Reason "concurrency test" -Json 2>&1
    if ($LASTEXITCODE -ne 0) {
      throw (($output | Out-String).Trim())
    }
    $output
  } -ArgumentList $PhaseScript, $Root

  Wait-Job -Job $mcpJob, $phaseJob | Out-Null
  $mcpDiagnostics = (Receive-Job -Job $mcpJob -Keep | Out-String).Trim()
  $phaseDiagnostics = (Receive-Job -Job $phaseJob -Keep | Out-String).Trim()

  Assert-Equals "Completed" ([string]$mcpJob.State) "concurrent MCP approval should complete. Output=[$mcpDiagnostics]"
  Assert-Equals "Completed" ([string]$phaseJob.State) "concurrent phase approval should complete. Output=[$phaseDiagnostics]"

  Remove-Job -Job $mcpJob, $phaseJob -Force
}

function Assert-GateStateHasBothApprovals {
  param([Parameter(Mandatory = $true)][string]$Root)

  $state = Get-Content -LiteralPath (Join-Path $Root ".workflow-gate/state.json") -Raw | ConvertFrom-Json
  Assert-True ([bool]$state.approvals.mcp.approved) "state should keep MCP approval after concurrent writes."
  $phaseRecord = $state.approvals.phase_plan.PSObject.Properties["workflow-hardening"].Value
  Assert-True ([bool]$phaseRecord.approved) "state should keep phase approval after concurrent writes."
}

$approveScript = Join-Path $ProjectRoot "scripts/approve-mcp-workflow.ps1"
$approvePhaseScript = Join-Path $ProjectRoot "scripts/approve-phase-plan.ps1"
$checkScript = Join-Path $ProjectRoot "scripts/check-workflow.ps1"
$validateScript = Join-Path $ProjectRoot "scripts/validate-workflow-governance.ps1"
$orderGateScript = Join-Path $ProjectRoot ".workflow-gate/check-workflow-order.ps1"

$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("codex-workflow-governance-test-" + [guid]::NewGuid().ToString("N"))

try {
  New-Item -ItemType Directory -Path (Join-Path $tempRoot ".workflow-gate") -Force | Out-Null
  New-Item -ItemType Directory -Path (Join-Path $tempRoot "planning") -Force | Out-Null
  New-Item -ItemType Directory -Path (Join-Path $tempRoot "scripts") -Force | Out-Null
  New-Item -ItemType Directory -Path (Join-Path $tempRoot ".planning/phases/workflow-hardening") -Force | Out-Null

  Copy-Item -LiteralPath $orderGateScript -Destination (Join-Path $tempRoot ".workflow-gate/check-workflow-order.ps1") -Force
  "process.exit(0);" | Set-Content -LiteralPath (Join-Path $tempRoot "scripts/prevent-secrets.js") -Encoding UTF8
  "# Approved plan fixture" | Set-Content -LiteralPath (Join-Path $tempRoot "planning/11_APPROVED_PROJECT_PLAN.md") -Encoding UTF8
  "# Skill report fixture" | Set-Content -LiteralPath (Join-Path $tempRoot "SKILL_SELECTION_REPORT.md") -Encoding UTF8
  "# Workflow hardening plan fixture" | Set-Content -LiteralPath (Join-Path $tempRoot ".planning/phases/workflow-hardening/WORKFLOW-HARDENING-PLAN.md") -Encoding UTF8
  '{"plan":"fixture"}' | Set-Content -LiteralPath (Join-Path $tempRoot ".workflow-gate/MCP_EXECUTION_PLAN.json") -Encoding UTF8
  "# Risk report fixture" | Set-Content -LiteralPath (Join-Path $tempRoot ".workflow-gate/MCP_RISK_REPORT.md") -Encoding UTF8
  @{
    generated_at_utc = "2026-05-06T00:00:00Z"
    report_sha256 = "stale"
    execution_plan_sha256 = "stale"
  } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $tempRoot ".workflow-gate/MCP_RISK_REPORT.meta.json") -Encoding UTF8
  @{
    version = "1.0.0"
    current_level = "0"
    updated_at_utc = "2026-05-06T00:00:00Z"
    transitions = @()
    approvals = @{
      mcp = $null
      phase_plan = @{}
    }
  } | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $tempRoot ".workflow-gate/state.json") -Encoding UTF8
  "" | Set-Content -LiteralPath (Join-Path $tempRoot ".workflow-gate/approval-ledger.jsonl") -Encoding UTF8

  Push-Location $tempRoot
  try {
    & git init | Out-Null
    & git config user.email "workflow-test@example.invalid" | Out-Null
    & git config user.name "Workflow Test" | Out-Null
    & git config core.autocrlf false | Out-Null
    & git add . | Out-Null
    & git commit -m "fixture" | Out-Null
    & git commit --allow-empty -m "empty follow-up" | Out-Null
  } finally {
    Pop-Location
  }

  $approvalJson = & powershell -NoProfile -ExecutionPolicy Bypass -File $approveScript `
    -ProjectRoot $tempRoot `
    -ApprovedBy "test" `
    -Reason "unit test" `
    -Json
  $approval = $approvalJson | ConvertFrom-Json

  Assert-Equals "PASS" $approval.status "MCP approval script should pass."
  Assert-True (-not [string]::IsNullOrWhiteSpace($approval.report_sha256)) "approval should include report hash."

  $state = Get-Content -LiteralPath (Join-Path $tempRoot ".workflow-gate/state.json") -Raw | ConvertFrom-Json
  Assert-True ([bool]$state.approvals.mcp.approved) "state should record approved MCP evidence."
  Assert-Equals $approval.report_sha256 $state.approvals.mcp.report_sha256 "state hash should match approval output."

  $phaseApprovalJson = & powershell -NoProfile -ExecutionPolicy Bypass -File $approvePhaseScript `
    -ProjectRoot $tempRoot `
    -PhaseName "workflow-hardening" `
    -ApprovedBy "test" `
    -Reason "unit test" `
    -Json
  $phaseApproval = $phaseApprovalJson | ConvertFrom-Json
  Assert-Equals "PASS" $phaseApproval.status "phase approval script should pass."
  Assert-True (-not [string]::IsNullOrWhiteSpace($phaseApproval.plan_sha256)) "phase approval should include plan hash."

  $stateAfterPhaseApproval = Get-Content -LiteralPath (Join-Path $tempRoot ".workflow-gate/state.json") -Raw | ConvertFrom-Json
  $phaseRecord = $stateAfterPhaseApproval.approvals.phase_plan.PSObject.Properties["workflow-hardening"].Value
  Assert-True ([bool]$phaseRecord.approved) "state should record approved phase plan evidence."
  Assert-Equals $phaseApproval.plan_sha256 $phaseRecord.plan_sha256 "state phase hash should match approval output."

  $ledgerRows = @(Get-Content -LiteralPath (Join-Path $tempRoot ".workflow-gate/approval-ledger.jsonl") | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
  Assert-True ($ledgerRows.Count -ge 1) "ledger should contain approval entries."
  $ledgerEntry = $null
  foreach ($row in $ledgerRows) {
    $candidate = $row | ConvertFrom-Json
    if ([string]$candidate.type -eq "mcp_approval") {
      $ledgerEntry = $candidate
      break
    }
  }
  Assert-True ($null -ne $ledgerEntry) "ledger should contain an MCP approval entry."
  Assert-Equals "mcp_approval" $ledgerEntry.type "ledger should record MCP approval type."
  Assert-Equals $approval.report_sha256 $ledgerEntry.report_sha256 "ledger hash should match approval output."

  $phaseLedgerEntry = $null
  foreach ($row in $ledgerRows) {
    $candidate = $row | ConvertFrom-Json
    if ([string]$candidate.type -eq "phase_approval") {
      $phaseLedgerEntry = $candidate
      break
    }
  }
  Assert-True ($null -ne $phaseLedgerEntry) "ledger should contain a phase approval entry using the upgraded schema."
  Assert-Equals "workflow-hardening" $phaseLedgerEntry.phase_id "phase approval ledger should use phase_id."
  Assert-Equals $phaseApproval.plan_sha256 $phaseLedgerEntry.plan_sha256 "phase approval ledger hash should match approval output."

  $validateJson = & powershell -NoProfile -ExecutionPolicy Bypass -File $validateScript -ProjectRoot $tempRoot -Json
  $validate = $validateJson | ConvertFrom-Json
  Assert-Equals "PASS" $validate.status "schema validation should pass after approval script updates evidence."

  $gateJson = & $orderGateScript -ProjectRoot $tempRoot -EnforceSourceApproval:$false
  $gate = $gateJson | ConvertFrom-Json
  Assert-Equals "PASS" $gate.status "workflow order gate should pass with canonical artifacts and approval evidence."

  $checkJson = & powershell -NoProfile -ExecutionPolicy Bypass -File $checkScript -ProjectRoot $tempRoot -Json
  $check = $checkJson | ConvertFrom-Json
  Assert-Equals "PASS" $check.status "workflow check should pass when all gates pass."

  for ($i = 0; $i -lt 3; $i++) {
    Reset-GateState -Root $tempRoot
    Invoke-ApprovalPairConcurrently -Root $tempRoot -McpScript $approveScript -PhaseScript $approvePhaseScript
    Assert-GateStateHasBothApprovals -Root $tempRoot
  }

  Add-Content -LiteralPath (Join-Path $tempRoot ".workflow-gate/MCP_RISK_REPORT.md") -Value "tamper" -Encoding UTF8

  Invoke-ExpectFailure -Message "schema validation should fail when risk report changes after approval." -Action {
    & powershell -NoProfile -ExecutionPolicy Bypass -File $validateScript -ProjectRoot $tempRoot -Json
  }

  "PASS"
} finally {
  if (Test-Path -LiteralPath $tempRoot) {
    Remove-Item -LiteralPath $tempRoot -Recurse -Force
  }
}

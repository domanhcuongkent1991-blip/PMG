param(
  [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path,
  [switch]$Json
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Get-FileSha256 {
  param([Parameter(Mandatory = $true)][string]$Path)
  return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Read-JsonRequired {
  param(
    [Parameter(Mandatory = $true)][string]$Path,
    [Parameter(Mandatory = $true)][string]$Label,
    [System.Collections.ArrayList]$Violations
  )
  if (-not (Test-Path -LiteralPath $Path)) {
    [void]$Violations.Add("$Label is missing: $Path")
    return $null
  }
  try {
    $raw = Get-Content -LiteralPath $Path -Raw
    if ([string]::IsNullOrWhiteSpace($raw)) {
      [void]$Violations.Add("$Label is empty: $Path")
      return $null
    }
    return ($raw | ConvertFrom-Json)
  } catch {
    [void]$Violations.Add("$Label is invalid JSON: $($_.Exception.Message)")
    return $null
  }
}

function Read-LedgerEntries {
  param(
    [Parameter(Mandatory = $true)][string]$Path,
    [System.Collections.ArrayList]$Violations
  )
  $entries = @()
  if (-not (Test-Path -LiteralPath $Path)) {
    [void]$Violations.Add("approval ledger is missing: $Path")
    return $entries
  }

  $lineNumber = 0
  foreach ($line in (Get-Content -LiteralPath $Path)) {
    $lineNumber++
    if ([string]::IsNullOrWhiteSpace($line)) {
      continue
    }
    try {
      $entries += @($line | ConvertFrom-Json)
    } catch {
      [void]$Violations.Add("approval ledger line $lineNumber is invalid JSON.")
    }
  }
  return $entries
}

$root = (Resolve-Path -LiteralPath $ProjectRoot).Path
$gateDir = Join-Path $root ".workflow-gate"
$riskPath = Join-Path $gateDir "MCP_RISK_REPORT.md"
$metaPath = Join-Path $gateDir "MCP_RISK_REPORT.meta.json"
$planPath = Join-Path $gateDir "MCP_EXECUTION_PLAN.json"
$statePath = Join-Path $gateDir "state.json"
$ledgerPath = Join-Path $gateDir "approval-ledger.jsonl"

$violations = New-Object System.Collections.ArrayList

if (-not (Test-Path -LiteralPath $riskPath)) {
  [void]$violations.Add("MCP risk report is missing: $riskPath")
}
if (-not (Test-Path -LiteralPath $planPath)) {
  [void]$violations.Add("MCP execution plan is missing: $planPath")
}

$meta = Read-JsonRequired -Path $metaPath -Label "MCP risk report metadata" -Violations $violations
$state = Read-JsonRequired -Path $statePath -Label "workflow gate state" -Violations $violations
$ledgerEntries = Read-LedgerEntries -Path $ledgerPath -Violations $violations

$riskHash = $null
$planHash = $null
if (Test-Path -LiteralPath $riskPath) {
  $riskHash = Get-FileSha256 -Path $riskPath
}
if (Test-Path -LiteralPath $planPath) {
  $planHash = Get-FileSha256 -Path $planPath
}

if ($null -ne $meta) {
  if ($null -eq $meta.PSObject.Properties["report_sha256"] -or [string]$meta.report_sha256 -ne [string]$riskHash) {
    [void]$violations.Add("MCP risk report metadata hash is missing or stale.")
  }
  if ($null -eq $meta.PSObject.Properties["execution_plan_sha256"] -or [string]$meta.execution_plan_sha256 -ne [string]$planHash) {
    [void]$violations.Add("MCP execution plan metadata hash is missing or stale.")
  }
}

$approval = $null
if ($null -eq $state) {
  [void]$violations.Add("workflow gate state cannot be checked.")
} elseif ($null -eq $state.PSObject.Properties["approvals"] -or $null -eq $state.approvals) {
  [void]$violations.Add("workflow gate state is missing approvals.")
} elseif ($null -eq $state.approvals.PSObject.Properties["mcp"] -or $null -eq $state.approvals.mcp) {
  [void]$violations.Add("workflow gate state is missing MCP approval.")
} else {
  $approval = $state.approvals.mcp
  if (-not [bool]$approval.approved) {
    [void]$violations.Add("workflow gate MCP approval is not approved.")
  }
  if ([string]$approval.report_sha256 -ne [string]$riskHash) {
    [void]$violations.Add("workflow gate MCP approval report hash is missing or stale.")
  }
  if ([string]$approval.execution_plan_sha256 -ne [string]$planHash) {
    [void]$violations.Add("workflow gate MCP approval execution plan hash is missing or stale.")
  }
}

$ledgerMatch = $false
foreach ($entry in $ledgerEntries) {
  if ([string]$entry.type -eq "mcp_approval" -and [bool]$entry.approved -and [string]$entry.report_sha256 -eq [string]$riskHash) {
    $ledgerMatch = $true
    break
  }
}
if (-not $ledgerMatch) {
  [void]$violations.Add("approval ledger is missing a matching MCP approval entry.")
}

$result = [ordered]@{
  status = if ($violations.Count -eq 0) { "PASS" } else { "FAIL" }
  violations = @($violations | Sort-Object -Unique)
}

if ($Json) {
  $result | ConvertTo-Json -Depth 10
} else {
  $result | ConvertTo-Json -Depth 10
}

if ($violations.Count -gt 0) {
  exit 2
}

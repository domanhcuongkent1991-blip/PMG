param(
  [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path,
  [string]$ApprovedBy = "codex",
  [string]$Reason = "MCP workflow approved by operator.",
  [switch]$Json
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Get-FileSha256 {
  param([Parameter(Mandatory = $true)][string]$Path)
  return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Set-ObjectProperty {
  param(
    [Parameter(Mandatory = $true)][psobject]$Object,
    [Parameter(Mandatory = $true)][string]$Name,
    [AllowNull()]$Value
  )
  if ($null -eq $Object.PSObject.Properties[$Name]) {
    $Object | Add-Member -MemberType NoteProperty -Name $Name -Value $Value
  } else {
    $Object.PSObject.Properties[$Name].Value = $Value
  }
}

function Ensure-ObjectProperty {
  param(
    [Parameter(Mandatory = $true)][psobject]$Object,
    [Parameter(Mandatory = $true)][string]$Name
  )
  $prop = $Object.PSObject.Properties[$Name]
  if ($null -eq $prop -or $null -eq $prop.Value -or $prop.Value -is [string] -or $prop.Value -is [bool] -or $prop.Value -is [int]) {
    if ($null -eq $prop) {
      $Object | Add-Member -MemberType NoteProperty -Name $Name -Value ([pscustomobject]@{})
    } else {
      $Object.PSObject.Properties[$Name].Value = [pscustomobject]@{}
    }
  }
}

function Read-JsonObject {
  param(
    [Parameter(Mandatory = $true)][string]$Path,
    [Parameter(Mandatory = $true)][psobject]$Fallback
  )
  if (-not (Test-Path -LiteralPath $Path)) {
    return $Fallback
  }
  $raw = Get-Content -LiteralPath $Path -Raw
  if ([string]::IsNullOrWhiteSpace($raw)) {
    return $Fallback
  }
  return ($raw | ConvertFrom-Json)
}

function Test-McpLedgerEntryExists {
  param(
    [Parameter(Mandatory = $true)][string]$Path,
    [Parameter(Mandatory = $true)][string]$ReportHash,
    [Parameter(Mandatory = $true)][string]$PlanHash
  )
  if (-not (Test-Path -LiteralPath $Path)) {
    return $false
  }
  foreach ($line in (Get-Content -LiteralPath $Path)) {
    if ([string]::IsNullOrWhiteSpace($line)) {
      continue
    }
    try {
      $entry = $line | ConvertFrom-Json
      if ([string]$entry.type -eq "mcp_approval" -and [bool]$entry.approved -and [string]$entry.report_sha256 -eq $ReportHash -and [string]$entry.execution_plan_sha256 -eq $PlanHash) {
        return $true
      }
    } catch {
      continue
    }
  }
  return $false
}

function Get-WorkflowMutexName {
  param([Parameter(Mandatory = $true)][string]$ProjectRoot)
  $bytes = [System.Text.Encoding]::UTF8.GetBytes($ProjectRoot.ToLowerInvariant())
  $sha = [System.Security.Cryptography.SHA256]::Create()
  try {
    $digest = $sha.ComputeHash($bytes)
    $suffix = [System.BitConverter]::ToString($digest).Replace("-", "").ToLowerInvariant()
    return "Local\CodexWorkflowApproval-$suffix"
  } finally {
    $sha.Dispose()
  }
}

$root = (Resolve-Path -LiteralPath $ProjectRoot).Path
$gateDir = Join-Path $root ".workflow-gate"
$riskPath = Join-Path $gateDir "MCP_RISK_REPORT.md"
$metaPath = Join-Path $gateDir "MCP_RISK_REPORT.meta.json"
$planPath = Join-Path $gateDir "MCP_EXECUTION_PLAN.json"
$statePath = Join-Path $gateDir "state.json"
$ledgerPath = Join-Path $gateDir "approval-ledger.jsonl"

if (-not (Test-Path -LiteralPath $riskPath)) {
  throw "Missing MCP risk report: $riskPath"
}
if (-not (Test-Path -LiteralPath $planPath)) {
  throw "Missing MCP execution plan: $planPath"
}
if (-not (Test-Path -LiteralPath $gateDir)) {
  New-Item -ItemType Directory -Path $gateDir -Force | Out-Null
}

$now = [DateTime]::UtcNow.ToString("o")
$riskHash = Get-FileSha256 -Path $riskPath
$planHash = Get-FileSha256 -Path $planPath

$mutex = New-Object System.Threading.Mutex($false, (Get-WorkflowMutexName -ProjectRoot $root))
$lockTaken = $false
try {
  $lockTaken = $mutex.WaitOne([TimeSpan]::FromSeconds(30))
  if (-not $lockTaken) {
    throw "Timed out waiting for workflow approval lock."
  }

$meta = Read-JsonObject -Path $metaPath -Fallback ([pscustomobject]@{})
Set-ObjectProperty -Object $meta -Name "generated_at_utc" -Value $now
Set-ObjectProperty -Object $meta -Name "report_sha256" -Value $riskHash
Set-ObjectProperty -Object $meta -Name "execution_plan_sha256" -Value $planHash
$meta | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $metaPath -Encoding UTF8

$stateFallback = [pscustomobject]@{
  version = "1.0.0"
  current_level = "0"
  updated_at_utc = $now
  transitions = @()
  approvals = [pscustomobject]@{
    mcp = $null
    phase_plan = [pscustomobject]@{}
  }
}
$state = Read-JsonObject -Path $statePath -Fallback $stateFallback
Set-ObjectProperty -Object $state -Name "updated_at_utc" -Value $now
Ensure-ObjectProperty -Object $state -Name "approvals"
if ($null -eq $state.approvals.PSObject.Properties["phase_plan"]) {
  $state.approvals | Add-Member -MemberType NoteProperty -Name "phase_plan" -Value ([pscustomobject]@{})
}

$approval = [pscustomobject]@{
  approved = $true
  approved_at_utc = $now
  approved_by = $ApprovedBy
  report_sha256 = $riskHash
  execution_plan_sha256 = $planHash
  risk_report_path = ".workflow-gate/MCP_RISK_REPORT.md"
  reason = $Reason
}
Set-ObjectProperty -Object $state.approvals -Name "mcp" -Value $approval
$state | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $statePath -Encoding UTF8

if (-not (Test-McpLedgerEntryExists -Path $ledgerPath -ReportHash $riskHash -PlanHash $planHash)) {
  $ledgerEntry = [ordered]@{
    type = "mcp_approval"
    approved = $true
    approved_at_utc = $now
    approved_by = $ApprovedBy
    report_sha256 = $riskHash
    execution_plan_sha256 = $planHash
    risk_report_path = ".workflow-gate/MCP_RISK_REPORT.md"
    reason = $Reason
  }
  ($ledgerEntry | ConvertTo-Json -Depth 10 -Compress) | Add-Content -LiteralPath $ledgerPath -Encoding UTF8
}

$result = [ordered]@{
  status = "PASS"
  report_sha256 = $riskHash
  execution_plan_sha256 = $planHash
  state_path = $statePath
  ledger_path = $ledgerPath
}
} finally {
  if ($lockTaken) {
    $mutex.ReleaseMutex()
  }
  $mutex.Dispose()
}

if ($Json) {
  $result | ConvertTo-Json -Depth 10
} else {
  Write-Host "MCP workflow approval recorded."
  Write-Host "Risk report SHA256: $riskHash"
}

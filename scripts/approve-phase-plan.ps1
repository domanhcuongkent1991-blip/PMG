param(
  [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path,
  [Parameter(Mandatory = $true)][string]$PhaseName,
  [string]$ApprovedBy = "codex",
  [string]$Reason = "Phase plan approved by operator.",
  [switch]$Json
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Get-FileSha256 {
  param([Parameter(Mandatory = $true)][string]$Path)
  return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Get-PhasePlanHash {
  param([Parameter(Mandatory = $true)][string[]]$PlanFiles)
  if ($PlanFiles.Count -eq 0) {
    throw "No *-PLAN.md files found for phase."
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
  } finally {
    $sha.Dispose()
  }
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

function Test-PhaseLedgerEntryExists {
  param(
    [Parameter(Mandatory = $true)][string]$Path,
    [Parameter(Mandatory = $true)][string]$Phase,
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
      if ([string]$entry.type -eq "phase_approval" -and [bool]$entry.approved -and [string]$entry.phase_id -eq $Phase -and [string]$entry.plan_sha256 -eq $PlanHash) {
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
$statePath = Join-Path $gateDir "state.json"
$ledgerPath = Join-Path $gateDir "approval-ledger.jsonl"
$phaseDir = Join-Path (Join-Path $root ".planning/phases") $PhaseName

if (-not (Test-Path -LiteralPath $phaseDir)) {
  throw "Phase directory not found: $phaseDir"
}

$planFiles = @(Get-ChildItem -LiteralPath $phaseDir -Filter "*-PLAN.md" -File | Sort-Object Name | ForEach-Object { $_.FullName })
$planHash = Get-PhasePlanHash -PlanFiles $planFiles
$now = [DateTime]::UtcNow.ToString("o")

if (-not (Test-Path -LiteralPath $gateDir)) {
  New-Item -ItemType Directory -Path $gateDir -Force | Out-Null
}

$mutex = New-Object System.Threading.Mutex($false, (Get-WorkflowMutexName -ProjectRoot $root))
$lockTaken = $false
try {
  $lockTaken = $mutex.WaitOne([TimeSpan]::FromSeconds(30))
  if (-not $lockTaken) {
    throw "Timed out waiting for workflow approval lock."
  }

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
Ensure-ObjectProperty -Object $state.approvals -Name "phase_plan"

$approval = [pscustomobject]@{
  approved = $true
  approved_at_utc = $now
  approved_by = $ApprovedBy
  plan_sha256 = $planHash
  phase_id = $PhaseName
  reason = $Reason
}
Set-ObjectProperty -Object $state.approvals.phase_plan -Name $PhaseName -Value $approval
$state | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $statePath -Encoding UTF8

if (-not (Test-PhaseLedgerEntryExists -Path $ledgerPath -Phase $PhaseName -PlanHash $planHash)) {
  $ledgerEntry = [ordered]@{
    type = "phase_approval"
    approved = $true
    at_utc = $now
    approved_by = $ApprovedBy
    phase_id = $PhaseName
    plan_sha256 = $planHash
    reason = $Reason
  }
  ($ledgerEntry | ConvertTo-Json -Depth 10 -Compress) | Add-Content -LiteralPath $ledgerPath -Encoding UTF8
}

$result = [ordered]@{
  status = "PASS"
  phase = $PhaseName
  plan_sha256 = $planHash
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
  Write-Host "Phase plan approval recorded: $PhaseName"
  Write-Host "Plan SHA256: $planHash"
}

param(
  [string]$CodexHome = "",
  [switch]$StopCodexProcesses,
  [switch]$RelaunchCodex,
  [int]$WaitTimeoutSeconds = 30
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Get-CodexProcesses {
  return @(Get-Process -ErrorAction SilentlyContinue | Where-Object { $_.ProcessName -match "codex" })
}

function Stop-CodexProcessesSafely {
  param(
    [int]$TimeoutSeconds = 30
  )

  $running = @(Get-CodexProcesses)
  $mainCodexPath = ""
  foreach ($proc in $running) {
    try {
      if ($proc.ProcessName -eq "Codex" -and -not [string]::IsNullOrWhiteSpace($proc.Path)) {
        $mainCodexPath = $proc.Path
        break
      }
    }
    catch {
      # Some process objects may not expose Path without elevation.
    }
  }

  foreach ($proc in $running) {
    try {
      if ($proc.MainWindowHandle -ne 0) {
        $null = $proc.CloseMainWindow()
      }
    }
    catch {
      # Fall through to forced stop after the wait window.
    }
  }

  $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
  while ((Get-Date) -lt $deadline) {
    if (@(Get-CodexProcesses).Count -eq 0) {
      return [ordered]@{ codex_path = $mainCodexPath; forced = $false }
    }
    Start-Sleep -Milliseconds 500
  }

  foreach ($proc in @(Get-CodexProcesses)) {
    try {
      Stop-Process -Id $proc.Id -Force -ErrorAction Stop
    }
    catch {
      throw "Cannot stop Codex process '$($proc.ProcessName)' (PID $($proc.Id)): $($_.Exception.Message)"
    }
  }

  return [ordered]@{ codex_path = $mainCodexPath; forced = $true }
}

function Resolve-CodexHome {
  param([string]$InputPath)
  if (-not [string]::IsNullOrWhiteSpace($InputPath)) {
    return $InputPath
  }
  return (Join-Path $env:USERPROFILE ".codex")
}

$projectRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$doctorScript = Join-Path $projectRoot "scripts\codex-session-path-doctor.ps1"
if (-not (Test-Path -LiteralPath $doctorScript)) {
  throw "Missing doctor script: $doctorScript"
}

$codexStopInfo = [ordered]@{ codex_path = ""; forced = $false }
$running = @(Get-CodexProcesses)
if (@($running).Count -gt 0) {
  if (-not $StopCodexProcesses) {
    $names = (@($running) | Select-Object -ExpandProperty ProcessName -Unique) -join ", "
    throw "Codex is running ($names). Close Codex Desktop completely before hard fix, or run this script with -StopCodexProcesses from an external PowerShell window."
  }
  Write-Host "[hard-fix] Stopping Codex processes..."
  $codexStopInfo = Stop-CodexProcessesSafely -TimeoutSeconds $WaitTimeoutSeconds
}

$codexHomePath = Resolve-CodexHome -InputPath $CodexHome
Write-Host "[hard-fix] Codex home: $codexHomePath"

$fixJson = & powershell -NoProfile -ExecutionPolicy Bypass -File $doctorScript -Action fix -CodexHome $codexHomePath -NormalizeGlobalState
$fix = $fixJson | ConvertFrom-Json
if (-not [bool]$fix.fixed) {
  $err = [string]$fix.error
  if ([string]::IsNullOrWhiteSpace($err)) {
    $err = "Unknown failure in fix step."
  }
  throw "Hard fix failed: $err"
}

$auditJson = & powershell -NoProfile -ExecutionPolicy Bypass -File $doctorScript -Action audit -CodexHome $codexHomePath
$audit = $auditJson | ConvertFrom-Json
$remainingPrefix = [int]$audit.totals.with_extended_prefix
$remainingMismatch = [int]$audit.totals.mismatch_db_vs_session_cwd

$summary = [ordered]@{
  status = "ok"
  backup_dir = $fix.backup_dir
  totals = $audit.totals
  stopped_codex = [bool]$StopCodexProcesses
  forced_stop = [bool]$codexStopInfo.forced
  message = "Hard fix completed."
}

if ($remainingPrefix -gt 0) {
  $summary.status = "warning"
  $summary.message = "Remaining prefixed paths detected. This usually means your Codex app version is still re-writing Windows verbatim paths. Update Codex Desktop, then rerun hard fix."
}

if ($remainingMismatch -gt 0) {
  $summary.status = "warning"
  if ($summary.message -notmatch "Remaining prefixed paths") {
    $summary.message = "Remaining path mismatch detected. Review sample in doctor audit output."
  }
}

if ($RelaunchCodex) {
  $codexPath = [string]$codexStopInfo.codex_path
  if ([string]::IsNullOrWhiteSpace($codexPath) -or -not (Test-Path -LiteralPath $codexPath)) {
    $summary.status = "warning"
    $summary.message = $summary.message + " Codex was not relaunched because the executable path could not be resolved."
  }
  else {
    Start-Process -FilePath $codexPath | Out-Null
    $summary["relaunched_codex"] = $true
  }
}

$summary | ConvertTo-Json -Depth 25

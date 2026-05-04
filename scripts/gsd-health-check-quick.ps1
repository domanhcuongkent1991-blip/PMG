param(
  [string]$ProjectPath = "",
  [switch]$Strict
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Normalize-PathIdentity {
  param([string]$PathValue)
  if ([string]::IsNullOrWhiteSpace($PathValue)) {
    return ""
  }
  $value = $PathValue.Trim()
  if ($value -match '^[\\]{2}\?\\UNC\\') {
    $value = "\" + $value.Substring(8)
  }
  elseif ($value -match '^[\\]{2}\?\\') {
    $value = $value.Substring(4)
  }
  return ($value -replace '/', '\')
}

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$doctorScript = Join-Path $scriptRoot "codex-session-path-doctor.ps1"
if (-not (Test-Path -LiteralPath $doctorScript)) {
  throw "Missing script: $doctorScript"
}

$doctorRaw = & powershell -NoProfile -ExecutionPolicy Bypass -File $doctorScript -Action audit
$doctor = $doctorRaw | ConvertFrom-Json

$withPrefix = [int]$doctor.totals.with_extended_prefix
$mismatch = [int]$doctor.totals.mismatch_db_vs_session_cwd
$missingRollout = [int]$doctor.totals.rollout_missing

$projectPrefixCount = 0
$projectThreadCount = 0
$projectNorm = ""

if (-not [string]::IsNullOrWhiteSpace($ProjectPath)) {
  $projectNorm = (Normalize-PathIdentity -PathValue $ProjectPath).ToLowerInvariant()
  $dbPath = [string]$doctor.state_db
  if (Test-Path -LiteralPath $dbPath) {
    $rows = & sqlite3 -separator "`t" $dbPath "SELECT id, rollout_path, cwd FROM threads;"
    if ($LASTEXITCODE -eq 0) {
      foreach ($line in $rows) {
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        $parts = $line -split "`t", 3
        if ($parts.Count -lt 3) { continue }
        $cwd = [string]$parts[2]
        $cwdNorm = (Normalize-PathIdentity -PathValue $cwd).ToLowerInvariant()
        if ($cwdNorm -eq $projectNorm) {
          $projectThreadCount++
          $rollout = [string]$parts[1]
          if ($cwd -match '^[\\]{2}\?\\' -or $rollout -match '^[\\]{2}\?\\') {
            $projectPrefixCount++
          }
        }
      }
    }
  }
}

$isProjectScope = -not [string]::IsNullOrWhiteSpace($ProjectPath)
if ($isProjectScope) {
  $isWarn = ($projectPrefixCount -gt 0 -or $mismatch -gt 0 -or $missingRollout -gt 0)
}
else {
  $isWarn = ($withPrefix -gt 0 -or $mismatch -gt 0 -or $missingRollout -gt 0)
}
$status = if ($isWarn) { "WARN" } else { "PASS" }

$result = [ordered]@{
  status = $status
  strict = [bool]$Strict
  totals = [ordered]@{
    threads = [int]$doctor.totals.threads
    with_extended_prefix = $withPrefix
    mismatch_db_vs_session_cwd = $mismatch
    rollout_missing = $missingRollout
  }
  project = [ordered]@{
    path = $ProjectPath
    threads = $projectThreadCount
    with_extended_prefix = $projectPrefixCount
  }
  recommendation = if ($isWarn) {
    "Run from an external PowerShell window: powershell -ExecutionPolicy Bypass -File .\\scripts\\fix-codex-thread-path-hard.ps1 -StopCodexProcesses"
  } else {
    if ($isProjectScope -and $withPrefix -gt 0) {
      "Project health check passed. Some prefixed paths remain in other projects; optional global hard-fix can clean them."
    }
    else {
      "Health check passed."
    }
  }
}

$result | ConvertTo-Json -Depth 10

if ($Strict -and $isWarn) {
  exit 2
}

exit 0

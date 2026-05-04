param(
  [ValidateSet("audit", "fix")]
  [string]$Action = "audit",

  [string]$CodexHome = "",

  [switch]$AllowWhileCodexRunning,

  [switch]$NormalizeGlobalState
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Resolve-CodexHome {
  param([string]$InputPath)
  if (-not [string]::IsNullOrWhiteSpace($InputPath)) {
    return $InputPath
  }
  return (Join-Path $env:USERPROFILE ".codex")
}

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
  $value = $value -replace '/', '\'
  return $value
}

function Is-ExtendedPath {
  param([string]$PathValue)
  if ([string]::IsNullOrWhiteSpace($PathValue)) {
    return $false
  }
  return ($PathValue -match '^[\\]{2}\?\\')
}

function Get-SessionMetaCwd {
  param([string]$RolloutPath)
  if ([string]::IsNullOrWhiteSpace($RolloutPath)) {
    return ""
  }
  $normalized = Normalize-PathIdentity -PathValue $RolloutPath
  if (-not (Test-Path -LiteralPath $normalized)) {
    return ""
  }

  $lines = @(Get-Content -LiteralPath $normalized -TotalCount 40)
  foreach ($line in $lines) {
    if ($line -like '*"type":"session_meta"*') {
      try {
        $obj = $line | ConvertFrom-Json
        if ($null -ne $obj.payload -and $obj.payload.PSObject.Properties.Name -contains "cwd") {
          return [string]$obj.payload.cwd
        }
      }
      catch {
        return ""
      }
    }
  }
  return ""
}

function Get-CanonicalExistingPath {
  param([string]$PathValue)
  if ([string]::IsNullOrWhiteSpace($PathValue)) {
    return ""
  }
  $normalized = Normalize-PathIdentity -PathValue $PathValue
  if (-not (Test-Path -LiteralPath $normalized)) {
    return ""
  }
  try {
    return (Resolve-Path -LiteralPath $normalized).Path
  }
  catch {
    return ""
  }
}

function Get-Threads {
  param([string]$StateDbPath)
  $rows = @()
  $raw = & sqlite3 -separator "`t" $StateDbPath "SELECT id, rollout_path, cwd FROM threads;"
  if ($LASTEXITCODE -ne 0) {
    throw "sqlite3 query failed for threads table."
  }

  foreach ($line in $raw) {
    if ([string]::IsNullOrWhiteSpace($line)) {
      continue
    }
    $parts = $line -split "`t", 3
    if ($parts.Count -lt 3) {
      continue
    }
    $rows += [pscustomobject]@{
      id = [string]$parts[0]
      rollout_path = [string]$parts[1]
      cwd = [string]$parts[2]
    }
  }
  return $rows
}

function Test-CodexRunning {
  $procs = @(Get-Process -ErrorAction SilentlyContinue | Where-Object { $_.ProcessName -match 'codex' })
  return ($procs.Count -gt 0)
}

function Backup-Files {
  param(
    [string]$CodexHomePath,
    [string[]]$PathsToBackup
  )
  $timestamp = (Get-Date).ToUniversalTime().ToString("yyyyMMddTHHmmssZ")
  $backupDir = Join-Path $CodexHomePath ("backups\thread-path-fix-" + $timestamp)
  New-Item -ItemType Directory -Path $backupDir -Force | Out-Null
  foreach ($path in $PathsToBackup) {
    if (Test-Path -LiteralPath $path) {
      $name = [System.IO.Path]::GetFileName($path)
      Copy-Item -LiteralPath $path -Destination (Join-Path $backupDir $name) -Force
    }
  }
  return $backupDir
}

function Normalize-GlobalStatePaths {
  param([string]$GlobalStatePath)
  if (-not (Test-Path -LiteralPath $GlobalStatePath)) {
    return $false
  }

  $raw = Get-Content -LiteralPath $GlobalStatePath -Raw
  if ([string]::IsNullOrWhiteSpace($raw)) {
    return $false
  }

  $data = $raw | ConvertFrom-Json
  $changed = $false

  foreach ($key in @("electron-saved-workspace-roots", "active-workspace-roots", "project-order")) {
    $prop = $data.PSObject.Properties[$key]
    if ($null -ne $prop -and $null -ne $prop.Value -and $prop.Value -is [System.Collections.IEnumerable]) {
      $oldValues = @($prop.Value)
      $newValues = @()
      foreach ($value in $oldValues) {
        $newValue = Normalize-PathIdentity -PathValue ([string]$value)
        $newValues += $newValue
        if ([string]$newValue -ne [string]$value) {
          $changed = $true
        }
      }
      $prop.Value = $newValues
    }
  }

  $hintsProp = $data.PSObject.Properties["thread-workspace-root-hints"]
  if ($null -ne $hintsProp -and $null -ne $hintsProp.Value) {
    $hints = $hintsProp.Value
    foreach ($hintEntry in @($hints.PSObject.Properties)) {
      $oldValue = [string]$hintEntry.Value
      $newValue = Normalize-PathIdentity -PathValue $oldValue
      if ($newValue -ne $oldValue) {
        $hintEntry.Value = $newValue
        $changed = $true
      }
    }
  }

  if ($changed) {
    $data | ConvertTo-Json -Depth 50 | Set-Content -LiteralPath $GlobalStatePath -Encoding UTF8
  }
  return $changed
}

if (-not (Get-Command sqlite3 -ErrorAction SilentlyContinue)) {
  throw "Missing required tool: sqlite3"
}

$codexHomePath = Resolve-CodexHome -InputPath $CodexHome
$stateDbPath = Join-Path $codexHomePath "state_5.sqlite"
$sessionIndexPath = Join-Path $codexHomePath "session_index.jsonl"
$globalStatePath = Join-Path $codexHomePath ".codex-global-state.json"

if (-not (Test-Path -LiteralPath $stateDbPath)) {
  throw "Missing state database: $stateDbPath"
}

$threads = Get-Threads -StateDbPath $stateDbPath
$analysisRows = @()
$prefixedCount = 0
$mismatchCount = 0
$missingRolloutCount = 0

foreach ($thread in $threads) {
  $sessionMetaCwd = Get-SessionMetaCwd -RolloutPath $thread.rollout_path
  $normalizedDbCwd = Normalize-PathIdentity -PathValue $thread.cwd
  $normalizedSessionCwd = Normalize-PathIdentity -PathValue $sessionMetaCwd
  $normalizedRollout = Normalize-PathIdentity -PathValue $thread.rollout_path
  $rolloutExists = Test-Path -LiteralPath $normalizedRollout
  $hasPrefix = (Is-ExtendedPath -PathValue $thread.rollout_path) -or (Is-ExtendedPath -PathValue $thread.cwd)

  if ($hasPrefix) {
    $prefixedCount++
  }
  if (-not $rolloutExists) {
    $missingRolloutCount++
  }
  $isMismatch = $false
  if (-not [string]::IsNullOrWhiteSpace($normalizedSessionCwd) -and $normalizedDbCwd -ne $normalizedSessionCwd) {
    $canonicalDbCwd = Get-CanonicalExistingPath -PathValue $normalizedDbCwd
    $canonicalSessionCwd = Get-CanonicalExistingPath -PathValue $normalizedSessionCwd
    if (-not [string]::IsNullOrWhiteSpace($canonicalDbCwd) -and -not [string]::IsNullOrWhiteSpace($canonicalSessionCwd)) {
      if ($canonicalDbCwd.ToLowerInvariant() -ne $canonicalSessionCwd.ToLowerInvariant()) {
        $isMismatch = $true
        $mismatchCount++
      }
    }
  }

  $analysisRows += [pscustomobject]@{
    id = $thread.id
    rollout_path = $thread.rollout_path
    rollout_path_normalized = $normalizedRollout
    rollout_exists = $rolloutExists
    cwd_db = $thread.cwd
    cwd_session_meta = $sessionMetaCwd
    mismatch_db_vs_session_cwd = $isMismatch
    has_extended_prefix = $hasPrefix
  }
}

$result = [ordered]@{
  action = $Action
  codex_home = $codexHomePath
  state_db = $stateDbPath
  totals = [ordered]@{
    threads = $threads.Count
    with_extended_prefix = $prefixedCount
    mismatch_db_vs_session_cwd = $mismatchCount
    rollout_missing = $missingRolloutCount
  }
  can_fix_safely_now = (-not (Test-CodexRunning))
  fixed = $false
  backup_dir = ""
  global_state_normalized = $false
  sample = @($analysisRows | Where-Object { $_.has_extended_prefix -or $_.mismatch_db_vs_session_cwd } | Select-Object -First 20)
}

if ($Action -eq "fix") {
  if ((Test-CodexRunning) -and (-not $AllowWhileCodexRunning)) {
    $result["error"] = "Codex process is running. Close Codex Desktop before fix, or pass -AllowWhileCodexRunning."
    $result | ConvertTo-Json -Depth 25
    exit 2
  }

  $backupDir = Backup-Files -CodexHomePath $codexHomePath -PathsToBackup @(
    $stateDbPath,
    (Join-Path $codexHomePath "state_5.sqlite-shm"),
    (Join-Path $codexHomePath "state_5.sqlite-wal"),
    $sessionIndexPath,
    $globalStatePath
  )
  $result["backup_dir"] = $backupDir

  $sql = @"
BEGIN IMMEDIATE;
UPDATE threads
SET rollout_path = substr(rollout_path, 5)
WHERE hex(substr(rollout_path, 1, 4)) = '5C5C3F5C';
UPDATE threads
SET cwd = substr(cwd, 5)
WHERE hex(substr(cwd, 1, 4)) = '5C5C3F5C';
COMMIT;
"@
  $null = & sqlite3 $stateDbPath $sql
  if ($LASTEXITCODE -ne 0) {
    throw "Failed to update state_5.sqlite"
  }

  if ($NormalizeGlobalState) {
    try {
      $normalized = Normalize-GlobalStatePaths -GlobalStatePath $globalStatePath
      $result["global_state_normalized"] = [bool]$normalized
    }
    catch {
      $result["global_state_normalized"] = $false
      $result["warning"] = "Global-state normalization failed: $($_.Exception.Message)"
    }
  }

  $result["fixed"] = $true
}

$result | ConvertTo-Json -Depth 25

param(
  [string]$ProjectPath = "F:\codex_android_gsheet_full_pack",
  [switch]$AutoFix,
  [switch]$RunAutopilot,
  [ValidateSet("safe", "balanced", "fast")]
  [string]$SafetyLevel = "safe"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

. "$PSScriptRoot/lib.ps1"

function Assert-File {
  param([string]$PathValue, [string]$Label)
  if (-not (Test-Path -LiteralPath $PathValue)) {
    throw "Missing ${Label}: $PathValue"
  }
}

$projectResolved = (Resolve-Path $ProjectPath -ErrorAction Stop).Path
$centralRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$healthScript = Join-Path $PSScriptRoot "global-health-check.ps1"
$fixScript = Join-Path $PSScriptRoot "fix-codex-thread-path-hard.ps1"
$autopilotScript = Join-Path $PSScriptRoot "Run-GsdAutopilot.ps1"

Assert-File -PathValue $healthScript -Label "health script"
Assert-File -PathValue $fixScript -Label "repair script"
Assert-File -PathValue $autopilotScript -Label "autopilot script"

Write-Step "GSD one-command runner"
Write-Step "Project=$projectResolved | AutoFix=$AutoFix | RunAutopilot=$RunAutopilot | SafetyLevel=$SafetyLevel"

$healthJson = & powershell -NoProfile -ExecutionPolicy Bypass -File $healthScript -ProjectPath $projectResolved -Json
$health = $healthJson | ConvertFrom-Json
$isSafe = [bool]$health.safe_to_run_gsd

if (-not $isSafe -and $AutoFix) {
  Write-Step "Environment is WARN. Running hard repair (this may stop Codex Desktop)."
  & powershell -NoProfile -ExecutionPolicy Bypass -File $fixScript -StopCodexProcesses
  if ($LASTEXITCODE -ne 0) {
    throw "Hard repair failed. Check script output and rerun."
  }

  $healthJson2 = & powershell -NoProfile -ExecutionPolicy Bypass -File $healthScript -ProjectPath $projectResolved -Json
  $health2 = $healthJson2 | ConvertFrom-Json
  $isSafe = [bool]$health2.safe_to_run_gsd
  if (-not $isSafe) {
    throw "Environment is still WARN after repair. Run doctor manually to inspect remaining issues."
  }
}

if (-not $isSafe) {
  Write-Step "Environment is not safe. Stop here."
  Write-Output $healthJson
  exit 2
}

Write-Step "Environment is PASS."

if ($RunAutopilot) {
  Write-Step "Running GSD Autopilot..."
  & powershell -NoProfile -ExecutionPolicy Bypass -File $autopilotScript -ProjectPath $projectResolved -SafetyLevel $SafetyLevel
  if ($LASTEXITCODE -ne 0) {
    throw "Autopilot failed. Check project report in out/."
  }
}

Write-Step "Done."

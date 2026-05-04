param(
  [string]$ProjectPath = "",
  [switch]$Strict,
  [switch]$Json
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

. "$PSScriptRoot/lib.ps1"

$healthScript = Join-Path $PSScriptRoot "gsd-health-check-quick.ps1"
if (-not (Test-Path -LiteralPath $healthScript)) {
  throw "Missing health script: $healthScript"
}

$argsList = @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $healthScript)
if (-not [string]::IsNullOrWhiteSpace($ProjectPath)) {
  $argsList += @("-ProjectPath", $ProjectPath)
}

$raw = & powershell @argsList
$health = $raw | ConvertFrom-Json

$checks = @()
$checks += [pscustomobject]@{
  name = "codex_session_path"
  status = [string]$health.status
  detail = $health
}

$overall = "PASS"
foreach ($check in $checks) {
  if ($check.status -ne "PASS") {
    $overall = "WARN"
  }
}

$result = [ordered]@{
  status = $overall
  strict = [bool]$Strict
  project_path = $ProjectPath
  checks = $checks
  safe_to_run_gsd = ($overall -eq "PASS")
  recommendation = if ($overall -eq "PASS") {
    "Global health passed. GSD may run."
  } else {
    "Do not run GSD yet. Close Codex Desktop and run: powershell -ExecutionPolicy Bypass -File `"$PSScriptRoot\fix-codex-thread-path-hard.ps1`" -StopCodexProcesses"
  }
}

if ($Json) {
  $result | ConvertTo-Json -Depth 25
} else {
  Write-Step "Global health: $overall"
  Write-Step "Codex session path: $($health.status)"
  if ($overall -ne "PASS") {
    Write-Step $result.recommendation
  }
  $result | ConvertTo-Json -Depth 25
}

if ($Strict -and $overall -ne "PASS") {
  exit 2
}

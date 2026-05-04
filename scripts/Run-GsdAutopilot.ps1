param(
  [Parameter(Mandatory = $true)]
  [string]$ProjectPath,

  [ValidateSet("auto", "full-gsd", "phase-gsd", "audit-only", "external-audit-only", "orchestrator", "governance-source")]
  [string]$Profile = "auto",

  [ValidateSet("safe", "balanced", "fast")]
  [string]$SafetyLevel = "safe",

  [switch]$DryRun,
  [switch]$Force
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

. "$PSScriptRoot/lib.ps1"

function Normalize-PathIdentity {
  param([string]$PathValue)
  if ([string]::IsNullOrWhiteSpace($PathValue)) { return "" }
  $value = $PathValue.Trim()
  if ($value -match '^[\\]{2}\?\\UNC\\') {
    $value = "\" + $value.Substring(8)
  } elseif ($value -match '^[\\]{2}\?\\') {
    $value = $value.Substring(4)
  }
  return ($value -replace '/', '\').TrimEnd('\').ToLowerInvariant()
}

$controlRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$registryPath = Join-Path $controlRoot "config\project-registry.json"
if (-not (Test-Path -LiteralPath $registryPath)) {
  throw "Missing project registry: $registryPath"
}

if (-not (Test-Path -LiteralPath $ProjectPath)) {
  throw "Project path does not exist: $ProjectPath"
}

$resolvedProjectPath = (Resolve-Path $ProjectPath).Path
$registry = Get-Content -LiteralPath $registryPath -Raw | ConvertFrom-Json
$projectNorm = Normalize-PathIdentity -PathValue $resolvedProjectPath
$projectEntry = $null

foreach ($entry in @($registry.projects)) {
  if ((Normalize-PathIdentity -PathValue ([string]$entry.path)) -eq $projectNorm) {
    $projectEntry = $entry
    break
  }
}

$selectedProfile = $Profile
if ($selectedProfile -eq "auto") {
  if ($null -ne $projectEntry) {
    $selectedProfile = [string]$projectEntry.profile
  } else {
    $selectedProfile = [string]$registry.default_profile
  }
}

$profilePath = Join-Path $controlRoot "config\project-profiles\$selectedProfile.json"
if (-not (Test-Path -LiteralPath $profilePath)) {
  throw "Missing project profile: $profilePath"
}
$profileConfig = Get-Content -LiteralPath $profilePath -Raw | ConvertFrom-Json

Write-Step "GSD Autopilot starting"
Write-Step "Project=$resolvedProjectPath"
Write-Step "Profile=$selectedProfile | SafetyLevel=$SafetyLevel | DryRun=$DryRun"

$healthScript = Join-Path $PSScriptRoot "global-health-check.ps1"
$healthJson = & powershell -NoProfile -ExecutionPolicy Bypass -File $healthScript -ProjectPath $resolvedProjectPath -Json
$health = $healthJson | ConvertFrom-Json
if (-not [bool]$health.safe_to_run_gsd) {
  $message = "Blocked at Level 0 health gate. $($health.recommendation)"
  if (-not $Force) {
    throw $message
  }
  Write-Step "Force enabled. Continuing despite health warning: $message"
}

$outDir = Join-Path $resolvedProjectPath "out"
Ensure-Dir -Path $outDir
$reportPath = Join-Path $outDir "gsd-autopilot-report.md"
$lines = @()
$lines += "# GSD Autopilot Report"
$lines += ""
$lines += "- Project: $resolvedProjectPath"
$lines += "- Profile: $selectedProfile"
$lines += "- SafetyLevel: $SafetyLevel"
$lines += "- Health: $($health.status)"
$lines += "- Timestamp: $((Get-Date).ToString("yyyy-MM-dd HH:mm:ss"))"
$lines += ""

if (-not [bool]$profileConfig.allow_install_gsd -or -not [bool]$profileConfig.allow_execute) {
  $lines += "## Result"
  $lines += ""
  $lines += "Autopilot stopped intentionally because this profile is `audit-only`/`external-audit-only`/governance-only."
  $lines += "No GSD install or code execution was performed."
  $lines += ""
  $lines += "## Next Safe Action"
  $lines += ""
  $lines += 'Ask AI for an external audit or explicitly switch this project to `phase-gsd` if you want controlled execution.'
  Set-Content -LiteralPath $reportPath -Value ($lines -join [Environment]::NewLine) -Encoding UTF8
  Write-Step "Report written: $reportPath"
  Write-Step "Stopped safely for profile: $selectedProfile"
  exit 0
}

$targetRunner = Join-Path $resolvedProjectPath "scripts\run-end-to-end.ps1"
if (-not (Test-Path -LiteralPath $targetRunner)) {
  throw "Target project does not have scripts\run-end-to-end.ps1. Bootstrap the project first from the central repo."
}

$localGsdTools = Join-Path $resolvedProjectPath ".codex\get-shit-done\bin\gsd-tools.cjs"
$localSdkCli = Join-Path $resolvedProjectPath "node_modules\@gsd-build\sdk\dist\cli.js"
$skipInstallGsd = Test-Path -LiteralPath $localGsdTools
$skipSdkInstall = Test-Path -LiteralPath $localSdkCli

if ($DryRun) {
  $lines += "## Dry Run"
  $lines += ""
  $lines += "Would run target GSD bootstrap:"
  $lines += ""
  $lines += '```powershell'
  $previewArgs = @("-Runtime codex", "-SafetyLevel $SafetyLevel")
  if ($skipInstallGsd) { $previewArgs += "-SkipInstallGsd" }
  if ($skipSdkInstall) { $previewArgs += "-SkipSdkInstall" }
  $lines += "powershell -ExecutionPolicy Bypass -File `"$targetRunner`" $($previewArgs -join ' ')"
  $lines += '```'
  Set-Content -LiteralPath $reportPath -Value ($lines -join [Environment]::NewLine) -Encoding UTF8
  Write-Step "Dry-run report written: $reportPath"
  exit 0
}

$runnerArgs = @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $targetRunner, "-Runtime", "codex", "-SafetyLevel", $SafetyLevel)
if ($skipInstallGsd) {
  Write-Step "Local GSD runtime already exists. Skipping GSD installer."
  $runnerArgs += "-SkipInstallGsd"
}
if ($skipSdkInstall) {
  Write-Step "Local @gsd-build/sdk already exists. Skipping SDK install."
  $runnerArgs += "-SkipSdkInstall"
}

& powershell @runnerArgs
$runnerExitCode = $LASTEXITCODE
if ($runnerExitCode -ne 0) {
  $lines += "## Result"
  $lines += ""
  $lines += "Target GSD runner failed with exit code $runnerExitCode."
  $lines += ""
  $lines += "See: $resolvedProjectPath\out\bootstrap-report.md"
  Set-Content -LiteralPath $reportPath -Value ($lines -join [Environment]::NewLine) -Encoding UTF8
  Write-Step "Report written: $reportPath"
  throw "Target GSD runner failed. Check target out/bootstrap-report.md."
}

$lines += "## Result"
$lines += ""
$lines += "Target GSD runner completed successfully."
Set-Content -LiteralPath $reportPath -Value ($lines -join [Environment]::NewLine) -Encoding UTF8
Write-Step "Report written: $reportPath"
Write-Step "GSD Autopilot completed"

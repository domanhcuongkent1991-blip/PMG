param(
  [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Assert-True {
  param(
    [bool]$Condition,
    [string]$Message
  )
  if (-not $Condition) {
    throw $Message
  }
}

function Assert-Equals {
  param(
    $Expected,
    $Actual,
    [string]$Message
  )
  if ($Expected -ne $Actual) {
    throw "$Message Expected=[$Expected] Actual=[$Actual]"
  }
}

function Invoke-JsonScript {
  param(
    [string]$ScriptPath,
    [string[]]$Arguments
  )

  $output = & powershell -NoProfile -ExecutionPolicy Bypass -File $ScriptPath @Arguments -Json
  if ($LASTEXITCODE -ne 0) {
    throw "Script failed: $ScriptPath`n$output"
  }
  return ($output | ConvertFrom-Json)
}

$startScript = Join-Path $ProjectRoot "scripts/start-safety-checkpoint.ps1"
$finishScript = Join-Path $ProjectRoot "scripts/finish-safety-checkpoint.ps1"
$listScript = Join-Path $ProjectRoot "scripts/list-safety-checkpoints.ps1"
$cleanCheckScript = Join-Path $ProjectRoot "scripts/check-gsd-artifacts.ps1"
$secretGuardScript = Join-Path $ProjectRoot "scripts/prevent-secrets.js"

$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("codex-safety-checkpoint-test-" + [guid]::NewGuid().ToString("N"))

try {
  New-Item -ItemType Directory -Path (Join-Path $tempRoot "scripts") -Force | Out-Null
  Copy-Item -LiteralPath $cleanCheckScript -Destination (Join-Path $tempRoot "scripts/check-gsd-artifacts.ps1") -Force
  Copy-Item -LiteralPath $secretGuardScript -Destination (Join-Path $tempRoot "scripts/prevent-secrets.js") -Force

  Push-Location $tempRoot
  try {
    & git init | Out-Null
    & git config user.email "workflow-test@example.invalid" | Out-Null
    & git config user.name "Workflow Test" | Out-Null
    & git config core.autocrlf false | Out-Null

    "baseline" | Set-Content -LiteralPath "README.md" -Encoding UTF8
    & git add . | Out-Null
    & git commit -m "baseline" | Out-Null
  } finally {
    Pop-Location
  }

  $start = Invoke-JsonScript -ScriptPath $startScript -Arguments @("-ProjectRoot", $tempRoot, "-Name", "Fix white screen")
  Assert-Equals "PASS" $start.status "task-start should pass."
  Assert-True ($start.tag_name -like "safety/pre-task/*fix-white-screen*") "task-start should create a descriptive safety tag."
  Assert-True (Test-Path -LiteralPath (Join-Path $tempRoot ".workflow-gate/current-safety-checkpoint.json")) "task-start should write checkpoint state."

  $tagExists = & git -C $tempRoot tag --list $start.tag_name
  Assert-Equals $start.tag_name $tagExists "safety tag should exist after start."

  $finishWithoutCommit = Invoke-JsonScript -ScriptPath $finishScript -Arguments @("-ProjectRoot", $tempRoot)
  Assert-Equals "HELD" $finishWithoutCommit.status "task-finish should hold checkpoint without a later official commit."
  $tagStillExists = & git -C $tempRoot tag --list $start.tag_name
  Assert-Equals $start.tag_name $tagStillExists "safety tag should remain without official commit."

  Push-Location $tempRoot
  try {
    "official change" | Set-Content -LiteralPath "README.md" -Encoding UTF8
    & git add README.md | Out-Null
    & git commit -m "test: official task commit" | Out-Null
  } finally {
    Pop-Location
  }

  $finishAfterCommit = Invoke-JsonScript -ScriptPath $finishScript -Arguments @("-ProjectRoot", $tempRoot)
  Assert-Equals "PASS" $finishAfterCommit.status "task-finish should pass and remove checkpoint after official commit."
  $tagAfterFinish = & git -C $tempRoot tag --list $start.tag_name
  Assert-True ([string]::IsNullOrWhiteSpace($tagAfterFinish)) "safety tag should be deleted after official commit."
  Assert-True (-not (Test-Path -LiteralPath (Join-Path $tempRoot ".workflow-gate/current-safety-checkpoint.json"))) "checkpoint state should be cleared after finish."

  $list = Invoke-JsonScript -ScriptPath $listScript -Arguments @("-ProjectRoot", $tempRoot)
  Assert-Equals "PASS" $list.status "checkpoint list should pass."
  Assert-Equals 0 $list.total_checkpoints "checkpoint list should be empty after cleanup."

  "PASS"
} finally {
  if (Test-Path -LiteralPath $tempRoot) {
    Remove-Item -LiteralPath $tempRoot -Recurse -Force
  }
}

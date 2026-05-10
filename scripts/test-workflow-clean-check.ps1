param(
  [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Assert-True {
  param(
    [Parameter(Mandatory = $true)][bool]$Condition,
    [Parameter(Mandatory = $true)][string]$Message
  )
  if (-not $Condition) {
    throw $Message
  }
}

function Assert-Equals {
  param(
    [Parameter(Mandatory = $true)]$Expected,
    [Parameter(Mandatory = $true)]$Actual,
    [Parameter(Mandatory = $true)][string]$Message
  )
  if ($Expected -ne $Actual) {
    throw "$Message Expected=[$Expected] Actual=[$Actual]"
  }
}

function Invoke-CleanCheck {
  param(
    [Parameter(Mandatory = $true)][string]$ScriptPath,
    [Parameter(Mandatory = $true)][string]$Root
  )
  $global:LASTEXITCODE = 0
  $output = & powershell -NoProfile -ExecutionPolicy Bypass -File $ScriptPath -ProjectRoot $Root -Json
  if ($LASTEXITCODE -ne 0) {
    throw "clean-check exited with $LASTEXITCODE`n$output"
  }
  return ($output | ConvertFrom-Json)
}

$scriptPath = Join-Path $ProjectRoot "scripts/check-gsd-artifacts.ps1"
$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("codex-gsd-clean-check-test-" + [guid]::NewGuid().ToString("N"))

try {
  New-Item -ItemType Directory -Path $tempRoot -Force | Out-Null
  Push-Location $tempRoot
  try {
    & git init | Out-Null
    & git config user.email "workflow-test@example.invalid" | Out-Null
    & git config user.name "Workflow Test" | Out-Null
    & git config core.autocrlf false | Out-Null

    @"
out/
.gsd-runtime/
docs/uat/evidence/**
!docs/uat/evidence/**/.gitkeep
"@ | Set-Content -LiteralPath ".gitignore" -Encoding UTF8
    "baseline" | Set-Content -LiteralPath "README.md" -Encoding UTF8
    & git add . | Out-Null
    & git commit -m "baseline" | Out-Null

    New-Item -ItemType Directory -Path ".planning/phases/demo" -Force | Out-Null
    "# Demo plan" | Set-Content -LiteralPath ".planning/phases/demo/DEMO-PLAN.md" -Encoding UTF8
    "# Demo summary" | Set-Content -LiteralPath ".planning/phases/demo/DEMO-SUMMARY.md" -Encoding UTF8

    New-Item -ItemType Directory -Path "android-mvp/app/src/main/java/example" -Force | Out-Null
    "class Demo" | Set-Content -LiteralPath "android-mvp/app/src/main/java/example/Demo.kt" -Encoding UTF8

    New-Item -ItemType Directory -Path "docs/uat/evidence/run-1" -Force | Out-Null
    "raw-db" | Set-Content -LiteralPath "docs/uat/evidence/run-1/device_tracker.db" -Encoding UTF8
    "raw-log" | Set-Content -LiteralPath "docs/uat/evidence/run-1/logcat.txt" -Encoding UTF8

    New-Item -ItemType Directory -Path "docs/uat/results" -Force | Out-Null
    "# UAT result" | Set-Content -LiteralPath "docs/uat/results/UAT_RESULT.md" -Encoding UTF8
  } finally {
    Pop-Location
  }

  $result = Invoke-CleanCheck -ScriptPath $scriptPath -Root $tempRoot

  Assert-Equals "FLAG" $result.status "clean-check should flag intentional dirty artifacts without failing."
  Assert-True ($result.counts.source_changes -eq 1) "clean-check should classify Android source separately."
  Assert-True ($result.counts.governance_artifacts -ge 2) "clean-check should classify phase artifacts."
  Assert-True ($result.counts.review_and_uat_reports -eq 1) "clean-check should classify UAT result report."
  Assert-True ($result.counts.raw_evidence -eq 0) "ignored raw evidence should not appear as dirty files."
  Assert-True (($result.recommendations | Out-String).Contains("Stage/review product source changes separately")) "clean-check should recommend separating product source."

  "PASS"
} finally {
  if (Test-Path -LiteralPath $tempRoot) {
    Remove-Item -LiteralPath $tempRoot -Recurse -Force
  }
}

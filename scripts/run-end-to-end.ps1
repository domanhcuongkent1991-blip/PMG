param(
  [ValidateSet("claude", "opencode", "gemini", "kilo", "codex", "copilot", "cursor", "windsurf", "antigravity", "augment", "trae", "qwen", "codebuddy", "cline", "all")]
  [string]$Runtime = "codex",

  [ValidateSet("safe", "balanced", "fast")]
  [string]$SafetyLevel = "safe",

  [switch]$DryRun,
  [switch]$SkipInstallGsd,
  [switch]$SkipSdkInstall,
  [switch]$SkipAutoRun,
  [switch]$SkipCodexPathAudit,
  [switch]$RepairCodexPathMismatch,
  [switch]$AllowUnsafeCodexPathState
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

. "$PSScriptRoot/lib.ps1"

$projectRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
Set-Location $projectRoot

Ensure-Dir "out"
Ensure-LocalNpmCache -ProjectRoot $projectRoot
$reportPath = Join-Path $projectRoot "out/bootstrap-report.md"
$stepResults = @()
$startTime = Get-Date

function Add-Result {
  param([hashtable]$Result)
  $script:stepResults += [pscustomobject]$Result
}

function Copy-IfMissing {
  param(
    [Parameter(Mandatory = $true)][string]$SourcePath,
    [Parameter(Mandatory = $true)][string]$DestinationPath
  )
  if (-not (Test-Path -LiteralPath $SourcePath)) {
    return $false
  }
  if (Test-Path -LiteralPath $DestinationPath) {
    return $true
  }
  $parent = Split-Path -Parent $DestinationPath
  if (-not (Test-Path -LiteralPath $parent)) {
    New-Item -ItemType Directory -Path $parent -Force | Out-Null
  }
  Copy-Item -LiteralPath $SourcePath -Destination $DestinationPath -Recurse -Force
  return $true
}

Write-Step "Starting GSD end-to-end bootstrap"
Write-Step "Runtime=$Runtime | SafetyLevel=$SafetyLevel | DryRun=$DryRun"

Add-Result (Invoke-StepWithRetry -StepName "Preflight: node exists" -DryRun:$DryRun -Action {
  Assert-CommandExists "node"
  node --version | Out-Host
})
Add-Result (Invoke-StepWithRetry -StepName "Preflight: npm exists" -DryRun:$DryRun -Action {
  Assert-CommandExists "npm"
  npm --version | Out-Host
})
Add-Result (Invoke-StepWithRetry -StepName "Preflight: npx exists" -DryRun:$DryRun -Action {
  Assert-CommandExists "npx"
  npx --version | Out-Host
})
Add-Result (Invoke-StepWithRetry -StepName "Preflight: git exists" -DryRun:$DryRun -Action {
  Assert-CommandExists "git"
  git --version | Out-Host
})

if (-not $SkipCodexPathAudit) {
  Add-Result (Invoke-StepWithRetry -StepName "Preflight: Codex thread path audit" -DryRun:$DryRun -MaxAttempts 1 -Action {
    $doctorScript = Join-Path $projectRoot "scripts/codex-session-path-doctor.ps1"
    if (-not (Test-Path -LiteralPath $doctorScript)) {
      throw "Missing doctor script: $doctorScript"
    }

    $auditJson = & powershell -NoProfile -ExecutionPolicy Bypass -File $doctorScript -Action audit
    $audit = $auditJson | ConvertFrom-Json
    if ($null -eq $audit -or $null -eq $audit.totals) {
      throw "Unable to read Codex path audit result."
    }

    $prefixed = [int]$audit.totals.with_extended_prefix
    $mismatch = [int]$audit.totals.mismatch_db_vs_session_cwd
    $missingRollout = [int]$audit.totals.rollout_missing
    Write-Step "Codex path audit: with_extended_prefix=$prefixed, mismatch_db_vs_session_cwd=$mismatch, rollout_missing=$missingRollout"

    if ($RepairCodexPathMismatch) {
      throw "Do not repair Codex state from inside an active Codex session. Run from an external PowerShell window: powershell -ExecutionPolicy Bypass -File `"$projectRoot\scripts\fix-codex-thread-path-hard.ps1`" -StopCodexProcesses"
    }

    if (-not $AllowUnsafeCodexPathState -and ($prefixed -gt 0 -or $mismatch -gt 0 -or $missingRollout -gt 0)) {
      throw "Unsafe Codex session path state detected. GSD is blocked to prevent the repeat resume-thread error. Run from an external PowerShell window: powershell -ExecutionPolicy Bypass -File `"$projectRoot\scripts\fix-codex-thread-path-hard.ps1`" -StopCodexProcesses"
    }
  })
}

if (-not $DryRun) {
  $failedPreflight = @($stepResults | Where-Object { $_.Status -eq "FAILED" })
  if ($failedPreflight.Count -gt 0) {
    $firstError = [string]$failedPreflight[0].Error
    throw "Preflight failed before GSD execution. First error: $firstError"
  }
}

Add-Result (Invoke-StepWithRetry -StepName "Validate input/prd.md exists" -DryRun:$DryRun -Action {
  if (-not (Test-Path -LiteralPath "input/prd.md")) {
    throw "Missing file: input/prd.md"
  }
  $prd = Get-Content -LiteralPath "input/prd.md" -Raw
  if ([string]::IsNullOrWhiteSpace($prd)) {
    throw "input/prd.md is empty"
  }
})

Add-Result (Invoke-StepWithRetry -StepName "Enable git pre-commit hooks (.githooks)" -DryRun:$DryRun -MaxAttempts 1 -Action {
  if (-not (Test-Path -LiteralPath ".git")) {
    Write-Step "No .git entry detected. Skipping hook setup."
    return
  }
  try {
    & cmd.exe /c "git -C ""$projectRoot"" rev-parse --is-inside-work-tree >nul 2>nul"
    if ($LASTEXITCODE -ne 0) {
      Write-Step "Git repository is not accessible in this shell context. Skipping hook setup."
      return
    }

    & cmd.exe /c "git -C ""$projectRoot"" config core.hooksPath "".githooks"" >nul 2>nul"
    if ($LASTEXITCODE -ne 0) {
      Write-Step "Cannot configure git hooksPath (non-fatal). Continuing without git hook setup."
      return
    }
  } catch {
    Write-Step "Git hook setup skipped (non-fatal): $($_.Exception.Message)"
    return
  }
})

if (-not $SkipInstallGsd) {
  $runtimeFlag = Get-GsdRuntimeFlag -Runtime $Runtime
  Add-Result (Invoke-StepWithRetry -StepName "Install GSD ($Runtime, local)" -DryRun:$DryRun -Action {
    & npx "get-shit-done-cc@latest" $runtimeFlag "--local"
  })
} else {
  Write-Step "SkipInstallGsd enabled, skipping GSD installer"
}

if (-not $SkipSdkInstall) {
  Add-Result (Invoke-StepWithRetry -StepName "Install SDK dependencies" -DryRun:$DryRun -Action {
    & npm install --no-audit --no-fund "@gsd-build/sdk@^0.1.0"
  })
} else {
  Write-Step "SkipSdkInstall enabled, skipping npm install"
}

Add-Result (Invoke-StepWithRetry -StepName "Prepare GSD SDK runtime compatibility (.claude bridge)" -DryRun:$DryRun -MaxAttempts 1 -Action {
  $homeDir = $env:USERPROFILE
  if ([string]::IsNullOrWhiteSpace($homeDir)) {
    throw "USERPROFILE is not set."
  }

  $claudeRoot = Join-Path $homeDir ".claude"
  $targetGsdRoot = Join-Path $claudeRoot "get-shit-done"
  $targetAgentsRoot = Join-Path $claudeRoot "agents"

  $sourceGsdRoot = Join-Path $projectRoot ".codex\get-shit-done"
  $sourceAgentsRoot = Join-Path $projectRoot ".codex\agents"

  try {
    $copiedGsd = Copy-IfMissing -SourcePath $sourceGsdRoot -DestinationPath $targetGsdRoot
    $copiedAgents = Copy-IfMissing -SourcePath $sourceAgentsRoot -DestinationPath $targetAgentsRoot
  } catch {
    throw "Cannot prepare .claude compatibility bridge under $claudeRoot. Run this command using the same Windows user account that runs Codex Desktop. Details: $($_.Exception.Message)"
  }

  $targetTools = Join-Path $targetGsdRoot "bin\gsd-tools.cjs"
  if (-not (Test-Path -LiteralPath $targetTools)) {
    throw "Missing gsd-tools runtime at $targetTools. Run GSD installer again and verify .codex/get-shit-done exists."
  }

  if ($copiedGsd -or $copiedAgents) {
    Write-Step "Prepared .claude compatibility bridge for @gsd-build/sdk."
  }
})

Add-Result (Invoke-StepWithRetry -StepName "Initialize project from input/prd.md" -DryRun:$DryRun -Action {
  & node ".\node_modules\@gsd-build\sdk\dist\cli.js" init "@input/prd.md" --project-dir "."
  if (-not (Test-Path -LiteralPath ".planning")) {
    throw "GSD init did not create .planning directory. Check runtime permissions and agent execution rights."
  }
})

Add-Result (Invoke-StepWithRetry -StepName "Apply safety config to .planning/config.json" -DryRun:$DryRun -Action {
  if (-not (Test-Path -LiteralPath ".planning/config.json")) {
    Write-Step "No .planning/config.json yet. Skipping safety-config patch for now."
    return
  }
  Set-GsdSafetyConfig -SafetyLevel $SafetyLevel -ConfigPath ".planning/config.json"
})

if (-not $SkipAutoRun) {
  Add-Result (Invoke-StepWithRetry -StepName "Run auto pipeline" -DryRun:$DryRun -Action {
    & node ".\node_modules\@gsd-build\sdk\dist\cli.js" auto --project-dir "."
    if (-not (Test-Path -LiteralPath ".planning")) {
      throw "Auto pipeline finished without .planning artifacts. Treat this run as failed."
    }
  })
} else {
  Write-Step "SkipAutoRun enabled, skipping auto pipeline"
}

$endTime = Get-Date
$duration = [Math]::Round(($endTime - $startTime).TotalSeconds, 2)

$ok = @($stepResults | Where-Object { $_.Status -eq "FAILED" }).Count -eq 0
$statusText = if ($ok) { "SUCCESS" } else { "FAILED" }

$reportLines = @()
$reportLines += "# GSD Bootstrap Report"
$reportLines += ""
$reportLines += "- Timestamp: $($endTime.ToString("yyyy-MM-dd HH:mm:ss"))"
$reportLines += "- Runtime: $Runtime"
$reportLines += "- SafetyLevel: $SafetyLevel"
$reportLines += "- DryRun: $DryRun"
$reportLines += "- Status: $statusText"
$reportLines += "- DurationSeconds: $duration"
$reportLines += ""
$reportLines += "## Step Results"
$reportLines += ""
$reportLines += "| Step | Status | Attempts | Error |"
$reportLines += "|---|---|---:|---|"
foreach ($r in $stepResults) {
  $errText = ($r.Error -replace "\|", "/")
  $reportLines += "| $($r.Step) | $($r.Status) | $($r.Attempts) | $errText |"
}

if (-not $ok) {
  $reportLines += ""
  $reportLines += "## Suggested Recovery"
  $reportLines += ""
  $reportLines += "1. Confirm Codex CLI can spawn child processes in your current shell."
  $reportLines += "2. Run init manually and inspect full error output:"
  $reportLines += '   node .\node_modules\@gsd-build\sdk\dist\cli.js init "@input/prd.md" --project-dir "."'
  $reportLines += '3. If error contains `spawn EPERM`, run from an elevated shell or a terminal session without process restrictions.'
  $reportLines += '4. Fallback in Codex chat: run `$gsd-new-project` then `$gsd-next`.'
}

Set-Content -LiteralPath $reportPath -Value ($reportLines -join [Environment]::NewLine) -Encoding UTF8
Write-Step "Report written: $reportPath"

if (-not $ok) {
  Write-Step "Bootstrap finished with failures. Check out/bootstrap-report.md"
  exit 1
}

Write-Step "Bootstrap finished successfully"

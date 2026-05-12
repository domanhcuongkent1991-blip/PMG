param(
  [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path,
  [switch]$Json
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Invoke-RequiredCommand {
  param(
    [string]$Step,
    [scriptblock]$Action
  )
  $global:LASTEXITCODE = 0
  $output = @(& $Action 2>&1)
  $exitCode = [int]$global:LASTEXITCODE
  if ($exitCode -ne 0) {
    throw "$Step failed with exit code $exitCode. $($output -join [Environment]::NewLine)"
  }
  return @($output | ForEach-Object { [string]$_ })
}

function Invoke-OptionalWorkflowCheck {
  param([string]$Root)
  $packagePath = Join-Path $Root "package.json"
  if (-not (Test-Path -LiteralPath $packagePath)) {
    return "SKIPPED: package.json not found"
  }
  $package = Get-Content -LiteralPath $packagePath -Raw | ConvertFrom-Json
  if ($null -eq $package.scripts -or $null -eq $package.scripts.PSObject.Properties["workflow:check"]) {
    return "SKIPPED: workflow:check script not found"
  }
  Invoke-RequiredCommand -Step "workflow check" -Action { & npm run workflow:check } | Out-Null
  return "PASS"
}

$root = (Resolve-Path -LiteralPath $ProjectRoot).Path
$statePath = Join-Path (Join-Path $root ".workflow-gate") "current-safety-checkpoint.json"

if (-not (Test-Path -LiteralPath $statePath)) {
  throw "No active safety checkpoint found: $statePath"
}

$state = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
$tagName = [string]$state.tag_name

Push-Location $root
try {
  & git rev-parse --verify "refs/tags/$tagName" 2>$null | Out-Null
  if ($LASTEXITCODE -ne 0) {
    throw "Safety tag does not exist: $tagName"
  }

  if (Test-Path -LiteralPath "scripts/prevent-secrets.js") {
    Invoke-RequiredCommand -Step "secret guard" -Action { & node "scripts/prevent-secrets.js" } | Out-Null
  }

  $cleanCheckStatus = "SKIPPED"
  if (Test-Path -LiteralPath "scripts/check-gsd-artifacts.ps1") {
    $cleanOutput = Invoke-RequiredCommand -Step "workflow clean-check" -Action {
      & powershell -NoProfile -ExecutionPolicy Bypass -File ".\scripts\check-gsd-artifacts.ps1" -ProjectRoot $root -Json
    }
    $clean = (($cleanOutput -join [Environment]::NewLine) | ConvertFrom-Json)
    $cleanCheckStatus = [string]$clean.status
    if ([int]$clean.counts.raw_evidence -gt 0) {
      throw "Raw runtime evidence is visible in git status. Checkpoint will not be closed."
    }
  }

  $workflowCheckStatus = Invoke-OptionalWorkflowCheck -Root $root
  $commitsAfterTag = [int]((& git rev-list "$tagName..HEAD" --count).Trim())
  $stateRelativePath = ".workflow-gate/current-safety-checkpoint.json"
  $dirtyFiles = @(& git status --porcelain=v1 --untracked-files=all | Where-Object {
    if ([string]::IsNullOrWhiteSpace($_) -or $_.Length -lt 4) {
      return $false
    }
    $path = $_.Substring(3).Replace("\", "/")
    return $path -ne $stateRelativePath
  })

  if ($commitsAfterTag -le 0) {
    $result = [ordered]@{
      status = "HELD"
      reason = "No official commit exists after the safety checkpoint."
      tag_name = $tagName
      commits_after_tag = $commitsAfterTag
      dirty_file_count = $dirtyFiles.Count
      clean_check = $cleanCheckStatus
      workflow_check = $workflowCheckStatus
    }
  } elseif ($dirtyFiles.Count -gt 0) {
    $result = [ordered]@{
      status = "HELD"
      reason = "Repo still has dirty files after the official commit."
      tag_name = $tagName
      commits_after_tag = $commitsAfterTag
      dirty_file_count = $dirtyFiles.Count
      clean_check = $cleanCheckStatus
      workflow_check = $workflowCheckStatus
    }
  } else {
    & git tag -d $tagName | Out-Null
    if ($LASTEXITCODE -ne 0) {
      throw "Failed to delete safety tag: $tagName"
    }
    Remove-Item -LiteralPath $statePath -Force
    $result = [ordered]@{
      status = "PASS"
      reason = "Official commit exists and repo is clean; safety checkpoint removed."
      tag_name = $tagName
      commits_after_tag = $commitsAfterTag
      dirty_file_count = 0
      clean_check = $cleanCheckStatus
      workflow_check = $workflowCheckStatus
    }
  }
} finally {
  Pop-Location
}

if ($Json) {
  $result | ConvertTo-Json -Depth 10
} else {
  Write-Host "$($result.status): $($result.reason)"
}

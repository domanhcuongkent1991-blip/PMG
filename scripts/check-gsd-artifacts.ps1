param(
  [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path,
  [switch]$Json
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Normalize-GitPath {
  param([Parameter(Mandatory = $true)][string]$Path)
  return $Path.Replace("\", "/")
}

function New-Bucket {
  return ,([System.Collections.Generic.List[string]]::new())
}

function Add-Path {
  param(
    [System.Collections.Generic.List[string]]$Bucket,
    [Parameter(Mandatory = $true)][string]$Path
  )
  $Bucket.Add($Path)
}

function Get-PorcelainPath {
  param([Parameter(Mandatory = $true)][string]$Line)
  if ($Line.Length -lt 4) {
    return ""
  }
  $path = $Line.Substring(3)
  if ($path -match " -> ") {
    $parts = $path -split " -> ", 2
    return (Normalize-GitPath -Path $parts[1])
  }
  return (Normalize-GitPath -Path $path)
}

function Test-SourcePath {
  param([Parameter(Mandatory = $true)][string]$Path)
  if ($Path.StartsWith("android-mvp/")) { return $true }
  if ($Path -match '(^|/)(build\.gradle|settings\.gradle|gradle\.properties|gradlew|gradlew\.bat)$') { return $true }
  if ($Path.StartsWith("scripts/") -and $Path -notmatch '^scripts/(check|approve|validate|test-workflow|verify-).*\.(ps1|js)$') { return $true }
  return $false
}

function Test-GovernanceArtifactPath {
  param([Parameter(Mandatory = $true)][string]$Path)
  if ($Path.StartsWith(".planning/")) { return $true }
  if ($Path.StartsWith(".workflow-gate/")) { return $true }
  if ($Path.StartsWith("governance/")) { return $true }
  if ($Path.StartsWith("planning/")) { return $true }
  if ($Path -eq "SKILL_SELECTION_REPORT.md") { return $true }
  if ($Path -match '^WORKLOG_\d{4}-\d{2}-\d{2}\.md$') { return $true }
  if ($Path -eq "AGENTS.md") { return $true }
  return $false
}

function Test-ReviewOrUatReportPath {
  param([Parameter(Mandatory = $true)][string]$Path)
  if ($Path.StartsWith("docs/review/")) { return $true }
  if ($Path.StartsWith("docs/audit/")) { return $true }
  if ($Path.StartsWith("docs/uat/results/")) { return $true }
  if ($Path.StartsWith("docs/uat/") -and $Path.EndsWith(".md")) { return $true }
  if ($Path -match '^AUDIT.*\.md$') { return $true }
  return $false
}

function Test-RawEvidencePath {
  param([Parameter(Mandatory = $true)][string]$Path)
  if ($Path.StartsWith("docs/uat/evidence/")) { return $true }
  if ($Path -match '(^|/)logcat.*\.(txt|log)$') { return $true }
  if ($Path -match '\.(db|db-shm|db-wal|sqlite|png|jpg|jpeg|zip|xml|log)$') { return $true }
  return $false
}

function Test-WorkflowScriptPath {
  param([Parameter(Mandatory = $true)][string]$Path)
  if ($Path -match '^scripts/(check|approve|validate|test-workflow|verify-).*\.(ps1|js)$') { return $true }
  if ($Path -eq "package.json") { return $true }
  if ($Path -eq ".gitignore") { return $true }
  return $false
}

$root = (Resolve-Path -LiteralPath $ProjectRoot).Path
Push-Location $root
try {
  $inside = (& git rev-parse --is-inside-work-tree 2>$null)
  if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($inside)) {
    throw "ProjectRoot is not a git worktree: $root"
  }

  $lines = @(& git status --porcelain=v1 --untracked-files=all)
} finally {
  Pop-Location
}

$buckets = [ordered]@{
  source_changes = New-Bucket
  governance_artifacts = New-Bucket
  review_and_uat_reports = New-Bucket
  raw_evidence = New-Bucket
  workflow_scripts = New-Bucket
  other_dirty = New-Bucket
}

foreach ($line in $lines) {
  if ([string]::IsNullOrWhiteSpace($line)) {
    continue
  }
  $path = Get-PorcelainPath -Line $line
  if ([string]::IsNullOrWhiteSpace($path)) {
    continue
  }

  if (Test-SourcePath -Path $path) {
    Add-Path -Bucket $buckets["source_changes"] -Path $path
  } elseif (Test-RawEvidencePath -Path $path) {
    Add-Path -Bucket $buckets["raw_evidence"] -Path $path
  } elseif (Test-GovernanceArtifactPath -Path $path) {
    Add-Path -Bucket $buckets["governance_artifacts"] -Path $path
  } elseif (Test-ReviewOrUatReportPath -Path $path) {
    Add-Path -Bucket $buckets["review_and_uat_reports"] -Path $path
  } elseif (Test-WorkflowScriptPath -Path $path) {
    Add-Path -Bucket $buckets["workflow_scripts"] -Path $path
  } else {
    Add-Path -Bucket $buckets["other_dirty"] -Path $path
  }
}

$counts = [ordered]@{}
foreach ($name in $buckets.Keys) {
  $counts[$name] = $buckets[$name].Count
}

$recommendations = [System.Collections.Generic.List[string]]::new()
if ($counts.raw_evidence -gt 0) {
  $recommendations.Add("Move raw UAT/runtime evidence to an ignored location or extend .gitignore for those patterns.")
}
if ($counts.source_changes -gt 0) {
  $recommendations.Add("Stage/review product source changes separately from GSD governance artifacts.")
}
if ($counts.governance_artifacts -gt 0) {
  $recommendations.Add("Commit approved governance artifacts or archive drafts after each GSD phase.")
}
if ($counts.review_and_uat_reports -gt 0) {
  $recommendations.Add("Keep concise review/UAT reports; keep heavy raw evidence out of Git.")
}
if ($counts.workflow_scripts -gt 0) {
  $recommendations.Add("Review workflow script changes as tooling changes before committing product code.")
}
if ($counts.other_dirty -gt 0) {
  $recommendations.Add("Inspect unclassified dirty files and decide whether to track, archive, or ignore them.")
}
if ($recommendations.Count -eq 0) {
  $recommendations.Add("Repo is clean under the GSD artifact policy.")
}

$status = "CLEAN"
if ($counts.raw_evidence -gt 0) {
  $status = "FAIL"
} elseif ($lines.Count -gt 0) {
  $status = "FLAG"
}

$result = [ordered]@{
  status = $status
  project_root = $root
  counts = $counts
  buckets = $buckets
  recommendations = @($recommendations)
}

if ($Json) {
  $result | ConvertTo-Json -Depth 10
} else {
  $result | ConvertTo-Json -Depth 10
}

if ($status -eq "FAIL") {
  exit 2
}

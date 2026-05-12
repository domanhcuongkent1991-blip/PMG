param(
  [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path,
  [Parameter(Mandatory = $true)][string]$Name,
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

function Convert-ToSlug {
  param([string]$Value)
  $slug = $Value.ToLowerInvariant() -replace '[^a-z0-9]+', '-'
  $slug = $slug.Trim("-")
  if ([string]::IsNullOrWhiteSpace($slug)) {
    return "task"
  }
  if ($slug.Length -gt 48) {
    return $slug.Substring(0, 48).Trim("-")
  }
  return $slug
}

function Read-CleanCheck {
  param([string]$Root)
  $script = Join-Path $Root "scripts/check-gsd-artifacts.ps1"
  if (-not (Test-Path -LiteralPath $script)) {
    throw "Missing workflow clean-check script: $script"
  }
  $output = Invoke-RequiredCommand -Step "workflow clean-check" -Action {
    & powershell -NoProfile -ExecutionPolicy Bypass -File $script -ProjectRoot $Root -Json
  }
  return (($output -join [Environment]::NewLine) | ConvertFrom-Json)
}

$root = (Resolve-Path -LiteralPath $ProjectRoot).Path
$gateDir = Join-Path $root ".workflow-gate"
$statePath = Join-Path $gateDir "current-safety-checkpoint.json"

Push-Location $root
try {
  $inside = (& git rev-parse --is-inside-work-tree 2>$null)
  if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($inside)) {
    throw "ProjectRoot is not a git worktree: $root"
  }

  if (Test-Path -LiteralPath $statePath) {
    throw "An active safety checkpoint already exists: $statePath"
  }

  if (Test-Path -LiteralPath "scripts/prevent-secrets.js") {
    Invoke-RequiredCommand -Step "secret guard" -Action { & node "scripts/prevent-secrets.js" } | Out-Null
  }

  $clean = Read-CleanCheck -Root $root
  if ([int]$clean.counts.raw_evidence -gt 0) {
    throw "Raw runtime evidence is visible in git status. Move/archive it before opening a checkpoint."
  }

  $branch = (& git branch --show-current).Trim()
  $head = (& git rev-parse HEAD).Trim()
  $timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
  $slug = Convert-ToSlug -Value $Name
  $tagName = "safety/pre-task/$timestamp-$slug"

  & git tag $tagName $head
  if ($LASTEXITCODE -ne 0) {
    throw "Failed to create safety tag: $tagName"
  }

  if (-not (Test-Path -LiteralPath $gateDir)) {
    New-Item -ItemType Directory -Path $gateDir -Force | Out-Null
  }

  $dirtySummary = [ordered]@{}
  foreach ($property in $clean.counts.PSObject.Properties) {
    $dirtySummary[$property.Name] = $property.Value
  }

  $state = [ordered]@{
    schema_version = "1.0"
    task_name = $Name
    tag_name = $tagName
    branch = $branch
    head_sha = $head
    started_at_utc = [DateTime]::UtcNow.ToString("o")
    dirty_summary = $dirtySummary
  }
  $state | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $statePath -Encoding UTF8

  $result = [ordered]@{
    status = "PASS"
    task_name = $Name
    tag_name = $tagName
    branch = $branch
    head_sha = $head
    state_path = $statePath
    clean_status = $clean.status
  }
} finally {
  Pop-Location
}

if ($Json) {
  $result | ConvertTo-Json -Depth 10
} else {
  Write-Host "Safety checkpoint created: $($result.tag_name)"
}

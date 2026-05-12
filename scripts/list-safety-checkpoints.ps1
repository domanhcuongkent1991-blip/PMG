param(
  [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path,
  [int]$StaleAfterDays = 7,
  [switch]$Json
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$root = (Resolve-Path -LiteralPath $ProjectRoot).Path
$statePath = Join-Path (Join-Path $root ".workflow-gate") "current-safety-checkpoint.json"

Push-Location $root
try {
  $tags = @(& git tag --list "safety/*" --format "%(refname:short)|%(creatordate:iso-strict)|%(objectname:short)")
  $items = @()
  $now = Get-Date
  foreach ($tag in $tags) {
    if ([string]::IsNullOrWhiteSpace($tag)) {
      continue
    }
    $parts = $tag -split "\|", 3
    $created = $null
    if ($parts.Count -ge 2 -and -not [string]::IsNullOrWhiteSpace($parts[1])) {
      $created = [DateTimeOffset]::Parse($parts[1]).DateTime
    }
    $ageDays = if ($null -ne $created) { [Math]::Round(($now - $created).TotalDays, 2) } else { $null }
    $items += [ordered]@{
      tag_name = $parts[0]
      created_at = if ($parts.Count -ge 2) { $parts[1] } else { "" }
      commit = if ($parts.Count -ge 3) { $parts[2] } else { "" }
      stale = ($null -ne $ageDays -and $ageDays -gt $StaleAfterDays)
      age_days = $ageDays
    }
  }
} finally {
  Pop-Location
}

$active = $null
if (Test-Path -LiteralPath $statePath) {
  $active = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
}

$result = [ordered]@{
  status = "PASS"
  total_checkpoints = $items.Count
  active_checkpoint = $active
  checkpoints = $items
}

if ($Json) {
  $result | ConvertTo-Json -Depth 10
} else {
  $result | ConvertTo-Json -Depth 10
}

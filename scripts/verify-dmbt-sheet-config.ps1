param(
  [string]$LocalPropertiesPath = "android-mvp/local.properties"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $LocalPropertiesPath)) {
  throw "Missing local properties file: $LocalPropertiesPath"
}

$requiredYearlySheetIds = @(
  849979183,
  1783863163,
  1224276666,
  989601207,
  1607125070
)

$monthlyLegacySheetIds = @(
  1383308512
)

$lines = Get-Content -LiteralPath $LocalPropertiesPath

function Read-PropertyValue {
  param(
    [string[]]$AllLines,
    [string]$PropertyKey
  )

  $line = $AllLines | Where-Object { $_ -match "^\s*$([Regex]::Escape($PropertyKey))\s*=" } | Select-Object -First 1
  if ($null -eq $line) {
    return ""
  }

  $parts = $line -split "=", 2
  if ($parts.Count -lt 2) {
    return ""
  }
  return $parts[1].Trim()
}

function Parse-IdList {
  param([string]$Raw)

  if ([string]::IsNullOrWhiteSpace($Raw)) {
    return @()
  }

  return @($Raw.Split(@(',', ';', ' ', "`t", "`r", "`n"), [System.StringSplitOptions]::RemoveEmptyEntries) |
    ForEach-Object { $_.Trim() } |
    Where-Object { $_ -match '^\d+$' } |
    ForEach-Object { [int]$_ } |
    Select-Object -Unique)
}

$dmbtPrimary = Read-PropertyValue -AllLines $lines -PropertyKey "SHEETS_DMBT_LOG_SHEET_ID"
$dmbtSheetIdsRaw = Read-PropertyValue -AllLines $lines -PropertyKey "SHEETS_DMBT_SHEET_IDS"
$dmbtReadOnlyRaw = Read-PropertyValue -AllLines $lines -PropertyKey "SHEETS_DMBT_READONLY_SHEET_IDS"
$dmbtDefaultCreate = Read-PropertyValue -AllLines $lines -PropertyKey "SHEETS_DMBT_DEFAULT_CREATE_SHEET_ID"

$configuredSheetIds = @(Parse-IdList -Raw $dmbtSheetIdsRaw)
$configuredReadOnlyIds = @(Parse-IdList -Raw $dmbtReadOnlyRaw)

Write-Host "DMBT primary sheet id: $dmbtPrimary"
Write-Host "DMBT sheet ids raw: '$dmbtSheetIdsRaw'"
Write-Host "DMBT sheet ids parsed: $($configuredSheetIds -join ',')"
Write-Host "DMBT readonly ids parsed: $($configuredReadOnlyIds -join ',')"
Write-Host "DMBT default create sheet id: '$dmbtDefaultCreate'"

if ($configuredSheetIds.Length -eq 0) {
  throw "SHEETS_DMBT_SHEET_IDS is empty. App will fallback to SHEETS_DMBT_LOG_SHEET_ID only."
}

$missingYearly = $requiredYearlySheetIds | Where-Object { $configuredSheetIds -notcontains $_ }
if (@($missingYearly).Length -gt 0) {
  throw "Missing required yearly DMBT sheet ids: $($missingYearly -join ',')"
}

$monthlyStillIncluded = $configuredSheetIds | Where-Object { $monthlyLegacySheetIds -contains $_ }
if (@($monthlyStillIncluded).Length -gt 0) {
  Write-Host "WARN: Monthly legacy DMBT sheet id still present in SHEETS_DMBT_SHEET_IDS: $($monthlyStillIncluded -join ',')" -ForegroundColor Yellow
}

if ([string]::IsNullOrWhiteSpace($dmbtDefaultCreate)) {
  Write-Host "WARN: SHEETS_DMBT_DEFAULT_CREATE_SHEET_ID is empty. Default create target will fallback automatically." -ForegroundColor Yellow
}

Write-Host "PASS: DMBT sheet config contains required yearly sheet ids."

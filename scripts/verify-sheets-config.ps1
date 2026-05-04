param(
  [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path,
  [switch]$Offline,
  [switch]$Json
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Read-PropertiesFile {
  param([Parameter(Mandatory = $true)][string]$Path)
  $props = @{}
  if (-not (Test-Path -LiteralPath $Path)) {
    throw "Missing local.properties: $Path"
  }
  Get-Content -LiteralPath $Path | ForEach-Object {
    $line = $_.Trim()
    if ($line -eq "" -or $line.StartsWith("#")) { return }
    $idx = $line.IndexOf("=")
    if ($idx -le 0) { return }
    $key = $line.Substring(0, $idx).Trim()
    $value = $line.Substring($idx + 1).Trim()
    $props[$key] = $value
  }
  return $props
}

function Has-Value {
  param($Map, [string]$Key)
  return $Map.ContainsKey($Key) -and -not [string]::IsNullOrWhiteSpace([string]$Map[$Key])
}

function Parse-SheetIdList {
  param([string]$RawValue, [string]$PrimarySheetId)
  if ([string]::IsNullOrWhiteSpace($RawValue)) {
    return @()
  }

  $primary = $null
  if (-not [string]::IsNullOrWhiteSpace($PrimarySheetId)) {
    $parsedPrimary = 0
    if ([int]::TryParse($PrimarySheetId.Trim(), [ref]$parsedPrimary) -and $parsedPrimary -ge 0) {
      $primary = $parsedPrimary
    }
  }

  $ids = [System.Collections.Generic.List[int]]::new()
  $RawValue -split '[,;\s]+' | ForEach-Object {
    $part = $_.Trim()
    if ($part -eq "") { return }
    $parsed = 0
    if ([int]::TryParse($part, [ref]$parsed) -and $parsed -ge 0 -and $parsed -ne $primary -and -not $ids.Contains($parsed)) {
      $ids.Add($parsed)
    }
  }
  return @($ids)
}

$localPropertiesPath = Join-Path $ProjectRoot "android-mvp/local.properties"
$props = Read-PropertiesFile -Path $localPropertiesPath

$requiredBase = @(
  "SHEETS_SPREADSHEET_ID",
  "SHEETS_DMBT_LOG_SHEET_ID",
  "SHEETS_HGT_CHECKS_SHEET_ID"
)
$missing = @($requiredBase | Where-Object { -not (Has-Value $props $_) })

$hasRefreshAuth = (Has-Value $props "SHEETS_OAUTH_CLIENT_ID") -and (Has-Value $props "SHEETS_REFRESH_TOKEN")
$hasAccessToken = Has-Value $props "SHEETS_ACCESS_TOKEN"
$readOnlyDmbtSheetIds = @(Parse-SheetIdList `
  -RawValue ([string]($props["SHEETS_DMBT_READONLY_SHEET_IDS"])) `
  -PrimarySheetId ([string]($props["SHEETS_DMBT_LOG_SHEET_ID"])))
$authMode = if ($hasRefreshAuth) {
  "REFRESH_TOKEN"
} elseif ($hasAccessToken) {
  "ACCESS_TOKEN"
} else {
  "MISSING"
}

if ($authMode -eq "MISSING") {
  $missing += "SHEETS_REFRESH_TOKEN or SHEETS_ACCESS_TOKEN"
}

$checks = @(
  [pscustomobject]@{ name = "spreadsheet_id"; ok = Has-Value $props "SHEETS_SPREADSHEET_ID" },
  [pscustomobject]@{ name = "dmbt_sheet_id"; ok = Has-Value $props "SHEETS_DMBT_LOG_SHEET_ID" },
  [pscustomobject]@{ name = "hgt_sheet_id"; ok = Has-Value $props "SHEETS_HGT_CHECKS_SHEET_ID" },
  [pscustomobject]@{ name = "refresh_auth"; ok = $hasRefreshAuth },
  [pscustomobject]@{ name = "access_token_fallback"; ok = $hasAccessToken },
  [pscustomobject]@{ name = "dmbt_readonly_sheet_ids_configured"; ok = ($readOnlyDmbtSheetIds.Count -gt 0) }
)

$status = if ($missing.Count -eq 0) { "PASS" } else { "FAIL" }
$online = $null

if (-not $Offline -and $status -eq "PASS") {
  $online = [ordered]@{
    attempted = $true
    note = "Online verification is intentionally handled by the app/API smoke path to avoid printing secrets in this script."
  }
} else {
  $online = [ordered]@{
    attempted = $false
    note = "Offline mode only validates presence and safe auth mode."
  }
}

$result = [ordered]@{
  status = $status
  auth_mode = $authMode
  access_token_embedded_required = ($authMode -eq "ACCESS_TOKEN")
  missing = $missing
  dmbt_readonly_sheet_id_count = $readOnlyDmbtSheetIds.Count
  checks = $checks
  online = $online
}

if ($Json) {
  $result | ConvertTo-Json -Depth 8
} else {
  Write-Host "Google Sheets config status: $status"
  Write-Host "Auth mode: $authMode"
  Write-Host "DMBT read-only sheet IDs: $($readOnlyDmbtSheetIds.Count)"
  if ($missing.Count -gt 0) {
    Write-Host "Missing: $($missing -join ', ')"
  }
}

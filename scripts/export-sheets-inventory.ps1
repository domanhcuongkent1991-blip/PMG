param(
  [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path,
  [switch]$Offline,
  [switch]$Json,
  [string]$OutputPath = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

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

function Get-PropValue {
  param($Map, [string]$Key)
  if (Has-Value $Map $Key) { return [string]$Map[$Key] }
  return ""
}

function Invoke-JsonGet {
  param(
    [Parameter(Mandatory = $true)][string]$Uri,
    [Parameter(Mandatory = $true)][string]$AccessToken
  )
  $headers = @{ Authorization = "Bearer $AccessToken" }
  return Invoke-RestMethod -Method Get -Uri $Uri -Headers $headers
}

function Request-AccessToken {
  param($Props)
  if ((Has-Value $Props "SHEETS_OAUTH_CLIENT_ID") -and
      (Has-Value $Props "SHEETS_OAUTH_CLIENT_SECRET") -and
      (Has-Value $Props "SHEETS_REFRESH_TOKEN")) {
    $body = @{
      client_id = [string]$Props["SHEETS_OAUTH_CLIENT_ID"]
      client_secret = [string]$Props["SHEETS_OAUTH_CLIENT_SECRET"]
      refresh_token = [string]$Props["SHEETS_REFRESH_TOKEN"]
      grant_type = "refresh_token"
    }
    $response = Invoke-RestMethod -Method Post -Uri "https://oauth2.googleapis.com/token" -Body $body
    if ([string]::IsNullOrWhiteSpace([string]$response.access_token)) {
      throw "OAuth refresh did not return an access token."
    }
    return [string]$response.access_token
  }
  if (Has-Value $Props "SHEETS_ACCESS_TOKEN") {
    return [string]$Props["SHEETS_ACCESS_TOKEN"]
  }
  throw "No Google Sheets auth is configured."
}

function Get-RoleRows {
  param($Props)
  $roleToProp = [ordered]@{
    DEVICE_MASTER = "SHEETS_DEVICE_MASTER_SHEET_ID"
    DMBT_LOG = "SHEETS_DMBT_LOG_SHEET_ID"
    HGT_CHECKS = "SHEETS_HGT_CHECKS_SHEET_ID"
    LOOKUP_OPTIONS = "SHEETS_LOOKUP_OPTIONS_SHEET_ID"
    APP_CONFIG = "SHEETS_APP_CONFIG_SHEET_ID"
  }
  $rows = @()
  foreach ($role in $roleToProp.Keys) {
    $propName = $roleToProp[$role]
    $sheetId = Get-PropValue $Props $propName
    $rows += [ordered]@{
      role = $role
      configured = -not [string]::IsNullOrWhiteSpace($sheetId)
      sheet_id = $sheetId
      title = $null
      header_status = if ([string]::IsNullOrWhiteSpace($sheetId)) { "NOT_CONFIGURED" } else { "PENDING" }
      headers = @()
      error = $null
    }
  }
  return $rows
}

function Write-InventoryMarkdown {
  param(
    [Parameter(Mandatory = $true)]$Inventory,
    [Parameter(Mandatory = $true)][string]$Path
  )
  $parent = Split-Path -Parent $Path
  if (-not (Test-Path -LiteralPath $parent)) {
    New-Item -ItemType Directory -Path $parent -Force | Out-Null
  }

  $lines = @()
  $lines += "# Google Sheets Inventory"
  $lines += ""
  $lines += "- Generated: $($Inventory.generated_at)"
  $lines += "- Status: $($Inventory.status)"
  $lines += "- Online attempted: $($Inventory.online.attempted)"
  $lines += ""
  $lines += "| Role | Configured | Sheet ID | Title | Header status | Headers | Error |"
  $lines += "|---|---:|---:|---|---|---|---|"
  foreach ($role in $Inventory.roles) {
    $headers = if ($role.headers.Count -gt 0) { ($role.headers -join ", ") } else { "" }
    $title = if ($null -eq $role.title) { "" } else { [string]$role.title }
    $errorText = if ($null -eq $role.error) { "" } else { [string]$role.error }
    $lines += "| $($role.role) | $($role.configured) | $($role.sheet_id) | $title | $($role.header_status) | $headers | $errorText |"
  }
  $lines += ""
  $lines += "Safety notes:"
  $lines += "- This inventory only reads metadata/header rows."
  $lines += "- It never writes Google Sheets or local app data."
  $lines += "- Secrets are intentionally excluded from this report."

  $lines | Set-Content -LiteralPath $Path -Encoding UTF8
}

$localPropertiesPath = Join-Path $ProjectRoot "android-mvp/local.properties"
$props = Read-PropertiesFile -Path $localPropertiesPath
$spreadsheetId = Get-PropValue $props "SHEETS_SPREADSHEET_ID"
if ([string]::IsNullOrWhiteSpace($spreadsheetId)) {
  throw "SHEETS_SPREADSHEET_ID is missing."
}

$roleRows = @(Get-RoleRows -Props $props)
$online = [ordered]@{
  attempted = $false
  note = "Offline mode: only configured role mappings were inspected."
}
$status = "PASS"

if (-not $Offline) {
  $online.attempted = $true
  $online.note = "Read spreadsheet metadata and configured header rows."
  try {
    $accessToken = Request-AccessToken -Props $props
    $metadataUri = "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId" +
      "?fields=sheets(properties(sheetId,title))"
    $metadata = Invoke-JsonGet -Uri $metadataUri -AccessToken $accessToken
    $titleBySheetId = @{}
    foreach ($sheet in @($metadata.sheets)) {
      $sheetId = [string]$sheet.properties.sheetId
      $titleBySheetId[$sheetId] = [string]$sheet.properties.title
    }

    foreach ($role in $roleRows) {
      if (-not $role.configured) { continue }
      if (-not $titleBySheetId.ContainsKey([string]$role.sheet_id)) {
        $role.header_status = "MISSING_SHEET"
        $role.error = "Configured sheetId was not found in spreadsheet metadata."
        $status = "WARN"
        continue
      }
      $role.title = $titleBySheetId[[string]$role.sheet_id]
      $encodedRange = [System.Uri]::EscapeDataString("$($role.title)!1:1")
      $valuesUri = "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/$encodedRange"
      $values = Invoke-JsonGet -Uri $valuesUri -AccessToken $accessToken
      $firstRow = @()
      if ($null -ne $values.values -and $values.values.Count -gt 0) {
        $firstRow = @($values.values[0])
      }
      $role.headers = @($firstRow | ForEach-Object { [string]$_ })
      $role.header_status = if ($role.headers.Count -gt 0) { "OK" } else { "EMPTY_HEADER" }
      if ($role.header_status -ne "OK") { $status = "WARN" }
    }
  } catch {
    $status = "FAIL"
    $online.note = "Online inventory failed without exposing secrets: $($_.Exception.Message)"
  }
}

if ([string]::IsNullOrWhiteSpace($OutputPath)) {
  $OutputPath = Join-Path $ProjectRoot "docs/sheets-inventory.md"
}

$inventory = [ordered]@{
  status = $status
  generated_at = (Get-Date).ToString("s")
  spreadsheet_configured = $true
  roles = $roleRows
  online = $online
  output_path = $OutputPath
}

Write-InventoryMarkdown -Inventory ([pscustomobject]$inventory) -Path $OutputPath

if ($Json) {
  $inventory | ConvertTo-Json -Depth 12
} else {
  Write-Host "Sheets inventory status: $status"
  Write-Host "Report: $OutputPath"
}

if ($status -eq "FAIL") {
  exit 1
}

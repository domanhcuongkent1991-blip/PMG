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

$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("codex-ops-script-test-" + [guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Path $tempRoot -Force | Out-Null

try {
  $worklogScript = Join-Path $ProjectRoot "scripts/ensure-daily-worklog.ps1"
  $verifyScript = Join-Path $ProjectRoot "scripts/verify-sheets-config.ps1"
  $inventoryScript = Join-Path $ProjectRoot "scripts/export-sheets-inventory.ps1"
  $inventoryNodeScript = Join-Path $ProjectRoot "scripts/export-sheets-inventory-node.js"

  $worklogJson = & powershell -NoProfile -ExecutionPolicy Bypass -File $worklogScript `
    -ProjectRoot $tempRoot `
    -Date "2026-05-02" `
    -Summary "Test session" `
    -Json
  $worklog = $worklogJson | ConvertFrom-Json
  $expectedWorklog = Join-Path $tempRoot "WORKLOG_2026-05-02.md"

  Assert-Equals "OK" $worklog.status "worklog status should be OK."
  Assert-True (Test-Path -LiteralPath $expectedWorklog) "daily worklog file should be created."
  $worklogContent = Get-Content -LiteralPath $expectedWorklog -Raw
  Assert-True ($worklogContent -match "# WORKLOG 2026-05-02") "daily worklog should contain expected title."
  Assert-True ($worklogContent -match "Test session") "daily worklog should contain provided summary."

  New-Item -ItemType Directory -Path (Join-Path $tempRoot "android-mvp") -Force | Out-Null
  @"
SHEETS_SPREADSHEET_ID=spreadsheet-123
SHEETS_DMBT_LOG_SHEET_ID=1607125070
SHEETS_HGT_CHECKS_SHEET_ID=57428884
SHEETS_OAUTH_CLIENT_ID=client-id.apps.googleusercontent.com
SHEETS_OAUTH_CLIENT_SECRET=super-secret-value
SHEETS_REFRESH_TOKEN=refresh-token-value
SHEETS_ACCESS_TOKEN=short-lived-token-value
"@ | Set-Content -LiteralPath (Join-Path $tempRoot "android-mvp/local.properties") -Encoding UTF8

  $verifyJson = & powershell -NoProfile -ExecutionPolicy Bypass -File $verifyScript `
    -ProjectRoot $tempRoot `
    -Offline `
    -Json
  $verify = $verifyJson | ConvertFrom-Json
  $verifyRaw = $verifyJson | Out-String

  Assert-Equals "PASS" $verify.status "offline config verification should pass."
  Assert-True ($verify.auth_mode -eq "REFRESH_TOKEN") "auth mode should prefer refresh token."
  Assert-True ($verify.access_token_embedded_required -eq $false) "access token should not be required when refresh token exists."
  Assert-True (-not $verifyRaw.Contains("super-secret-value")) "verification output must not expose client secret."
  Assert-True (-not $verifyRaw.Contains("refresh-token-value")) "verification output must not expose refresh token."
  Assert-True (-not $verifyRaw.Contains("short-lived-token-value")) "verification output must not expose access token."

  $inventoryJson = & powershell -NoProfile -ExecutionPolicy Bypass -File $inventoryScript `
    -ProjectRoot $tempRoot `
    -Offline `
    -Json
  $inventory = $inventoryJson | ConvertFrom-Json
  $inventoryRaw = $inventoryJson | Out-String

  Assert-Equals "PASS" $inventory.status "offline inventory should pass with configured base sheets."
  Assert-True ($inventory.roles.Count -ge 5) "inventory should include all known sheet roles."
  Assert-True ($inventory.roles.role -contains "DEVICE_MASTER") "inventory should include DEVICE_MASTER role."
  Assert-True ($inventory.roles.role -contains "LOOKUP_OPTIONS") "inventory should include LOOKUP_OPTIONS role."
  Assert-True ($inventory.roles.role -contains "APP_CONFIG") "inventory should include APP_CONFIG role."
  Assert-True (-not $inventoryRaw.Contains("super-secret-value")) "inventory output must not expose client secret."
  Assert-True (-not $inventoryRaw.Contains("refresh-token-value")) "inventory output must not expose refresh token."
  Assert-True (-not $inventoryRaw.Contains("short-lived-token-value")) "inventory output must not expose access token."

  $inventoryNodeJson = & node $inventoryNodeScript `
    --project-root $tempRoot `
    --offline `
    --json
  $inventoryNode = $inventoryNodeJson | ConvertFrom-Json
  $inventoryNodeRaw = $inventoryNodeJson | Out-String

  Assert-Equals "PASS" $inventoryNode.status "node offline inventory should pass with configured base sheets."
  Assert-True ($inventoryNode.roles.Count -ge 5) "node inventory should include all known sheet roles."
  Assert-True ($inventoryNode.roles.role -contains "DEVICE_MASTER") "node inventory should include DEVICE_MASTER role."
  Assert-True (-not $inventoryNodeRaw.Contains("super-secret-value")) "node inventory output must not expose client secret."
  Assert-True (-not $inventoryNodeRaw.Contains("refresh-token-value")) "node inventory output must not expose refresh token."
  Assert-True (-not $inventoryNodeRaw.Contains("short-lived-token-value")) "node inventory output must not expose access token."

  $selfTestOutput = & node $inventoryNodeScript --self-test
  Assert-True (($selfTestOutput | Out-String).Contains("PASS")) "node inventory self-test should pass."

  "PASS"
} finally {
  if (Test-Path -LiteralPath $tempRoot) {
    Remove-Item -LiteralPath $tempRoot -Recurse -Force
  }
}

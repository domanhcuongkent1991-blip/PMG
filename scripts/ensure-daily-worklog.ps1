param(
  [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path,
  [string]$Date = (Get-Date -Format "yyyy-MM-dd"),
  [string]$Summary = "Session started.",
  [switch]$Json
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$dateForFile = $Date.Trim()
if ($dateForFile -notmatch '^\d{4}-\d{2}-\d{2}$') {
  throw "Date must use yyyy-MM-dd format."
}

$worklogPath = Join-Path $ProjectRoot ("WORKLOG_$dateForFile.md")
$created = $false
$timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"

if (-not (Test-Path -LiteralPath $worklogPath)) {
  $lines = @(
    "# WORKLOG $dateForFile",
    "",
    "## Session Entries",
    "",
    "- $timestamp - $Summary",
    "",
    "## Verification",
    "",
    "- Pending.",
    "",
    "## Risks / Follow-up",
    "",
    "- Pending."
  )
  Set-Content -LiteralPath $worklogPath -Value ($lines -join [Environment]::NewLine) -Encoding UTF8
  $created = $true
} else {
  Add-Content -LiteralPath $worklogPath -Value ("- $timestamp - $Summary") -Encoding UTF8
}

$result = [ordered]@{
  status = "OK"
  date = $dateForFile
  path = $worklogPath
  created = $created
}

if ($Json) {
  $result | ConvertTo-Json -Depth 5
} else {
  Write-Host "Worklog ready: $worklogPath"
}

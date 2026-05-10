param(
  [string]$ApkPath,
  [switch]$LatestSafeBuild
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$safeBuildRoot = Join-Path $repoRoot "android-mvp\.codex-build"

if ($LatestSafeBuild) {
  $latestApk = Get-ChildItem -LiteralPath $safeBuildRoot -Directory -ErrorAction Stop |
    Sort-Object LastWriteTime -Descending |
    ForEach-Object {
      $candidate = Join-Path $_.FullName "app\outputs\apk\debug\app-debug.apk"
      if (Test-Path -LiteralPath $candidate) {
        Get-Item -LiteralPath $candidate
      }
    } |
    Select-Object -First 1

  if ($null -eq $latestApk) {
    throw "No app-debug.apk found under $safeBuildRoot"
  }

  $ApkPath = $latestApk.FullName
}

if ([string]::IsNullOrWhiteSpace($ApkPath)) {
  throw "Usage: .\scripts\verify-apk-seedless.ps1 -ApkPath <path> or -LatestSafeBuild"
}

$resolvedApk = Resolve-Path -LiteralPath $ApkPath
$apkItem = Get-Item -LiteralPath $resolvedApk

Add-Type -AssemblyName System.IO.Compression.FileSystem

function Read-ZipEntryText {
  param(
    [System.IO.Compression.ZipArchive]$Zip,
    [string]$EntryName
  )

  $entry = $Zip.GetEntry($EntryName)
  if ($null -eq $entry) {
    throw "APK is missing required asset: $EntryName"
  }

  $stream = $entry.Open()
  try {
    $reader = New-Object System.IO.StreamReader($stream)
    try {
      return @{
        Name = $EntryName
        Length = $entry.Length
        Text = $reader.ReadToEnd()
      }
    } finally {
      $reader.Dispose()
    }
  } finally {
    $stream.Dispose()
  }
}

$zip = [System.IO.Compression.ZipFile]::OpenRead($apkItem.FullName)
try {
  $assetNames = @(
    "assets/seed_device_logs.json",
    "assets/seed_hgt_checks.json"
  )

  $results = foreach ($assetName in $assetNames) {
    Read-ZipEntryText -Zip $zip -EntryName $assetName
  }
} finally {
  $zip.Dispose()
}

$hash = Get-FileHash -Algorithm SHA256 -LiteralPath $apkItem.FullName

Write-Host "APK: $($apkItem.FullName)"
Write-Host "SHA256: $($hash.Hash)"

foreach ($result in $results) {
  $trimmedText = $result.Text.Trim()
  $prefixLength = [Math]::Min(80, $trimmedText.Length)
  $prefix = if ($prefixLength -gt 0) { $trimmedText.Substring(0, $prefixLength) } else { "" }

  Write-Host "$($result.Name) len=$($result.Length) prefix=$prefix"

  if ($trimmedText -ne "[]") {
    throw "Seedless APK guard failed: $($result.Name) must be exactly [] after trim."
  }

  if ($result.Text -match "seed-beta-|seed-hgt-") {
    throw "Seedless APK guard failed: $($result.Name) still contains legacy seed IDs."
  }
}

Write-Host "PASS: APK seed assets are empty and safe to install."

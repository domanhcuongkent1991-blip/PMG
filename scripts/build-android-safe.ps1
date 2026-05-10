param(
  [switch]$SkipTests
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
if (Get-Variable -Name PSNativeCommandUseErrorActionPreference -ErrorAction SilentlyContinue) {
  $PSNativeCommandUseErrorActionPreference = $false
}

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$androidRoot = Join-Path $repoRoot "android-mvp"

if (-not (Test-Path -LiteralPath $androidRoot)) {
  throw "Missing android project folder: $androidRoot"
}

function Initialize-AndroidGradleEnvironment {
  param([string]$ProjectPath)

  $gradleHome = Join-Path $ProjectPath ".gradle-user-home"
  $androidHome = Join-Path $ProjectPath ".android-home"
  $javaHome = Join-Path $ProjectPath ".home"
  $tmpHome = Join-Path $ProjectPath ".tmp"
  $kotlinHome = Join-Path $ProjectPath ".kotlin"
  $appDataHome = Join-Path $ProjectPath ".appdata"

  foreach ($path in @($gradleHome, $androidHome, $javaHome, $tmpHome, $kotlinHome, $appDataHome)) {
    New-Item -ItemType Directory -Force -Path $path | Out-Null
  }

  $env:GRADLE_USER_HOME = $gradleHome
  $env:ANDROID_USER_HOME = $androidHome
  $env:TEMP = $tmpHome
  $env:TMP = $tmpHome
  $env:KOTLIN_USER_HOME = $kotlinHome
  $env:LOCALAPPDATA = $appDataHome
  $env:APPDATA = $appDataHome

  # Android Gradle Plugin 9 rejects setting both ANDROID_USER_HOME and the
  # deprecated ANDROID_PREFS_ROOT, even when they point to the same folder.
  Remove-Item Env:\ANDROID_PREFS_ROOT -ErrorAction SilentlyContinue

  $gradleOpts = @(
    "-Duser.home=$javaHome",
    "-Djava.io.tmpdir=$tmpHome",
    "-Dkotlin.daemon.client.alive.path=$tmpHome"
  )
  $existingGradleOpts = $env:GRADLE_OPTS
  if (-not [string]::IsNullOrWhiteSpace($existingGradleOpts)) {
    $env:GRADLE_OPTS = "$existingGradleOpts $($gradleOpts -join ' ')"
  } else {
    $env:GRADLE_OPTS = $gradleOpts -join " "
  }
}

function Remove-WithRetry {
  param([string]$PathToDelete)
  if (-not (Test-Path -LiteralPath $PathToDelete)) { return }
  $deleted = $false
  for ($i = 0; $i -lt 25; $i++) {
    try {
      Remove-Item -LiteralPath $PathToDelete -Recurse -Force -ErrorAction Stop
      $deleted = $true
      break
    } catch {
      Start-Sleep -Milliseconds 400
    }
  }
  if (-not $deleted) {
    Write-Host "WARN: still locked -> $PathToDelete" -ForegroundColor Yellow
  }
}

function Invoke-LockCleanup {
  param([string]$ProjectPath)

  Push-Location $ProjectPath
  try {
    & .\gradlew.bat --stop | Out-Host
  } catch {
    Write-Host "WARN: gradle --stop failed: $($_.Exception.Message)" -ForegroundColor Yellow
  }

  Get-Process | Where-Object {
    $_.ProcessName -match "java|gradle|kotlin|ksp" -and $_.Path -and $_.Path.StartsWith($repoRoot.Path)
  } | ForEach-Object {
    try {
      Stop-Process -Id $_.Id -Force -ErrorAction Stop
    } catch {
      # ignore non-owned processes
    }
  }

  $paths = @(
    (Join-Path $ProjectPath "app\build\generated\ksp"),
    (Join-Path $ProjectPath "app\build\kspCaches"),
    (Join-Path $ProjectPath "app\build\intermediates\incremental\debug\mergeDebugResources"),
    (Join-Path $ProjectPath "app\build\intermediates\incremental\debug\packageDebugResources"),
    (Join-Path $ProjectPath "app\build\intermediates\packaged_res\debug\packageDebugResources"),
    (Join-Path $ProjectPath "app\build\intermediates\merged_res_blame_folder\debug\mergeDebugResources"),
    (Join-Path $ProjectPath "build\reports\problems")
  )
  foreach ($path in $paths) {
    Remove-WithRetry -PathToDelete $path
  }
  Pop-Location
}

function Invoke-GradleWithRetry {
  param(
    [string]$ProjectPath,
    [string]$TaskName,
    [string[]]$GradleArgs,
    [string]$BuildId
  )

  Push-Location $ProjectPath
  try {
    for ($attempt = 1; $attempt -le 3; $attempt++) {
      Write-Host "[$TaskName] attempt $attempt/3" -ForegroundColor Cyan
      $runArgs = @("-PcodexBuildId=$BuildId") + $GradleArgs
      $output = & .\gradlew.bat @runArgs 2>&1
      $output | Out-Host
      if ($LASTEXITCODE -eq 0) {
        return
      }

      $isLockError = $output -match "AccessDeniedException|Unable to delete directory"
      if ($isLockError -and $attempt -lt 3) {
        Write-Host "[$TaskName] lock detected, running cleanup..." -ForegroundColor Yellow
        Pop-Location
        Invoke-LockCleanup -ProjectPath $ProjectPath
        Push-Location $ProjectPath
        continue
      }

      throw "[$TaskName] failed after attempt $attempt"
    }
  } finally {
    Pop-Location
  }
}

Initialize-AndroidGradleEnvironment -ProjectPath $androidRoot
Invoke-LockCleanup -ProjectPath $androidRoot

$buildId = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds().ToString()

if (-not $SkipTests) {
  Invoke-GradleWithRetry -ProjectPath $androidRoot -TaskName "testDebugUnitTest" -GradleArgs @("testDebugUnitTest", "--no-daemon", "--max-workers=1") -BuildId $buildId
}

Invoke-GradleWithRetry -ProjectPath $androidRoot -TaskName "assembleDebug" -GradleArgs @("assembleDebug", "--no-daemon", "--max-workers=1") -BuildId $buildId

Write-Host "Build pipeline completed successfully." -ForegroundColor Green

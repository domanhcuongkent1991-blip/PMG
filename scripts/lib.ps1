Set-StrictMode -Version Latest

function Write-Step {
  param(
    [Parameter(Mandatory = $true)][string]$Message
  )
  $ts = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
  Write-Host "[$ts] $Message"
}

function Ensure-Dir {
  param(
    [Parameter(Mandatory = $true)][string]$Path
  )
  if (-not (Test-Path -LiteralPath $Path)) {
    New-Item -ItemType Directory -Path $Path -Force | Out-Null
  }
}

function Ensure-LocalNpmCache {
  param(
    [string]$ProjectRoot = (Get-Location).Path
  )
  $cacheDir = Join-Path $ProjectRoot ".npm-cache"
  Ensure-Dir -Path $cacheDir
  $env:npm_config_cache = $cacheDir
}

function Assert-CommandExists {
  param(
    [Parameter(Mandatory = $true)][string]$CommandName
  )
  $cmd = Get-Command $CommandName -ErrorAction SilentlyContinue
  if (-not $cmd) {
    throw "Missing required command: $CommandName"
  }
}

function Get-GsdRuntimeFlag {
  param(
    [Parameter(Mandatory = $true)][string]$Runtime
  )
  $map = @{
    "claude"      = "--claude"
    "opencode"    = "--opencode"
    "gemini"      = "--gemini"
    "kilo"        = "--kilo"
    "codex"       = "--codex"
    "copilot"     = "--copilot"
    "cursor"      = "--cursor"
    "windsurf"    = "--windsurf"
    "antigravity" = "--antigravity"
    "augment"     = "--augment"
    "trae"        = "--trae"
    "qwen"        = "--qwen"
    "codebuddy"   = "--codebuddy"
    "cline"       = "--cline"
    "all"         = "--all"
  }
  if (-not $map.ContainsKey($Runtime)) {
    throw "Unsupported runtime: $Runtime"
  }
  return $map[$Runtime]
}

function Invoke-StepWithRetry {
  param(
    [Parameter(Mandatory = $true)][string]$StepName,
    [Parameter(Mandatory = $true)][scriptblock]$Action,
    [int]$MaxAttempts = 3,
    [switch]$DryRun
  )

  if ($DryRun) {
    Write-Step "[DRY-RUN] $StepName"
    return @{
      Step = $StepName
      Status = "SKIPPED"
      Attempts = 0
      Error = ""
    }
  }

  $attempt = 0
  while ($attempt -lt $MaxAttempts) {
    $attempt++
    try {
      Write-Step "$StepName (attempt $attempt/$MaxAttempts)"
      $null = & $Action
      return @{
        Step = $StepName
        Status = "OK"
        Attempts = $attempt
        Error = ""
      }
    } catch {
      $err = $_.Exception.Message
      Write-Step "Step failed: $StepName :: $err"
      if ($attempt -ge $MaxAttempts) {
        return @{
          Step = $StepName
          Status = "FAILED"
          Attempts = $attempt
          Error = $err
        }
      }
      Start-Sleep -Seconds 2
    }
  }
}

function Set-GsdSafetyConfig {
  param(
    [Parameter(Mandatory = $true)][ValidateSet("safe", "balanced", "fast")][string]$SafetyLevel,
    [string]$ConfigPath = ".planning/config.json"
  )

  if (-not (Test-Path -LiteralPath $ConfigPath)) {
    throw "Config file not found: $ConfigPath"
  }

  $raw = Get-Content -LiteralPath $ConfigPath -Raw
  if ([string]::IsNullOrWhiteSpace($raw)) {
    throw "Config file is empty: $ConfigPath"
  }

  $cfg = $raw | ConvertFrom-Json

  function Ensure-ObjectProperty {
    param(
      [Parameter(Mandatory = $true)][psobject]$Object,
      [Parameter(Mandatory = $true)][string]$Name
    )
    $prop = $Object.PSObject.Properties[$Name]
    if ($null -eq $prop -or $null -eq $prop.Value -or $prop.Value -isnot [psobject] -or $prop.Value -is [bool] -or $prop.Value -is [string] -or $prop.Value -is [int]) {
      if ($null -eq $prop) {
        $Object | Add-Member -MemberType NoteProperty -Name $Name -Value ([pscustomobject]@{})
      } else {
        $Object.PSObject.Properties[$Name].Value = [pscustomobject]@{}
      }
    }
  }

  function Set-ObjectProperty {
    param(
      [Parameter(Mandatory = $true)][psobject]$Object,
      [Parameter(Mandatory = $true)][string]$Name,
      [AllowNull()]$Value
    )
    if ($null -eq $Object.PSObject.Properties[$Name]) {
      $Object | Add-Member -MemberType NoteProperty -Name $Name -Value $Value
    } else {
      $Object.PSObject.Properties[$Name].Value = $Value
    }
  }

  Ensure-ObjectProperty -Object $cfg -Name "workflow"
  Ensure-ObjectProperty -Object $cfg -Name "parallelization"
  Ensure-ObjectProperty -Object $cfg -Name "planning"

  switch ($SafetyLevel) {
    "safe" {
      Set-ObjectProperty -Object $cfg -Name "mode" -Value "interactive"
      Set-ObjectProperty -Object $cfg -Name "model_profile" -Value "balanced"
      Set-ObjectProperty -Object $cfg.workflow -Name "research" -Value $true
      Set-ObjectProperty -Object $cfg.workflow -Name "plan_check" -Value $true
      Set-ObjectProperty -Object $cfg.workflow -Name "verifier" -Value $true
      Set-ObjectProperty -Object $cfg.workflow -Name "auto_advance" -Value $true
      Set-ObjectProperty -Object $cfg.workflow -Name "skip_discuss" -Value $false
      Set-ObjectProperty -Object $cfg.parallelization -Name "enabled" -Value $true
      Set-ObjectProperty -Object $cfg.planning -Name "commit_docs" -Value $false
    }
    "balanced" {
      Set-ObjectProperty -Object $cfg -Name "mode" -Value "interactive"
      Set-ObjectProperty -Object $cfg -Name "model_profile" -Value "balanced"
      Set-ObjectProperty -Object $cfg.workflow -Name "research" -Value $true
      Set-ObjectProperty -Object $cfg.workflow -Name "plan_check" -Value $true
      Set-ObjectProperty -Object $cfg.workflow -Name "verifier" -Value $true
      Set-ObjectProperty -Object $cfg.workflow -Name "auto_advance" -Value $true
      Set-ObjectProperty -Object $cfg.workflow -Name "skip_discuss" -Value $true
      Set-ObjectProperty -Object $cfg.parallelization -Name "enabled" -Value $true
      Set-ObjectProperty -Object $cfg.planning -Name "commit_docs" -Value $false
    }
    "fast" {
      Set-ObjectProperty -Object $cfg -Name "mode" -Value "yolo"
      Set-ObjectProperty -Object $cfg -Name "model_profile" -Value "budget"
      Set-ObjectProperty -Object $cfg.workflow -Name "research" -Value $false
      Set-ObjectProperty -Object $cfg.workflow -Name "plan_check" -Value $false
      Set-ObjectProperty -Object $cfg.workflow -Name "verifier" -Value $true
      Set-ObjectProperty -Object $cfg.workflow -Name "auto_advance" -Value $true
      Set-ObjectProperty -Object $cfg.workflow -Name "skip_discuss" -Value $true
      Set-ObjectProperty -Object $cfg.parallelization -Name "enabled" -Value $true
      Set-ObjectProperty -Object $cfg.planning -Name "commit_docs" -Value $false
    }
  }

  $cfg | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $ConfigPath -Encoding UTF8
}

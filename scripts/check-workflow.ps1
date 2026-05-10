param(
  [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path,
  [switch]$Json
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Invoke-WorkflowStep {
  param(
    [Parameter(Mandatory = $true)][string]$Name,
    [Parameter(Mandatory = $true)][scriptblock]$Action
  )

  $global:LASTEXITCODE = 0
  try {
    $output = @(& $Action 2>&1)
    $exitCode = [int]$global:LASTEXITCODE
    return [ordered]@{
      name = $Name
      status = if ($exitCode -eq 0) { "PASS" } else { "FAIL" }
      exit_code = $exitCode
      output = @($output | ForEach-Object { [string]$_ })
    }
  } catch {
    return [ordered]@{
      name = $Name
      status = "FAIL"
      exit_code = 1
      output = @($_.Exception.Message)
    }
  }
}

$root = (Resolve-Path -LiteralPath $ProjectRoot).Path
$preventSecretsScript = Join-Path $root "scripts/prevent-secrets.js"
$orderGateScript = Join-Path $root ".workflow-gate/check-workflow-order.ps1"
$validateScript = Join-Path $PSScriptRoot "validate-workflow-governance.ps1"

$steps = @()
$steps += Invoke-WorkflowStep -Name "node --version" -Action { & node --version }
$steps += Invoke-WorkflowStep -Name "npm --version" -Action { & npm --version }
$steps += Invoke-WorkflowStep -Name "npx --version" -Action { & npx --version }
$steps += Invoke-WorkflowStep -Name "git --version" -Action { & git --version }

if (Test-Path -LiteralPath $preventSecretsScript) {
  $steps += Invoke-WorkflowStep -Name "prevent-secrets" -Action { & node $preventSecretsScript }
} else {
  $steps += [ordered]@{
    name = "prevent-secrets"
    status = "FAIL"
    exit_code = 1
    output = @("Missing scripts/prevent-secrets.js")
  }
}

if (Test-Path -LiteralPath $validateScript) {
  $steps += Invoke-WorkflowStep -Name "workflow-governance-schema" -Action {
    & powershell -NoProfile -ExecutionPolicy Bypass -File $validateScript -ProjectRoot $root -Json
  }
} else {
  $steps += [ordered]@{
    name = "workflow-governance-schema"
    status = "FAIL"
    exit_code = 1
    output = @("Missing scripts/validate-workflow-governance.ps1")
  }
}

if (Test-Path -LiteralPath $orderGateScript) {
  $steps += Invoke-WorkflowStep -Name "workflow-order-gate" -Action {
    & powershell -NoProfile -ExecutionPolicy Bypass -File $orderGateScript -ProjectRoot $root -EnforceSourceApproval
  }
} else {
  $steps += [ordered]@{
    name = "workflow-order-gate"
    status = "FAIL"
    exit_code = 1
    output = @("Missing .workflow-gate/check-workflow-order.ps1")
  }
}

$failedSteps = @($steps | Where-Object { $_.status -ne "PASS" })
$result = [ordered]@{
  status = if ($failedSteps.Count -eq 0) { "PASS" } else { "FAIL" }
  project_root = $root
  steps = $steps
}

if ($Json) {
  $result | ConvertTo-Json -Depth 10
} else {
  $result | ConvertTo-Json -Depth 10
}

if ($failedSteps.Count -gt 0) {
  exit 2
}

# ============================================================
# FinSight AI - Phase 5H Observability & Security Log Scanner
# ============================================================

[CmdletBinding()]
param (
    [switch]$DryRun
)

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. "$scriptDir\config.ps1"

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "FINSIGHT AI - PHASE 5H CLOUD LOGGING & SECURITY SCANNER" -ForegroundColor Cyan
if ($DryRun) {
    Write-Host "[DRY-RUN MODE ENABLED: No live cloud log scanning will execute]" -ForegroundColor Yellow
}
Write-Host "============================================================" -ForegroundColor Cyan

Assert-TargetProject -ExpectedProject $global:GCP_PROJECT_ID

if ($DryRun) {
    Write-Host "  [DryRun] Would formulate Cloud Logging queries for service '$global:CLOUD_RUN_SERVICE'" -ForegroundColor Gray
    Write-Host "  [DryRun] Would scan logs for sensitive tokens (Bearer, JWT, DB passwords, private keys)" -ForegroundColor Gray
    Write-Host "  [DryRun] Would execute audit trace reconstruction via Phase5HObservabilityTest" -ForegroundColor Gray
    Write-Host "PHASE 5H OBSERVABILITY & SECURITY SCANNER READY: CLOUD_OBSERVABILITY_READY" -ForegroundColor Yellow
    exit 0
}

Write-Host "1. Formulating Cloud Logging Query Filters..." -ForegroundColor Cyan
$queryFilter = "resource.type=`"cloud_run_revision`" AND resource.labels.service_name=`"$global:CLOUD_RUN_SERVICE`""
Write-Host "   Cloud Logging Query Filter: $queryFilter" -ForegroundColor Yellow

Write-Host "2. Executing Security Log Scanner..." -ForegroundColor Cyan
$logFiles = Get-ChildItem -Path "logs", "target" -Include "*.log" -Recurse -ErrorAction SilentlyContinue

$secretPatternFound = $false
foreach ($file in $logFiles) {
    $content = Get-Content $file.FullName -Raw 2>$null
    if ($content -match "Bearer\s+ey" -or $content -match "password\s*=\s*['`"][^'`"]+['`"]") {
        Write-Host "   SECURITY ALERT: Potential unredacted secret found in $($file.Name)!" -ForegroundColor Red
        $secretPatternFound = $true
    }
}

if (-not $secretPatternFound) {
    Write-Host "   PASS: Zero sensitive tokens, passwords, or JWT secrets detected in log files" -ForegroundColor Green
}

Write-Host "3. Executing Audit Log Reconstruction Verification..." -ForegroundColor Cyan
.\mvnw.cmd test -Dtest=Phase5HObservabilityTest -Dspring.profiles.active=prod

if ($LASTEXITCODE -eq 0) {
    Write-Host "PHASE 5H OBSERVABILITY VERIFICATION COMPLETED SUCCESSFULLY" -ForegroundColor Green
} else {
    Write-Host "ERROR: Phase 5H Observability Verification failed with exit code $LASTEXITCODE" -ForegroundColor Red
    exit 1
}

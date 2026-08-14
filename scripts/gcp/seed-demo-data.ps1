# ============================================================
# FinSight AI - Explicit Cloud Evaluation Demo Data Seeder
# ============================================================

[CmdletBinding()]
param (
    [switch]$DryRun
)

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. "$scriptDir\config.ps1"

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "FINSIGHT AI - CLOUD EVALUATION DEMO SEEDER" -ForegroundColor Cyan
if ($DryRun) {
    Write-Host "[DRY-RUN MODE ENABLED: No cloud seeder will run]" -ForegroundColor Yellow
}
Write-Host "============================================================" -ForegroundColor Cyan

Assert-TargetProject -ExpectedProject $global:GCP_PROJECT_ID

if ($DryRun) {
    Write-Host "  [DryRun] Would execute EvaluationDemoDataSeeder against target database" -ForegroundColor Gray
    Write-Host "  [DryRun] Would seed 2 users, 6 accounts, 26 categories, 188 transactions idempotently" -ForegroundColor Gray
    Write-Host "  [DryRun] Would verify May 2026 (INR 8,500.00) vs June 2026 (INR 25,450.00) food spending ground truth" -ForegroundColor Gray
    Write-Host "CLOUD DEMO SEEDER AUTOMATION READY: DEMO_SEEDER_READY" -ForegroundColor Yellow
    exit 0
}

Write-Host "Executing explicit evaluation seeder..." -ForegroundColor Cyan
.\mvnw.cmd test -Dtest=EvaluationDemoDataSeeder -Dspring.profiles.active=prod

if ($LASTEXITCODE -eq 0) {
    Write-Host "CLOUD DEMO DATA SEEDED SUCCESSFULLY: GROUND_TRUTH_VERIFIED" -ForegroundColor Green
} else {
    Write-Host "ERROR: Cloud demo seeder failed with exit code $LASTEXITCODE" -ForegroundColor Red
    exit 1
}

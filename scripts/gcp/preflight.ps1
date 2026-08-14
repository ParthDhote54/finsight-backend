# ============================================================
# FinSight AI - GCP Account, Project & Billing Preflight Check
# ============================================================

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. "$scriptDir\config.ps1"

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "FINSIGHT AI - GCP ACCESS PREFLIGHT CHECK" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan

# Step 1: Check active account
$activeAccount = (gcloud auth list --filter=status:ACTIVE --format="value(account)" 2>$null)
if (-not $activeAccount) {
    Write-Host "GCP PREFLIGHT STATUS: GCP_ACCESS_BLOCKED (No active gcloud authentication found)" -ForegroundColor Red
    Write-Host "To resolve: Run 'gcloud auth login' and 'gcloud auth application-default login'" -ForegroundColor Yellow
    exit 1
}
Write-Host "ACTIVE GCP ACCOUNT: $activeAccount" -ForegroundColor Green

# Step 2: Check project safety guard
Assert-TargetProject -ExpectedProject $global:GCP_PROJECT_ID

# Step 3: Check project describe access
Write-Host "Checking access to project $global:GCP_PROJECT_ID..." -ForegroundColor Cyan
$projectDesc = (gcloud projects describe $global:GCP_PROJECT_ID --format="json" 2>$null)
if (-not $projectDesc) {
    Write-Host "GCP PREFLIGHT STATUS: GCP_ACCESS_BLOCKED (Account '$activeAccount' lacks permission to describe project '$global:GCP_PROJECT_ID')" -ForegroundColor Red
    Write-Host "To resolve: Ensure account is granted Viewer/Editor/Owner role on project $global:GCP_PROJECT_ID ($global:GCP_PROJECT_NUMBER)" -ForegroundColor Yellow
    exit 1
}
Write-Host "PROJECT ACCESS OK: Verified project $global:GCP_PROJECT_ID ($global:GCP_PROJECT_NUMBER)" -ForegroundColor Green

# Step 4: Check billing status
Write-Host "Checking billing status for $global:GCP_PROJECT_ID..." -ForegroundColor Cyan
$billingEnabled = (gcloud beta billing projects describe $global:GCP_PROJECT_ID --format="value(billingEnabled)" 2>$null)
if ($billingEnabled -ne "True") {
    Write-Host "GCP PREFLIGHT STATUS: BILLING_BLOCKED (Billing is NOT enabled for project '$global:GCP_PROJECT_ID')" -ForegroundColor Red
    Write-Host "To resolve: Link an active GCP Billing Account to project $global:GCP_PROJECT_ID" -ForegroundColor Yellow
    exit 1
}
Write-Host "BILLING OK: Billing is active on project $global:GCP_PROJECT_ID" -ForegroundColor Green

Write-Host "============================================================" -ForegroundColor Green
Write-Host "GCP PREFLIGHT STATUS: ALL_CHECKS_PASSED" -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Green

# END OF SCRIPT

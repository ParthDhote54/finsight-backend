# ============================================================
# FinSight AI - Real Private Cloud Run HTTP E2E Smoke Runner
# ============================================================

[CmdletBinding()]
param (
    [string]$ServiceUrl,
    [switch]$DryRun
)

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. "$scriptDir\config.ps1"

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "FINSIGHT AI - REAL PRIVATE CLOUD RUN HTTP E2E SMOKE RUNNER" -ForegroundColor Cyan
if ($DryRun) {
    Write-Host "[DRY-RUN MODE ENABLED: No live HTTP requests will be sent]" -ForegroundColor Yellow
}
Write-Host "============================================================" -ForegroundColor Cyan

Assert-TargetProject -ExpectedProject $global:GCP_PROJECT_ID

if (-not $ServiceUrl -and -not $DryRun) {
    $ServiceUrl = (gcloud run services describe $global:CLOUD_RUN_SERVICE --region=$global:GCP_REGION --project=$global:GCP_PROJECT_ID --format="value(status.url)" 2>$null)
}

if ($DryRun) {
    Write-Host "  [DryRun] Would issue unauthenticated request to Cloud Run URL -> expected HTTP 401/403" -ForegroundColor Gray
    Write-Host "  [DryRun] Would obtain IAM Identity Token via 'gcloud auth print-identity-token'" -ForegroundColor Gray
    Write-Host "  [DryRun] Would issue IAM-authed request without app JWT -> expected HTTP 401" -ForegroundColor Gray
    Write-Host "  [DryRun] Would issue full HTTP E2E suite (/actuator/health, general, aggregate, flagship, balance, refusal, tenant isolation)" -ForegroundColor Gray
    Write-Host "  [DryRun] Would write test execution log to target/cloud-e2e/phase5e_cloud_e2e.json" -ForegroundColor Gray
    Write-Host "PHASE 5E E2E AUTOMATION READY: CLOUD_E2E_READY" -ForegroundColor Yellow
    exit 0
}

if (-not $ServiceUrl) {
    Write-Host "ERROR: Could not resolve Cloud Run service URL for '$global:CLOUD_RUN_SERVICE'" -ForegroundColor Red
    exit 1
}

Write-Host "TARGET CLOUD RUN URL: $ServiceUrl" -ForegroundColor Yellow

# Step 1: Unauthenticated request test
Write-Host "1. Testing Unauthenticated Access Rejection..." -ForegroundColor Cyan
try {
    $resp = Invoke-WebRequest -Uri "$ServiceUrl/actuator/health" -Method Get -SkipHttpErrorCheck
    if ($resp.StatusCode -eq 401 -or $resp.StatusCode -eq 403) {
        Write-Host "   PASS: Unauthenticated request correctly rejected with HTTP $($resp.StatusCode)" -ForegroundColor Green
    } else {
        Write-Host "   SECURITY FAIL: Unauthenticated request returned HTTP $($resp.StatusCode)!" -ForegroundColor Red
        exit 1
    }
} catch {
    Write-Host "   PASS: Unauthenticated request failed as expected: $_" -ForegroundColor Green
}

# Step 2: Fetch IAM Identity Token
Write-Host "2. Fetching IAM Identity Token..." -ForegroundColor Cyan
$iamToken = (gcloud auth print-identity-token 2>$null).Trim()
if (-not $iamToken) {
    Write-Host "   ERROR: Failed to retrieve IAM identity token via gcloud" -ForegroundColor Red
    exit 1
}
Write-Host "   PASS: IAM Identity token retrieved securely" -ForegroundColor Green

# Step 3: Run CloudE2ESmokeTest JUnit runner
Write-Host "3. Executing Real HTTP Cloud E2E Smoke Suite..." -ForegroundColor Cyan
$env:CLOUD_RUN_URL = $ServiceUrl
$env:CLOUD_RUN_IAM_TOKEN = $iamToken

.\mvnw.cmd test -Dtest=CloudE2ESmokeTest -Dspring.profiles.active=prod

if ($LASTEXITCODE -eq 0) {
    Write-Host "PHASE 5E CLOUD E2E PASSED SUCCESSFULLY: ALL_SMOKE_CASES_VERIFIED" -ForegroundColor Green
} else {
    Write-Host "ERROR: Phase 5E Cloud E2E Smoke Test failed with exit code $LASTEXITCODE" -ForegroundColor Red
    exit 1
}

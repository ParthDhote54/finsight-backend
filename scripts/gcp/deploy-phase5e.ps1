# ============================================================
# FinSight AI - Phase 5E Private Cloud Run Service Deployment
# ============================================================

[CmdletBinding()]
param (
    [string]$ImageTag = "latest",
    [switch]$DryRun
)

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. "$scriptDir\config.ps1"

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "FINSIGHT AI - PHASE 5E PRIVATE CLOUD RUN DEPLOYMENT" -ForegroundColor Cyan
if ($DryRun) {
    Write-Host "[DRY-RUN MODE ENABLED: No Cloud Run service will be deployed]" -ForegroundColor Yellow
}
Write-Host "============================================================" -ForegroundColor Cyan

Assert-TargetProject -ExpectedProject $global:GCP_PROJECT_ID

$imageUri = "$global:GCP_REGION-docker.pkg.dev/$global:GCP_PROJECT_ID/$global:ARTIFACT_REPO/$global:IMAGE_NAME`:$ImageTag"
$cloudSqlConnStr = "$global:GCP_PROJECT_ID`:$global:GCP_REGION`:$global:CLOUD_SQL_INSTANCE"

Write-Host "IMAGE URI           : $imageUri" -ForegroundColor Yellow
Write-Host "CLOUD RUN SERVICE   : $global:CLOUD_RUN_SERVICE" -ForegroundColor Yellow
Write-Host "CLOUD SQL CONNECTION: $cloudSqlConnStr" -ForegroundColor Yellow
Write-Host "SERVICE ACCOUNT     : $global:RUNTIME_SERVICE_ACCOUNT" -ForegroundColor Yellow

if ($DryRun) {
    Write-Host ""
    Write-Host "  [DryRun] Would deploy private Cloud Run service '$global:CLOUD_RUN_SERVICE' (--no-allow-unauthenticated)" -ForegroundColor Gray
    Write-Host "  [DryRun] Would attach Cloud SQL connection '$cloudSqlConnStr'" -ForegroundColor Gray
    Write-Host "  [DryRun] Would bind Secret Manager secrets SPRING_DATASOURCE_PASSWORD and JWT_SECRET" -ForegroundColor Gray
    Write-Host "  [DryRun] Would set SPRING_PROFILES_ACTIVE=prod, GCP_PROJECT_ID=$global:GCP_PROJECT_ID, VERTEX_LOCATION=$global:VERTEX_LOCATION" -ForegroundColor Gray
    Write-Host "  [DryRun] Would set CPU=1, RAM=512MiB, max-instances=5, concurrency=80" -ForegroundColor Gray
    Write-Host "  [DryRun] Would verify IAM policy ensures no public (allUsers) invoker access" -ForegroundColor Gray
    Write-Host ""
    Write-Host "PHASE 5E DEPLOYMENT AUTOMATION READY: PHASE_5E_DEPLOYMENT_READY" -ForegroundColor Yellow
    exit 0
}

Write-Host "Deploying PRIVATE Cloud Run service..." -ForegroundColor Cyan

gcloud run deploy $global:CLOUD_RUN_SERVICE `
    --image=$imageUri `
    --region=$global:GCP_REGION `
    --service-account=$global:RUNTIME_SERVICE_ACCOUNT `
    --add-cloudsql-instances=$cloudSqlConnStr `
    --no-allow-unauthenticated `
    --set-env-vars="SPRING_PROFILES_ACTIVE=prod,GCP_PROJECT_ID=$global:GCP_PROJECT_ID,VERTEX_LOCATION=$global:VERTEX_LOCATION,SPRING_DATASOURCE_URL=jdbc:postgresql:///finsight?socketFactory=com.google.cloud.sql.postgres.SocketFactory&cloudSqlInstance=$cloudSqlConnStr" `
    --set-secrets="SPRING_DATASOURCE_PASSWORD=SPRING_DATASOURCE_PASSWORD:latest,JWT_SECRET=JWT_SECRET:latest" `
    --cpu=1 `
    --memory=512MiB `
    --max-instances=5 `
    --concurrency=80 `
    --timeout=300s `
    --project=$global:GCP_PROJECT_ID

if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: Cloud Run deployment failed with exit code $LASTEXITCODE" -ForegroundColor Red
    exit 1
}

# Security Gate Verification
Write-Host "Verifying Cloud Run IAM Security (Public Invocation Check)..." -ForegroundColor Cyan
$iamPolicy = (gcloud run services get-iam-policy $global:CLOUD_RUN_SERVICE --region=$global:GCP_REGION --project=$global:GCP_PROJECT_ID --format="json" 2>$null)
if ($iamPolicy -like "*allUsers*") {
    Write-Host "SECURITY GATE FAIL: Cloud Run service is publicly accessible (allUsers binding found)!" -ForegroundColor Red
    exit 1
}

$serviceUrl = (gcloud run services describe $global:CLOUD_RUN_SERVICE --region=$global:GCP_REGION --project=$global:GCP_PROJECT_ID --format="value(status.url)" 2>$null)

Write-Host "============================================================" -ForegroundColor Green
Write-Host "PHASE 5E DEPLOYMENT SUCCESSFUL" -ForegroundColor Green
Write-Host "  Service URL     : $serviceUrl" -ForegroundColor Green
Write-Host "  Private Access  : VERIFIED (--no-allow-unauthenticated)" -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Green

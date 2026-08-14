# ============================================================
# FinSight AI - Container Build & Push to Artifact Registry
# ============================================================

[CmdletBinding()]
param (
    [string]$ImageTag = "latest",
    [switch]$DryRun
)

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. "$scriptDir\config.ps1"

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "FINSIGHT AI - CONTAINER BUILD & PUSH" -ForegroundColor Cyan
if ($DryRun) {
    Write-Host "[DRY-RUN MODE ENABLED: No container push or build will execute]" -ForegroundColor Yellow
}
Write-Host "============================================================" -ForegroundColor Cyan

Assert-TargetProject -ExpectedProject $global:GCP_PROJECT_ID

$imageUri = "$global:GCP_REGION-docker.pkg.dev/$global:GCP_PROJECT_ID/$global:ARTIFACT_REPO/$global:IMAGE_NAME`:$ImageTag"

Write-Host "TARGET IMAGE URI: $imageUri" -ForegroundColor Yellow

if ($DryRun) {
    Write-Host "  [DryRun] Would configure gcloud docker auth for $global:GCP_REGION-docker.pkg.dev" -ForegroundColor Gray
    Write-Host "  [DryRun] Would build docker image from Dockerfile" -ForegroundColor Gray
    Write-Host "  [DryRun] Would push image to $imageUri" -ForegroundColor Gray
    Write-Host "PHASE 5D IMAGE BUILD & PUSH READY: PHASE_5D_IMAGE_BUILD_READY" -ForegroundColor Yellow
    exit 0
}

Write-Host "Configuring Docker authentication for Artifact Registry..." -ForegroundColor Cyan
gcloud auth configure-docker "$global:GCP_REGION-docker.pkg.dev" --quiet

Write-Host "Building Docker image ($imageUri)..." -ForegroundColor Cyan
docker build -t $imageUri -f Dockerfile .

if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: Local Docker build failed with exit code $LASTEXITCODE" -ForegroundColor Red
    exit 1
}

Write-Host "Pushing container image to Artifact Registry..." -ForegroundColor Cyan
docker push $imageUri

if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: Container push to Artifact Registry failed with exit code $LASTEXITCODE" -ForegroundColor Red
    exit 1
}

Write-Host "CONTAINER BUILD AND PUSH COMPLETED SUCCESSFULLY: $imageUri" -ForegroundColor Green

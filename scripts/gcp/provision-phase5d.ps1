# ============================================================
# FinSight AI - Phase 5D Cloud Infrastructure Provisioning
# ============================================================

[CmdletBinding()]
param (
    [switch]$DryRun
)

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. "$scriptDir\config.ps1"

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "FINSIGHT AI - PHASE 5D INFRASTRUCTURE PROVISIONING" -ForegroundColor Cyan
if ($DryRun) {
    Write-Host "[DRY-RUN MODE ENABLED: No GCP resources will be mutated]" -ForegroundColor Yellow
}
Write-Host "============================================================" -ForegroundColor Cyan

Assert-TargetProject -ExpectedProject $global:GCP_PROJECT_ID

$requiredApis = @(
    "run.googleapis.com",
    "sqladmin.googleapis.com",
    "artifactregistry.googleapis.com",
    "aiplatform.googleapis.com",
    "secretmanager.googleapis.com",
    "iam.googleapis.com"
)

# Step 1: APIs
Write-Host ""
Write-Host "1. Verifying Required APIs..." -ForegroundColor Cyan
foreach ($api in $requiredApis) {
    if ($DryRun) {
        Write-Host "  [DryRun] Would verify/enable API: $api" -ForegroundColor Gray
    } else {
        Write-Host "  Enabling API: $api..." -ForegroundColor Cyan
        gcloud services enable $api --project=$global:GCP_PROJECT_ID
    }
}

# Step 2: Artifact Registry
$repoName = $global:ARTIFACT_REPO
$gcpRegion = $global:GCP_REGION
$gcpProj = $global:GCP_PROJECT_ID

Write-Host ""
Write-Host "2. Verifying Artifact Registry Repository ($repoName)..." -ForegroundColor Cyan
if ($DryRun) {
    Write-Host "  [DryRun] Would check/create docker repo $repoName in location $gcpRegion" -ForegroundColor Gray
} else {
    $repoExists = (gcloud artifacts repositories describe $repoName --location=$gcpRegion --project=$gcpProj 2>$null)
    if (-not $repoExists) {
        Write-Host "  Creating Artifact Registry docker repository $repoName..." -ForegroundColor Cyan
        gcloud artifacts repositories create $repoName --repository-format=docker --location=$gcpRegion --description="FinSight AI Production Container Images" --project=$gcpProj
    } else {
        Write-Host "  REUSING existing Artifact Registry repository: $repoName" -ForegroundColor Green
    }
}

# Step 3: Runtime Service Account
$saFullName = $global:RUNTIME_SERVICE_ACCOUNT
Write-Host ""
Write-Host "3. Verifying Runtime Service Account ($saFullName)..." -ForegroundColor Cyan
$saName = $saFullName.Split('@')[0]
if ($DryRun) {
    Write-Host "  [DryRun] Would check/create service account $saName and bind Cloud SQL Client, Vertex AI User, Secret Accessor roles" -ForegroundColor Gray
} else {
    $saExists = (gcloud iam service-accounts describe $saFullName --project=$gcpProj 2>$null)
    if (-not $saExists) {
        Write-Host "  Creating service account $saName..." -ForegroundColor Cyan
        gcloud iam service-accounts create $saName --display-name="FinSight AI Cloud Run Runtime SA" --project=$gcpProj
    } else {
        Write-Host "  REUSING existing service account: $saFullName" -ForegroundColor Green
    }

    $roles = @("roles/cloudsql.client", "roles/aiplatform.user", "roles/secretmanager.secretAccessor")
    foreach ($role in $roles) {
        Write-Host "  Granting role $role to $saFullName..." -ForegroundColor Cyan
        $saMember = "serviceAccount:" + $saFullName
        gcloud projects add-iam-policy-binding $gcpProj --member=$saMember --role=$role --condition=None 2>$null
    }
}

# Step 4: Secret Manager Metadata
Write-Host ""
Write-Host "4. Verifying Secret Manager Metadata..." -ForegroundColor Cyan
$secrets = @("SPRING_DATASOURCE_PASSWORD", "JWT_SECRET")
foreach ($sec in $secrets) {
    if ($DryRun) {
        Write-Host "  [DryRun] Would check/create secret $sec" -ForegroundColor Gray
    } else {
        $secExists = (gcloud secrets describe $sec --project=$gcpProj 2>$null)
        if (-not $secExists) {
            Write-Host "  Creating secret $sec..." -ForegroundColor Cyan
            gcloud secrets create $sec --replication-policy="automatic" --project=$gcpProj
        } else {
            Write-Host "  REUSING existing secret: $sec" -ForegroundColor Green
        }
    }
}

# Step 5: Cloud SQL Instance
$sqlInstance = $global:CLOUD_SQL_INSTANCE
Write-Host ""
Write-Host "5. Verifying Cloud SQL Instance ($sqlInstance)..." -ForegroundColor Cyan
if ($DryRun) {
    Write-Host "  [DryRun] Would check/create cost-conscious PostgreSQL 15 Cloud SQL instance $sqlInstance (tier: db-f1-micro)" -ForegroundColor Gray
} else {
    $sqlExists = (gcloud sql instances describe $sqlInstance --project=$gcpProj 2>$null)
    if (-not $sqlExists) {
        Write-Host "  Creating cost-conscious Cloud SQL instance $sqlInstance..." -ForegroundColor Cyan
        gcloud sql instances create $sqlInstance --database-version=POSTGRES_15 --tier=db-f1-micro --region=$gcpRegion --storage-size=10GB --storage-type=SSD --availability-type=single-zone --project=$gcpProj
    } else {
        Write-Host "  REUSING existing Cloud SQL instance: $sqlInstance" -ForegroundColor Green
    }
}

Write-Host ""
Write-Host "============================================================" -ForegroundColor Green
Write-Host "DATABASE CONNECTION BUDGET SUMMARY" -ForegroundColor Cyan
Write-Host "  Cloud Run Max Instances : 5" -ForegroundColor Yellow
Write-Host "  Hikari Maximum Pool Size : 5 connections/instance" -ForegroundColor Yellow
Write-Host "  Total Max Theoretical DB Connections : 25 connections" -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Green

if ($DryRun) {
    Write-Host "PHASE 5D DRY-RUN COMPLETED: PHASE_5D_AUTOMATION_READY" -ForegroundColor Yellow
} else {
    Write-Host "PHASE 5D PROVISIONING COMPLETED SUCCESSFULLY" -ForegroundColor Green
}

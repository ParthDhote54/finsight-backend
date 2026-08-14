# ============================================================
# FinSight AI — Central GCP Environment & Project Safety Guard
# ============================================================

$global:GCP_PROJECT_ID = "finsight-ai-dev"
$global:GCP_PROJECT_NUMBER = "1057979032059"
$global:GCP_REGION = "us-central1"
$global:VERTEX_LOCATION = "us-central1"
$global:ARTIFACT_REPO = "finsight-repo"
$global:CLOUD_RUN_SERVICE = "finsight-ai-backend"
$global:CLOUD_SQL_INSTANCE = "finsight-db"
$global:CLOUD_SQL_DATABASE = "finsight"
$global:RUNTIME_SERVICE_ACCOUNT = "finsight-runtime@finsight-ai-dev.iam.gserviceaccount.com"
$global:IMAGE_NAME = "finsight-ai-backend"

function Assert-TargetProject {
    param (
        [string]$ExpectedProject = $global:GCP_PROJECT_ID
    )

    $rawProject = (gcloud config get-value project 2>$null)
    $configuredProject = if ($rawProject) { $rawProject.ToString().Trim() } else { "UNSET" }
    
    Write-Host "EXPECTED PROJECT  : $ExpectedProject" -ForegroundColor Cyan
    Write-Host "CONFIGURED PROJECT: $configuredProject" -ForegroundColor Yellow

    if ($configuredProject -ne $ExpectedProject) {
        Write-Host "ERROR: Configured gcloud project ($configuredProject) does NOT match target project ($ExpectedProject)." -ForegroundColor Red
        Write-Host "REFUSING TO CONTINUE TO PREVENT ACCIDENTAL PROVISIONING OF WRONG PROJECT." -ForegroundColor Red
        Write-Host "To fix: Run 'gcloud config set project $ExpectedProject' after authenticating with correct account." -ForegroundColor Yellow
        exit 1
    }
    
    Write-Host "PROJECT SAFETY CHECK PASSED: Target project matches $ExpectedProject" -ForegroundColor Green
}

# ============================================================
# FinSight AI - Cloud SQL Database, User & Secret Version Bootstrap
# ============================================================

[CmdletBinding()]
param (
    [switch]$DryRun
)

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. "$scriptDir\config.ps1"

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "FINSIGHT AI - CLOUD SQL DATABASE & SECRET BOOTSTRAP" -ForegroundColor Cyan
if ($DryRun) {
    Write-Host "[DRY-RUN MODE ENABLED: No DB or secret version mutations]" -ForegroundColor Yellow
}
Write-Host "============================================================" -ForegroundColor Cyan

Assert-TargetProject -ExpectedProject $global:GCP_PROJECT_ID

$gcpProj = $global:GCP_PROJECT_ID
$sqlInstance = $global:CLOUD_SQL_INSTANCE
$dbName = $global:CLOUD_SQL_DATABASE
$dbUser = "finsight_app_user"

# Step 1: Application Database
Write-Host ""
Write-Host "1. Verifying Application Database ($dbName)..." -ForegroundColor Cyan
if ($DryRun) {
    Write-Host "  [DryRun] Would check/create database $dbName on instance $sqlInstance" -ForegroundColor Gray
} else {
    $dbExists = (gcloud sql databases describe $dbName --instance=$sqlInstance --project=$gcpProj 2>$null)
    if (-not $dbExists) {
        Write-Host "  Creating database $dbName..." -ForegroundColor Cyan
        gcloud sql databases create $dbName --instance=$sqlInstance --project=$gcpProj
    } else {
        Write-Host "  REUSING existing database: $dbName" -ForegroundColor Green
    }
}

# Step 2: Database User
Write-Host ""
Write-Host "2. Verifying Application Database User ($dbUser)..." -ForegroundColor Cyan
if ($DryRun) {
    Write-Host "  [DryRun] Would check/create database user $dbUser on instance $sqlInstance" -ForegroundColor Gray
} else {
    $userList = (gcloud sql users list --instance=$sqlInstance --project=$gcpProj --format="value(name)" 2>$null)
    if ($userList -notcontains $dbUser) {
        Write-Host "  Database user $dbUser missing. Secure operator password required." -ForegroundColor Yellow
        $pwd = $env:SPRING_DATASOURCE_PASSWORD
        if (-not $pwd) {
            $secPwd = Read-Host -Prompt "Enter password for DB user $dbUser" -AsSecureString
            $pwd = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto([System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($secPwd))
        }
        Write-Host "  Creating database user $dbUser..." -ForegroundColor Cyan
        gcloud sql users create $dbUser --instance=$sqlInstance --password=$pwd --project=$gcpProj
    } else {
        Write-Host "  REUSING existing database user: $dbUser" -ForegroundColor Green
    }
}

# Step 3: Secret Manager Secret Versions
Write-Host ""
Write-Host "3. Verifying Secret Manager Secret Versions..." -ForegroundColor Cyan
$secrets = @("SPRING_DATASOURCE_PASSWORD", "JWT_SECRET")

foreach ($sec in $secrets) {
    if ($DryRun) {
        Write-Host "  [DryRun] Would check active secret version for $sec" -ForegroundColor Gray
    } else {
        $versions = (gcloud secrets versions list $sec --project=$gcpProj --filter="state=ENABLED" --format="value(name)" 2>$null)
        if (-not $versions) {
            Write-Host "  Secret $sec has no enabled versions. Secure operator input required." -ForegroundColor Yellow
            $val = if ($sec -eq "SPRING_DATASOURCE_PASSWORD") { $env:SPRING_DATASOURCE_PASSWORD } else { $env:JWT_SECRET }
            if (-not $val) {
                $secVal = Read-Host -Prompt "Enter secret value for $sec" -AsSecureString
                $val = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto([System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($secVal))
            }
            $val | gcloud secrets versions add $sec --data-file=- --project=$gcpProj
            Write-Host "  Secret version added for $sec" -ForegroundColor Green
        } else {
            Write-Host "  REUSING existing enabled secret version for: $sec" -ForegroundColor Green
        }
    }
}

Write-Host ""
Write-Host "============================================================" -ForegroundColor Green
if ($DryRun) {
    Write-Host "DATABASE BOOTSTRAP DRY-RUN COMPLETED: BOOTSTRAP_AUTOMATION_READY" -ForegroundColor Yellow
} else {
    Write-Host "DATABASE & SECRET BOOTSTRAP COMPLETED SUCCESSFULLY" -ForegroundColor Green
}
Write-Host "============================================================" -ForegroundColor Green

# END OF SCRIPT

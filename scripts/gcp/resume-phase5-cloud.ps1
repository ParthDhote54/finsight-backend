# ============================================================
# FinSight AI - Master Cloud-Resume Orchestrator
# ============================================================

[CmdletBinding()]
param (
    [switch]$DryRun
)

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. "$scriptDir\config.ps1"

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "FINSIGHT AI - MASTER CLOUD-RESUME ORCHESTRATOR" -ForegroundColor Cyan
if ($DryRun) {
    Write-Host "[DRY-RUN MODE ENABLED: Running full orchestration dry-run]" -ForegroundColor Yellow
}
Write-Host "============================================================" -ForegroundColor Cyan

Assert-TargetProject -ExpectedProject $global:GCP_PROJECT_ID

# Step 1: Preflight
Write-Host ""
Write-Host "---> STEP 1: GCP Access & Billing Preflight Check" -ForegroundColor Cyan
& "$scriptDir\preflight.ps1"
if ($LASTEXITCODE -ne 0 -and -not $DryRun) {
    Write-Host "STOPPING: Preflight check failed! PHASE_5_BLOCKED_AT_PREFLIGHT" -ForegroundColor Red
    exit 1
}

# Step 2: Phase 5D Provisioning
Write-Host ""
Write-Host "---> STEP 2: Phase 5D Infrastructure Provisioning" -ForegroundColor Cyan
if ($DryRun) {
    & "$scriptDir\provision-phase5d.ps1" -DryRun
} else {
    & "$scriptDir\provision-phase5d.ps1"
}
if ($LASTEXITCODE -ne 0) {
    Write-Host "STOPPING: Phase 5D Provisioning failed! PHASE_5_BLOCKED_AT_5D" -ForegroundColor Red
    exit 1
}

# Step 3: Container Build & Push
Write-Host ""
Write-Host "---> STEP 3: Container Build & Push" -ForegroundColor Cyan
if ($DryRun) {
    & "$scriptDir\build-and-push.ps1" -DryRun
} else {
    & "$scriptDir\build-and-push.ps1"
}
if ($LASTEXITCODE -ne 0) {
    Write-Host "STOPPING: Container Build/Push failed! PHASE_5_BLOCKED_AT_BUILD" -ForegroundColor Red
    exit 1
}

# Step 4: Phase 5E Private Cloud Run Deployment
Write-Host ""
Write-Host "---> STEP 4: Phase 5E Private Cloud Run Deployment" -ForegroundColor Cyan
if ($DryRun) {
    & "$scriptDir\deploy-phase5e.ps1" -DryRun
} else {
    & "$scriptDir\deploy-phase5e.ps1"
}
if ($LASTEXITCODE -ne 0) {
    Write-Host "STOPPING: Phase 5E Deployment failed! PHASE_5_BLOCKED_AT_5E" -ForegroundColor Red
    exit 1
}

# Step 5: Demo Seeder
Write-Host ""
Write-Host "---> STEP 5: Cloud Demo Data Seeder" -ForegroundColor Cyan
if ($DryRun) {
    & "$scriptDir\seed-demo-data.ps1" -DryRun
} else {
    & "$scriptDir\seed-demo-data.ps1"
}
if ($LASTEXITCODE -ne 0) {
    Write-Host "STOPPING: Cloud Demo Seeder failed! PHASE_5_BLOCKED_AT_SEEDER" -ForegroundColor Red
    exit 1
}

# Step 6: Real Cloud HTTP E2E Smoke Runner
Write-Host ""
Write-Host "---> STEP 6: Real Cloud HTTP E2E Smoke Runner" -ForegroundColor Cyan
if ($DryRun) {
    & "$scriptDir\run-cloud-e2e.ps1" -DryRun
} else {
    & "$scriptDir\run-cloud-e2e.ps1"
}
if ($LASTEXITCODE -ne 0) {
    Write-Host "STOPPING: Cloud HTTP E2E failed! PHASE_5_BLOCKED_AT_E2E" -ForegroundColor Red
    exit 1
}

# Step 7: Phase 5F Real Gemini Benchmark Launcher
Write-Host ""
Write-Host "---> STEP 7: Phase 5F Real Vertex AI Gemini Benchmark" -ForegroundColor Cyan
if ($DryRun) {
    & "$scriptDir\run-phase5f-real-benchmark.ps1" -DryRun
} else {
    & "$scriptDir\run-phase5f-real-benchmark.ps1"
}
if ($LASTEXITCODE -ne 0) {
    Write-Host "STOPPING: Phase 5F Benchmark failed! PHASE_5_BLOCKED_AT_5F" -ForegroundColor Red
    exit 1
}

# Step 8: Phase 5G Latency & Cost Measurement Analysis
Write-Host ""
Write-Host "---> STEP 8: Phase 5G Latency & Cost Measurement Analysis" -ForegroundColor Cyan
if ($DryRun) {
    & "$scriptDir\analyze-phase5g-metrics.ps1" -DryRun
} else {
    & "$scriptDir\analyze-phase5g-metrics.ps1"
}
if ($LASTEXITCODE -ne 0) {
    Write-Host "STOPPING: Phase 5G Analysis failed! PHASE_5_BLOCKED_AT_5G" -ForegroundColor Red
    exit 1
}

# Step 9: Phase 5H Observability & Security Log Check
Write-Host ""
Write-Host "---> STEP 9: Phase 5H Observability & Security Log Check" -ForegroundColor Cyan
if ($DryRun) {
    & "$scriptDir\audit-security-and-logs.ps1" -DryRun
} else {
    & "$scriptDir\audit-security-and-logs.ps1"
}
if ($LASTEXITCODE -ne 0) {
    Write-Host "STOPPING: Phase 5H Observability failed! PHASE_5_BLOCKED_AT_5H" -ForegroundColor Red
    exit 1
}

# Step 10: Final Local Deterministic Regression & Packaging
Write-Host ""
Write-Host "---> STEP 10: Final Local Deterministic Regression & Packaging" -ForegroundColor Cyan
if ($DryRun) {
    Write-Host "  [DryRun] Would execute 'mvnw.cmd clean test' and 'mvnw.cmd -DskipTests package'" -ForegroundColor Gray
} else {
    .\mvnw.cmd clean test
    if ($LASTEXITCODE -ne 0) {
        Write-Host "STOPPING: Final local regression tests failed!" -ForegroundColor Red
        exit 1
    }
    .\mvnw.cmd -DskipTests package
    if ($LASTEXITCODE -ne 0) {
        Write-Host "STOPPING: Final packaging failed!" -ForegroundColor Red
        exit 1
    }
}

Write-Host ""
Write-Host "============================================================" -ForegroundColor Green
if ($DryRun) {
    Write-Host "MASTER CLOUD RESUME ORCHESTRATOR DRY-RUN COMPLETE" -ForegroundColor Yellow
    Write-Host "STATUS: CLOUD_RESUME_AUTOMATION_READY" -ForegroundColor Yellow
    Write-Host "WAITING_FOR_GCP_ACCESS" -ForegroundColor Yellow
} else {
    Write-Host "MASTER CLOUD RESUME ORCHESTRATION COMPLETE -- ALL GATES PASSED" -ForegroundColor Green
    Write-Host "STATUS: BACKEND_RELEASE_GATE_PASS / PHASE_5_COMPLETE" -ForegroundColor Green
}
Write-Host "============================================================" -ForegroundColor Green

# END OF SCRIPT

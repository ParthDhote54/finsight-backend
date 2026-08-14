# ============================================================
# FinSight AI - Strict Real Vertex AI 65-Case Benchmark Launcher
# ============================================================

[CmdletBinding()]
param (
    [switch]$DryRun,
    [int]$Attempts = 1
)

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. "$scriptDir\config.ps1"

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "FINSIGHT AI - PHASE 5F STRICT REAL VERTEX AI BENCHMARK" -ForegroundColor Cyan
if ($DryRun) {
    Write-Host "[DRY-RUN MODE ENABLED: No live Gemini model requests will execute]" -ForegroundColor Yellow
}
Write-Host "============================================================" -ForegroundColor Cyan

Assert-TargetProject -ExpectedProject $global:GCP_PROJECT_ID

if ($DryRun) {
    Write-Host "  [DryRun] Would set RUN_PHASE5_REAL_EVAL=true and RUN_LIVE_VERTEX_TESTS=true" -ForegroundColor Gray
    Write-Host "  [DryRun] Would verify real Vertex AI provider is active (rejecting mock implementations)" -ForegroundColor Gray
    Write-Host "  [DryRun] Would execute 65 grounded benchmark evaluation cases ($Attempts attempt(s) per case)" -ForegroundColor Gray
    Write-Host "  [DryRun] Would write results to target/evaluation-reports/evaluation_report_5f_real.json & .md" -ForegroundColor Gray
    Write-Host "PHASE 5F REAL BENCHMARK LAUNCHER READY: REAL_BENCHMARK_READY" -ForegroundColor Yellow
    exit 0
}

Write-Host "Setting environment variables for STRICT REAL VERTEX EVALUATION..." -ForegroundColor Cyan
$env:RUN_PHASE5_REAL_EVAL = "true"
$env:RUN_LIVE_VERTEX_TESTS = "true"
$env:GCP_PROJECT_ID = $global:GCP_PROJECT_ID
$env:VERTEX_LOCATION = $global:VERTEX_LOCATION

Write-Host "Executing 65-case real Vertex AI benchmark suite..." -ForegroundColor Cyan
.\mvnw.cmd test "-Dtest=LiveVertexSmokeTest,Phase5FEvaluationBenchmarkTest" -Dspring.profiles.active=live

if ($LASTEXITCODE -eq 0) {
    Write-Host "PHASE 5F REAL BENCHMARK COMPLETED SUCCESSFULLY: evaluation_report_5f_real.json GENERATED" -ForegroundColor Green
} else {
    Write-Host "ERROR: Phase 5F Real Benchmark failed with exit code $LASTEXITCODE" -ForegroundColor Red
    exit 1
}

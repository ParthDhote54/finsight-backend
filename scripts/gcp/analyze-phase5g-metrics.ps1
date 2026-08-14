# ============================================================
# FinSight AI - Phase 5G Real Data Measurement Analyzer
# ============================================================

[CmdletBinding()]
param (
    [string]$ReportFile = "target/evaluation-reports/evaluation_report_5f_real.json",
    [string]$PricingSnapshotFile = "target/evaluation-input/pricing_snapshot.json",
    [switch]$DryRun
)

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. "$scriptDir\config.ps1"

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "FINSIGHT AI - PHASE 5G REAL MEASUREMENT ANALYZER" -ForegroundColor Cyan
if ($DryRun) {
    Write-Host "[DRY-RUN MODE ENABLED: No live report parsing will occur]" -ForegroundColor Yellow
}
Write-Host "============================================================" -ForegroundColor Cyan

Assert-TargetProject -ExpectedProject $global:GCP_PROJECT_ID

if ($DryRun) {
    Write-Host "  [DryRun] Would check for real benchmark report: $ReportFile" -ForegroundColor Gray
    Write-Host "  [DryRun] Would check for verified PricingSnapshot file: $PricingSnapshotFile" -ForegroundColor Gray
    Write-Host "  [DryRun] Would verify PricingSnapshot.modelId matches benchmark modelId (refusing mismatches)" -ForegroundColor Gray
    Write-Host "  [DryRun] Would calculate exact BigDecimal token costs in USD (no hardcoded USD/INR FX assumptions)" -ForegroundColor Gray
    Write-Host "  [DryRun] Would generate target/evaluation-reports/phase5g_latency_cost_analysis_real.md" -ForegroundColor Gray
    Write-Host "PHASE 5G ANALYZER AUTOMATION READY: ANALYSIS_READY" -ForegroundColor Yellow
    exit 0
}

if (-not (Test-Path $ReportFile)) {
    Write-Host "PHASE 5G STATUS: REAL_MEASUREMENTS_NOT_AVAILABLE (File '$ReportFile' does not exist)" -ForegroundColor Yellow
    Write-Host "Execute 'scripts/gcp/run-phase5f-real-benchmark.ps1' after GCP restoration to generate real report." -ForegroundColor Yellow
    exit 0
}

if (-not (Test-Path $PricingSnapshotFile)) {
    Write-Host "PHASE 5G STATUS: PRICING_NOT_VERIFIED (Pricing snapshot '$PricingSnapshotFile' does not exist)" -ForegroundColor Yellow
    Write-Host "Operator must supply a verified runtime PricingSnapshot JSON before final 5G cost gate PASS." -ForegroundColor Yellow
    exit 0
}

Write-Host "Analyzing real evaluation report and pricing snapshot..." -ForegroundColor Cyan
$env:PRICING_SNAPSHOT_FILE = $PricingSnapshotFile
$env:REAL_BENCHMARK_REPORT_FILE = $ReportFile

.\mvnw.cmd test -Dtest=Phase5GLatencyCostReliabilityTest -Dspring.profiles.active=prod

if ($LASTEXITCODE -eq 0) {
    Write-Host "PHASE 5G ANALYSIS COMPLETED SUCCESSFULLY: phase5g_latency_cost_analysis_real.md GENERATED" -ForegroundColor Green
} else {
    Write-Host "ERROR: Phase 5G analysis failed with exit code $LASTEXITCODE" -ForegroundColor Red
    exit 1
}

# FINSIGHT AI — PHASE 5 CLOUD RESUME OPERATOR RUNBOOK

## Objective

This runbook provides the exact steps to authenticate and execute the automated cloud resume workflow for **FinSight AI** once Google Cloud account access to target project `finsight-ai-dev` is restored.

---

## 1. Environment & Target Specifications

```text
Target Project ID: finsight-ai-dev
Target Project Number: 1057979032059
Default Region: us-central1
Vertex AI Location: us-central1
Artifact Registry: finsight-repo
Cloud Run Service: finsight-ai-backend (PRIVATE)
Cloud SQL Instance: finsight-db (PostgreSQL 15 + Java Socket Factory)
Runtime Service Account: finsight-runtime@finsight-ai-dev.iam.gserviceaccount.com
```

---

## 2. Authentication Recovery (Operator Action Required)

Run the following commands in PowerShell after receiving access:

```powershell
# 1. Authenticate gcloud CLI
gcloud auth login

# 2. Authenticate Application Default Credentials (for local Java Vertex AI runner)
gcloud auth application-default login

# 3. Set active project to target project
gcloud config set project finsight-ai-dev
```

> **IMPORTANT**: Never download or use service-account JSON keys. Local development uses `gcloud auth application-default login`; Cloud Run production uses the attached runtime service account.

---

## 3. Verify Project & Billing Access

Run the preflight check script:

```powershell
.\scripts\gcp\preflight.ps1
```

Expected Output:
```text
ACTIVE GCP ACCOUNT: <operator_email@example.com>
EXPECTED PROJECT  : finsight-ai-dev
CONFIGURED PROJECT: finsight-ai-dev
PROJECT SAFETY CHECK PASSED: Target project matches finsight-ai-dev
PROJECT ACCESS OK: Verified project finsight-ai-dev (1057979032059)
BILLING OK: Billing is active on project finsight-ai-dev
GCP PREFLIGHT STATUS: ALL_CHECKS_PASSED
```

---

## 4. One-Command Cloud Resume Execution

To execute the entire Phase 5D → 5H deployment, benchmarking, evaluation measurement, observability, and release verification:

```powershell
.\scripts\gcp\resume-phase5-cloud.ps1
```

### Dry-Run Mode (Validation without mutating cloud resources)

```powershell
.\scripts\gcp\resume-phase5-cloud.ps1 -DryRun
```

---

## 5. Automated Workflow Sequence

The master orchestrator automatically executes:

1. `preflight.ps1`: Verifies active account, project ID `finsight-ai-dev`, and billing status.
2. `provision-phase5d.ps1`: Idempotently provisions Artifact Registry, Service Account, Secret Manager metadata, and Cloud SQL instance.
3. `build-and-push.ps1`: Builds multi-stage production Docker container and pushes to Artifact Registry.
4. `deploy-phase5e.ps1`: Deploys PRIVATE Cloud Run service (`--no-allow-unauthenticated`) with Cloud SQL Java Connector and Secret Manager bindings.
5. `seed-demo-data.ps1`: Explicitly seeds 188 grounded transactions across May/June 2026 for evaluation users.
6. `run-cloud-e2e.ps1`: Runs real Cloud Run HTTP E2E smoke tests (health, aggregate, flagship, balance provenance, safe refusal, tenant isolation).
7. `run-phase5f-real-benchmark.ps1`: Runs strict 65-case real Vertex AI Gemini benchmark (`gemini-2.5-flash-lite`).
8. `analyze-phase5g-metrics.ps1`: Parses real token usage, computes latency P50/P95 metrics, applies `PricingSnapshot`, and projects monthly costs.
9. `audit-security-and-logs.ps1`: Formulates Cloud Logging query filters and scans logs for unredacted JWT/credential leaks.
10. Final deterministic regression (`mvnw clean test`) and production jar packaging (`mvnw -DskipTests package`).

---

## 6. Verification & Artifact Outputs

After execution, the following authoritative artifacts will be generated in `target/evaluation-reports/`:

- `phase5e_cloud_e2e.json` — Real HTTP Cloud Run E2E smoke test results.
- `evaluation_report_5f_real.json` & `.md` — 65-case real Vertex AI benchmark results.
- `phase5g_latency_cost_analysis_real.md` — Measured latency distributions, token metrics, and operational cost projections.
- Master release verdict: `BACKEND_RELEASE_GATE_PASS / PHASE_5_COMPLETE`.

# FinSight AI Deployment Runbook

## Constants

```powershell
$PROJECT_ID="finsight-ai-dev"
$REGION="asia-south1"
$SERVICE="finsight-backend"
$AR_REPO="finsight-repo"
$SQL_INSTANCE="finsight-db"
$DB_NAME="finsight"
$DB_USER="finsight_app"
$CLOUD_SQL_CONNECTION_NAME="$PROJECT_ID`:$REGION`:$SQL_INSTANCE"
```

## Enable APIs

```powershell
gcloud services enable run.googleapis.com cloudbuild.googleapis.com artifactregistry.googleapis.com sqladmin.googleapis.com secretmanager.googleapis.com aiplatform.googleapis.com firebase.googleapis.com --project=$PROJECT_ID
```

Success: command exits `0`.
If it fails: use an account with Project Owner/Editor or Service Usage Admin.

## Cloud SQL

```powershell
gcloud sql instances create $SQL_INSTANCE --project=$PROJECT_ID --region=$REGION --database-version=POSTGRES_16 --tier=db-f1-micro --storage-type=SSD --storage-size=10GB --availability-type=ZONAL --no-backup
gcloud sql databases create $DB_NAME --instance=$SQL_INSTANCE --project=$PROJECT_ID
gcloud sql users create $DB_USER --instance=$SQL_INSTANCE --project=$PROJECT_ID --password="$(gcloud secrets versions access latest --secret=db-password --project=$PROJECT_ID)"
```

Success: `gcloud sql instances describe finsight-db` shows `RUNNABLE`.
If it fails: wait 2 minutes and rerun the failed command.

## Artifact Registry

```powershell
gcloud artifacts repositories create $AR_REPO --repository-format=docker --location=$REGION --description="FinSight containers" --project=$PROJECT_ID
```

If already exists: continue.

## IAM For Cloud Run and Cloud Build

```powershell
$PROJECT_NUMBER="$(gcloud projects describe $PROJECT_ID --format='value(projectNumber)')"
$RUN_SA="$PROJECT_NUMBER-compute@developer.gserviceaccount.com"
$BUILD_SA="$PROJECT_NUMBER@cloudbuild.gserviceaccount.com"

gcloud projects add-iam-policy-binding $PROJECT_ID --member="serviceAccount:$RUN_SA" --role="roles/cloudsql.client"
gcloud projects add-iam-policy-binding $PROJECT_ID --member="serviceAccount:$RUN_SA" --role="roles/secretmanager.secretAccessor"
gcloud projects add-iam-policy-binding $PROJECT_ID --member="serviceAccount:$RUN_SA" --role="roles/aiplatform.user"
gcloud projects add-iam-policy-binding $PROJECT_ID --member="serviceAccount:$BUILD_SA" --role="roles/run.admin"
gcloud projects add-iam-policy-binding $PROJECT_ID --member="serviceAccount:$BUILD_SA" --role="roles/iam.serviceAccountUser"
```

Success: each command prints updated IAM policy.
If it fails: run from a Project Owner account.

## Backend Build and Deploy

```powershell
gcloud builds submit . --config=cloudbuild.yaml --project=$PROJECT_ID --substitutions=_REGION=$REGION,_SERVICE=$SERVICE,_REPOSITORY=$AR_REPO,_CLOUD_SQL_CONNECTION_NAME=$CLOUD_SQL_CONNECTION_NAME,_DB_NAME=$DB_NAME,_DB_USER=$DB_USER,_FRONTEND_ORIGIN=http://localhost:5173
```

After Firebase Hosting is live, update CORS:

```powershell
gcloud run deploy $SERVICE `
  --region=$REGION `
  --project=$PROJECT_ID `
  --update-env-vars="FINSIGHT_FRONTEND_ORIGIN=https://$PROJECT_ID.web.app"
```

Success: service URL prints.
If it fails: run `gcloud run services logs read $SERVICE --region=$REGION --project=$PROJECT_ID --limit=80`.

## Health Check

```powershell
$BACKEND_URL="$(gcloud run services describe $SERVICE --region=$REGION --project=$PROJECT_ID --format='value(status.url)')"
curl "$BACKEND_URL/actuator/health"
```

Success: `{"status":"UP"}`.
If it fails: check Cloud SQL connection name, secrets, and logs.

## Frontend Deploy

```powershell
cd frontend
"VITE_API_BASE_URL=$BACKEND_URL" | Set-Content .env.production
npm.cmd ci
npm.cmd run build
npx firebase-tools deploy --only hosting --project $PROJECT_ID
```

Success: Firebase Hosting URL prints.
If it fails: run `npx firebase-tools login` then retry deploy.

## Rollback

Backend:

```powershell
gcloud run revisions list --service=$SERVICE --region=$REGION --project=$PROJECT_ID
gcloud run services update-traffic $SERVICE --region=$REGION --project=$PROJECT_ID --to-revisions=REVISION_NAME=100
```

Frontend:

```powershell
cd frontend
npx firebase-tools hosting:releases:list --project $PROJECT_ID
npx firebase-tools hosting:rollback --project $PROJECT_ID
```

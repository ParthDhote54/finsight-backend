# FinSight AI Deployment

FinSight AI uses a simple production GCP architecture:

- React/Vite frontend on Firebase Hosting
- Spring Boot backend on Cloud Run
- PostgreSQL on Cloud SQL
- Docker images in Artifact Registry
- Secrets in Secret Manager
- Database schema managed only by Flyway
- Gemini/Vertex AI configured through production environment variables

Runtime settings:

- Region: `asia-south1`
- Cloud Run: `512Mi`, `1 CPU`, `min-instances=0`, `max-instances=3`, `timeout=60s`
- HikariCP: `maximum-pool-size=3`, `minimum-idle=0`
- Spring profile: `prod`
- JPA DDL: `validate`

Production safeguards:

- No `ddl-auto=update`
- No secrets committed to GitHub
- Cloud Run uses Cloud SQL integration
- JWT secret and DB password are injected from Secret Manager
- Frontend API URL is build-time configured with `VITE_API_BASE_URL`

Main verification flow:

1. `GET /actuator/health` returns `UP`
2. User signup succeeds
3. Login returns JWT
4. Account and transaction creation persist to Cloud SQL
5. Dashboard analytics read from database-backed records
6. AI endpoint degrades gracefully when DB-backed financial evidence is unresolved

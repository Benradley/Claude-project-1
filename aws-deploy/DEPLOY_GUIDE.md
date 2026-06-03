# AWS Deployment Guide — Drone Analytics

## Architecture overview

```
Visitor's browser
       │  HTTPS
       ▼
CloudFront distribution  ──── /api/* ────▶  Elastic Beanstalk  (Spring Boot, port 5000)
       │                                           │
       └──── /* ────────────▶  S3 bucket           ▼
                              (React SPA)   RDS PostgreSQL  (free tier, persistent)
```

| Layer | AWS service | Cost (12-month free tier) |
|-------|-------------|--------------------------|
| React SPA | S3 + CloudFront | $0 (5 GB S3, 1 TB CF/month) |
| Spring Boot API | Elastic Beanstalk (t3.micro) | $0 (750 hrs EC2/month) |
| Database | RDS PostgreSQL (db.t3.micro) | $0 (750 hrs RDS/month) |
| **Total** | | **$0/month for 12 months** |

After 12 months: ~$15–20/month (EC2 t3.micro + RDS t3.micro).

---

## Why these choices

**S3 + CloudFront instead of keeping nginx/Docker:**
The React build is 100% static files after `npm run build`. S3 stores them; CloudFront serves them over HTTPS globally with a CDN. No server needed. This is simpler, cheaper, and faster than running a container just to serve static files.

**Elastic Beanstalk instead of EC2 or ECS:**
EB takes a plain JAR file (already produced by `mvn package`) and handles the EC2 instance, load balancer, and deployment rollouts through a web console. No Docker, no server management. ECS would add Docker complexity; raw EC2 would require manual Java setup.

**RDS PostgreSQL instead of H2 file:**
H2 file data lives on the EB EC2 instance. If that instance is ever replaced (auto-scaling, AMI update, free-tier rotation) all user accounts and flight history disappear. RDS is a separate managed service — data persists independently of the app server. The free tier covers it for a year.

**CloudFront `/api/*` behaviour instead of hardcoding the EB URL in the frontend:**
The React app uses relative paths (`/api/auth/login`, `/api/flights/analyze`, etc.). CloudFront routes those calls to EB transparently. No URL ever appears in the frontend source code — the same build works in development (Vite proxy → localhost:8080) and production (CloudFront → EB).

---

## One-time AWS setup (web console only)

### Step 1 — AWS free-tier account
1. Go to https://aws.amazon.com → **Create a Free Account**
2. Choose **Free Tier** — a credit card is required but won't be charged within limits
3. Set your preferred region (e.g. **US East (N. Virginia) — us-east-1**). Use the same region for every service below.

---

### Step 2 — IAM deploy user (for GitHub Actions)
1. **IAM** → **Users** → **Create user**
2. Username: `drone-analytics-deploy`
3. **Attach policies directly** — add these managed policies:
   - `AmazonS3FullAccess`
   - `CloudFrontFullAccess`
   - `AdministratorAccess-AWSElasticBeanstalk`
4. Create the user, then open it → **Security credentials** tab → **Create access key**
5. Choose **Application running outside AWS** → copy both the **Access key ID** and **Secret access key** — you'll need them in Step 8

---

### Step 3 — S3 bucket for the React app
1. **S3** → **Create bucket**
2. Name: `drone-analytics-frontend` *(must be globally unique — append your initials if taken)*
3. Region: same as Step 1
4. **Uncheck** "Block all public access" and confirm
5. Create the bucket, then open it:
   - **Properties** → **Static website hosting** → Enable → Index: `index.html` → Error: `index.html` → Save
   - **Permissions** → **Bucket policy** → paste the policy below (replace `YOUR-BUCKET-NAME`):

```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Principal": "*",
    "Action": "s3:GetObject",
    "Resource": "arn:aws:s3:::YOUR-BUCKET-NAME/*"
  }]
}
```

---

### Step 4 — S3 bucket for EB deployment artefacts
1. **S3** → **Create bucket**
2. Name: `drone-analytics-deploy` *(+ your initials if needed)*
3. Same region; leave "Block all public access" **checked**
4. Create bucket — no further config needed

---

### Step 5 — RDS PostgreSQL database
1. **RDS** → **Create database**
2. **Standard create** → **PostgreSQL**
3. Template: **Free tier**
4. Settings:
   - DB instance identifier: `drone-analytics-db`
   - Master username: `droneuser`
   - Master password: choose a strong password and **write it down**
5. Instance: `db.t3.micro` (pre-selected by free tier)
6. Storage: 20 GiB gp2 (default)
7. **Connectivity**:
   - VPC: default
   - Public access: **No** *(EB and RDS will be in the same VPC)*
   - VPC security group: **Create new** → name it `drone-rds-sg`
8. Additional configuration → Initial database name: `droneanalytics`
9. Create database — takes ~5 minutes
10. Once available, click the DB identifier → copy the **Endpoint** (looks like `drone-analytics-db.xxxx.us-east-1.rds.amazonaws.com`)

> **Security group rule** (required for EB → RDS traffic):
> 1. Go to **EC2** → **Security Groups** → find `drone-rds-sg`
> 2. **Inbound rules** → **Edit** → **Add rule**:
>    - Type: `PostgreSQL` (port 5432)
>    - Source: `0.0.0.0/0` *(or restrict to the EB security group for tighter security)*
> 3. Save

---

### Step 6 — Elastic Beanstalk application + environment

#### 6a — Create the EB application
1. **Elastic Beanstalk** → **Create application**
2. Application name: `drone-analytics`

#### 6b — Create the EB environment
1. Inside the application → **Create environment**
2. Tier: **Web server environment**
3. Environment name: `drone-analytics-prod`
4. Platform: **Java** → **Corretto 17** (latest version)
5. Application code: **Sample application** *(GitHub Actions will deploy the real code)*
6. Click through the wizard keeping defaults → **Submit**
7. Wait ~5 minutes until the environment shows **Health: Ok**

#### 6c — Set environment variables
**Configuration** → **Updates, monitoring, and logging** (or **Software**) → **Edit** → scroll to **Environment properties**:

| Key | Value |
|-----|-------|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `JWT_SECRET` | *(random 32+ char string — generate at https://www.random.org/strings/)* |
| `DB_HOST` | *(RDS endpoint from Step 5 — e.g. `drone-analytics-db.xxxx.us-east-1.rds.amazonaws.com`)* |
| `DB_NAME` | `droneanalytics` |
| `DB_USER` | `droneuser` |
| `DB_PASSWORD` | *(the RDS master password from Step 5)* |

Click **Apply** and wait for the update.

#### 6d — Note the EB environment URL
On the environment dashboard you'll see a URL like:
`http://drone-analytics-prod.eba-xxxxxxxx.us-east-1.elasticbeanstalk.com`
Copy it — you need it in Step 7.

---

### Step 7 — CloudFront distribution

#### Create the distribution
1. **CloudFront** → **Create a CloudFront distribution**

#### Origin 1 — S3 (default, React SPA)
- **Origin domain**: select `drone-analytics-frontend` from the dropdown
- When prompted "Use website endpoint?", click **Use website endpoint**
- Name: `S3-drone-frontend`

#### Viewer settings
- Viewer protocol policy: **Redirect HTTP to HTTPS**
- Allowed HTTP methods: **GET, HEAD, OPTIONS, PUT, POST, PATCH, DELETE**

#### Origin 2 — Elastic Beanstalk (API)
1. Scroll to **Origins** → **Add origin**
2. Origin domain: paste the EB URL from Step 6d *(without `http://`)*
3. Protocol: **HTTP only**
4. Name: `EB-drone-backend`

#### Cache behaviour for `/api/*`
1. **Behaviors** → **Create behavior**
2. Path pattern: `/api/*`
3. Origin: `EB-drone-backend`
4. Viewer protocol policy: **Redirect HTTP to HTTPS**
5. Allowed HTTP methods: **GET, HEAD, OPTIONS, PUT, POST, PATCH, DELETE**
6. Cache policy: **CachingDisabled** *(API responses must never be cached)*
7. Origin request policy: **AllViewer** *(forwards all headers, including Authorization)*

#### Custom error pages (React Router support)
Without these, refreshing any page other than `/` returns a 403 from S3.

1. **Error pages** → **Create custom error response**:
   - HTTP error code: `403` → Response page path: `/index.html` → HTTP response code: `200`
2. Add another:
   - HTTP error code: `404` → Response page path: `/index.html` → HTTP response code: `200`

#### Finish
1. **Create distribution** — takes ~10 minutes to deploy globally
2. Once Status shows **Enabled**, copy the **Distribution domain name**:
   `https://d1a2b3c4xyz.cloudfront.net`

---

### Step 8 — Add your CloudFront URL to the backend CORS config

1. Back in **Elastic Beanstalk** → your environment → **Configuration** → **Software** → **Edit**
2. Add one more environment property:

| Key | Value |
|-----|-------|
| `CORS_ALLOWED_ORIGINS` | `https://d1a2b3c4xyz.cloudfront.net` *(your actual CF domain)* |

3. **Apply** and wait for the update

---

### Step 9 — Add GitHub Secrets

In your GitHub repo → **Settings** → **Secrets and variables** → **Actions** → **New repository secret**:

| Secret | Value |
|--------|-------|
| `AWS_ACCESS_KEY_ID` | Access key ID from Step 2 |
| `AWS_SECRET_ACCESS_KEY` | Secret access key from Step 2 |
| `AWS_REGION` | e.g. `us-east-1` |
| `S3_BUCKET_NAME` | `drone-analytics-frontend` |
| `CLOUDFRONT_DISTRIBUTION_ID` | CloudFront distribution ID (e.g. `E1A2B3C4D5E6F7`) |
| `EB_DEPLOY_BUCKET` | `drone-analytics-deploy` |
| `EB_APP_NAME` | `drone-analytics` |
| `EB_ENV_NAME` | `drone-analytics-prod` |

---

### Step 10 — Trigger the first deployment

Push any commit to `main` — or create an empty trigger commit:

```
git add -A
git commit -m "configure for AWS deployment"
git push origin main
```

Go to **GitHub → Actions** tab. Two jobs will run in parallel:
- **Frontend → S3 + CloudFront** (~2–3 min)
- **Backend → Elastic Beanstalk** (~5–8 min)

When both are green, open your CloudFront URL: `https://d1a2b3c4xyz.cloudfront.net`

---

## Files changed vs the original project

| File | What changed | Why |
|------|-------------|-----|
| `drone-dashboard/vite.config.js` | `base: '/'` (was `'/droneproject'` for Cloudflare) | CloudFront serves at the distribution root — no sub-path needed |
| `drone-ingestion/src/main/java/.../CorsConfig.java` | Reads origins from `CORS_ALLOWED_ORIGINS` env var | CloudFront URL not known at build time — set it after creating the distribution |
| `drone-ingestion/Procfile` | New file — `web: java -jar ... --server.port=5000 --spring.profiles.active=prod` | EB Java SE platform requires a Procfile to know the start command |
| `.github/workflows/deploy-aws.yml` | New workflow — builds and deploys both services on push to `main` | Automates the entire deployment so no terminal commands are needed after initial setup |

**Unchanged** (local dev still works identically):
- `application.properties` — H2 dev database
- `application-prod.properties` — PostgreSQL via env vars (used by EB)
- All frontend API files — relative `/api/` paths work via both Vite proxy and CloudFront behaviour
- `docker-compose.yml` — still works for local full-stack dev

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---------|-------------|-----|
| Login returns 403 | `CORS_ALLOWED_ORIGINS` missing in EB | Add it (Step 8) — must match your exact CloudFront domain |
| API calls return 502/503 | EB environment unhealthy | EB → Logs → check for missing env var (usually `JWT_SECRET` or `DB_*`) |
| Page refresh returns 403 | CloudFront custom error pages not set | Re-check Step 7 custom error responses (403 → `/index.html`) |
| White screen on load | `base` path mismatch | Confirm `vite.config.js` has `base: '/'` and rebuild |
| Database connection refused | RDS security group not open | Add inbound rule for port 5432 in `drone-rds-sg` (Step 5) |
| EB deployment stuck "Updating" | JAR too large / timeout | Increase EB deployment timeout in Configuration → Rolling updates |
| GitHub Actions fails on `aws` CLI | Secrets not set | Verify all 8 secrets are present in repo Settings → Secrets |

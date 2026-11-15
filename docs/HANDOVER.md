# Handover for the hosting company

This app is ready to deploy as containers. This page covers what you need to run it, which settings to provide, and what the app already handles versus what's yours.

## 1. What you're deploying

| Component | Image | Port | Notes |
| --- | --- | --- | --- |
| Website | `frontend/Dockerfile` (nginx, runs as non-root) | 8080 | Serves the React site and forwards `/api/*` to the API. Set `BACKEND_URL`. |
| API | `backend/Dockerfile` (Java 21 JRE, runs as non-root) | 8080 | Stateless. Can run more than one instance. Health: `/actuator/health/readiness` and `/actuator/health/liveness`. |
| Database | PostgreSQL 16 (managed service recommended, e.g. RDS) | 5432 | Schema is created and upgraded automatically by Flyway when the API starts. |

Traffic flow: browser → your load balancer (HTTPS) → website container → API container → PostgreSQL. Only the website needs to be public. The API should only be reachable from the website container (or through the same load balancer under `/api`).

**Important:** the website and API must be served from the **same domain**. Login uses a first-party cookie, so the site and `/api` need the same origin. The provided nginx config already does this.

## 2. Settings (environment variables)

### API

| Variable | Required | Example | Purpose |
| --- | --- | --- | --- |
| `DATABASE_URL` | yes | `jdbc:postgresql://db.internal:5432/summit?sslmode=require` | JDBC connection string |
| `DATABASE_USERNAME` | yes | `summit` | Database user (needs to create tables on first start) |
| `DATABASE_PASSWORD` | yes | (secret) | Store in your secrets manager |
| `APP_PUBLIC_BASE_URL` | yes | `https://summit.example.ie` | Used in password-reset links |
| `APP_ADMIN_EMAILS` | yes | `organiser@example.ie` | Comma-separated. These accounts become platform admins when they register or log in. |
| `COOKIE_SECURE` | no | `true` (default) | Keep `true` in production. HTTPS is required. |
| `DATABASE_POOL_SIZE` | no | `10` | Max DB connections per API instance. Keep (instances × pool size) under the database's connection limit. |
| `SESSION_DAYS` | no | `30` | How long a login lasts |
| `EMAIL_WEBHOOK_URL` | recommended | `https://…/send-email` | See section 5 |
| `EMAIL_WEBHOOK_TOKEN` | with the above | (secret) | Sent as `Authorization: Bearer …` |
| `RETENTION_DELETE_INACTIVE_USERS_DAYS` | no | `120` | GDPR: deletes accounts unused for this many days. `0` = off. Agree the number with the organisers. |
| `APP_PRIVACY_NOTICE_VERSION` | no | `2026-11` | Recorded with each user's consent. Change it when the privacy notice changes. |
| `APP_SEED_DEMO_DATA` | no | `false` | **Must stay false in production.** |
| `JAVA_OPTS` | no | `-XX:MaxRAMPercentage=75` | JVM options |

### Website

| Variable | Required | Example |
| --- | --- | --- |
| `BACKEND_URL` | yes | `http://summit-api.internal:8080` |

## 3. Who handles what

| Area | Already in the app | Hosting company |
| --- | --- | --- |
| HTTPS / TLS | Secure cookies, HSTS-friendly | Certificates, TLS termination, HTTP→HTTPS redirect, HSTS header |
| Login security | Password hashing (bcrypt), DB-backed sessions in HttpOnly SameSite cookies, CSRF protection on every change, lockout after 10 failed logins per email per 15 minutes, generic error messages | WAF / rate limiting in front of `/api/v1/auth/*`; bot protection if needed |
| Access control | Every admin endpoint checks the user's role for that specific event; tested | — |
| Web security headers | CSP, X-Frame-Options, nosniff, Referrer-Policy (nginx), plus CSP on API responses | Optionally add HSTS at the load balancer |
| Input handling | Validation on every request; only `http(s)` links accepted for images and websites; React escapes all text | — |
| Secrets | None in the code or images; all through environment variables | Secrets manager, rotation |
| Database | Schema migrations, constraints, cascading deletes | Managed PostgreSQL, **automated backups and point-in-time recovery**, encryption at rest, private networking |
| Logs | No passwords, tokens or request bodies are logged | Log collection and retention |
| Monitoring | Health endpoints | Uptime checks, alerts, dashboards |
| Scaling | Stateless API, ETags on public data (phones get cheap 304s), polling every 30 s | Instance sizing, autoscaling if wanted |
| Dependency updates | — | Image and dependency patching; GitHub Dependabot can be enabled on the repo |

## 4. Expected load

Chat and voting use polling (every 8 and 30 seconds), not WebSockets, so no sticky sessions or socket support are needed at the load balancer.


A few hundred attendees on one day. The heaviest moment is the morning, when most phones open the agenda at once, plus each open app checking for updates every 30 seconds. Public responses carry ETags, so unchanged data returns a small 304.

Suggested starting point: 2 API instances (for resilience, not load) with 1 GB RAM each, and the smallest production-grade PostgreSQL. Please run a quick load test (for example k6) in the week before the event at 2–3× the expected attendee count.

## 5. Email (password reset)

The app can send password-reset emails through a simple webhook, so it isn't tied to one email provider. When `EMAIL_WEBHOOK_URL` is set, the API sends:

```
POST <EMAIL_WEBHOOK_URL>
Authorization: Bearer <EMAIL_WEBHOOK_TOKEN>
Content-Type: application/json

{"to": "person@example.com", "subject": "Reset your password", "text": "…"}
```

On AWS, a small Lambda behind a function URL or API Gateway that calls SES works well. If no webhook is configured, reset emails are not sent (a warning is logged without the email's contents), and people who forget their password can't reset it themselves.

## 6. GDPR items to confirm with the organisers

- The organisers are the data controller. They need to fill in and approve the privacy notice. It's a template in `frontend/src/pages/MiscPages.tsx` (`PrivacyPage`): every `[bracketed]` item must be completed.
- Host the database and backups in an EU region, and accept the provider's Data Processing Agreement.
- Agree a retention period and set `RETENTION_DELETE_INACTIVE_USERS_DAYS`.
- Users can export their data (`GET /api/v1/me/export`) and delete their account (`DELETE /api/v1/me`) themselves.
- Decide who reports a personal data breach to the Data Protection Commission (72-hour rule).

## 7. Known limitations (current version)

- **Google and LinkedIn login are not included yet.** They were left out of this first version because the build environment couldn't download the OAuth libraries. They can be added with Spring Security's OAuth2 client, and the session design already supports it.
- **Images are links, not uploads.** Organisers paste `https://` links for speaker photos and partner logos. Uploads (e.g. to S3) can be added later.
- **The Docker images and nginx config were not test-run** in the development environment, because it couldn't download base images. The application itself was fully tested. Please run `docker compose up --build` once as a smoke test.
- **Login lockout is per API instance** (in memory). With several instances, a WAF rate limit on `/api/v1/auth/login` is the stronger protection.

## 8. API overview

All endpoints are under `/api/v1`.

| Area | Endpoints | Who |
| --- | --- | --- |
| Auth | `GET /auth/csrf`, `GET /auth/session`, `POST /auth/register`, `/auth/login`, `/auth/logout`, `/auth/password/forgot`, `/auth/password/reset` | Anyone |
| Public | `GET /events`, `/events/{slug}`, `/events/{slug}/sessions`, `/rooms`, `/speakers`, `/sponsors`, `/announcements`, `GET /sessions/{id}`, `GET /speakers/{id}` | Anyone (published events only) |
| Me | `GET/PATCH/DELETE /me`, `PUT /me/password`, `PUT /me/consents`, `GET /me/export`, `GET /me/schedule?event=`, `PUT/DELETE /me/schedule/{sessionId}` | Logged in |
| Hackathon (public) | `GET /events/{slug}/teams`, `GET /teams/{id}`, `GET /events/{slug}/voting` | Anyone (member names only when logged in) |
| Hackathon (me) | `GET/PUT/DELETE /me/hackathon?event=`, `POST/PUT /me/team?event=`, `POST /me/team/join`, `POST /me/team/leave?event=`, `PUT/DELETE /me/vote?event=` | Logged in |
| Chat | `GET /events/{slug}/chat[?after=]`, `POST /me/chat?event=`, `DELETE /me/chat/{id}`, `PUT/DELETE /me/announcements/{id}/reactions/{HEART|THUMBS_UP|PARTY|CLAP}` | Logged in; event must have chat switched on |
| Admin | `/admin/events[/{id}]` plus `/sessions`, `/rooms`, `/speakers`, `/sponsors`, `/announcements`, `/members`, `/teams` (+ `/auto-sort`, `/{id}/members`), `/participants`, `/votes`, `/chat/{id}` under each event | Organisers of that event; creating/deleting events needs a platform admin |

Every request that changes something must send the `X-XSRF-TOKEN` header matching the `XSRF-TOKEN` cookie. The frontend does this automatically.

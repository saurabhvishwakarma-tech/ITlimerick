# IT Limerick Summit app

A mobile-friendly web app for the IT Limerick Summit. Attendees see the agenda, speakers and partners, save sessions to "My schedule", and get live updates on the day. Organisers manage everything from an admin panel. It's built to be reused for future events.

- **Backend:** Java 21, Spring Boot 4.1, PostgreSQL, Flyway (`backend/`)
- **Frontend:** React 19, TypeScript, Vite, TanStack Query (`frontend/`)
- **Deployment:** two Docker images (API and website) plus PostgreSQL. Hosting, HTTPS and infrastructure security are handled by the hosting company. See [docs/HANDOVER.md](docs/HANDOVER.md).

## What's in it

| For attendees | For organisers |
| --- | --- |
| Agenda with an "On now" marker in venue time | Create and publish events (draft until published) |
| Session, speaker and partner pages | Add sessions, speakers, partners and rooms |
| "My schedule" bookmarks: work without an account, sync after login | Post live updates; urgent ones show as a banner |
| Live updates feed, refreshed every 30 seconds | Add other organisers, speakers and sponsors to an event |
| Works on poor Wi-Fi: last copy of the agenda is kept on the phone | Hidden speaker profiles until approved |
| Account page: change details, download data, delete account | |
| **Hackathon (when switched on):** browse teams and projects, start a team and share its code, join with a code, or ask to be placed | **Team sorter:** places everyone waiting into teams of even size; move people by hand |
| **Vote** for the project of the day: one vote each, changeable until voting closes, not for your own team | Set voting times, shortlist teams for the ballot, see live counts, choose when results go public |
| **Chat** with everyone at the event, and react to announcements | Turn chat on or off per event; remove any message |

## Run it locally

### Option A: everything in Docker (needs internet for the first build)

```bash
cp .env.example .env        # set APP_ADMIN_EMAILS to your email
docker compose up --build
```

Open http://localhost:8080, create an account with the email you put in `APP_ADMIN_EMAILS`, and you'll see "Organiser tools" on your account page. With `APP_SEED_DEMO_DATA=true`, an empty database gets a fictional sample event.

### Option B: run the parts separately (for development)

1. Start PostgreSQL and create a database and user called `summit` (password `summit`), plus a `summit_test` database for the tests.
2. Backend:
   ```bash
   cd backend
   APP_ADMIN_EMAILS=you@example.com COOKIE_SECURE=false APP_SEED_DEMO_DATA=true mvn spring-boot:run
   ```
3. Frontend (proxies `/api` to the backend on port 8080):
   ```bash
   cd frontend
   npm install
   npm run dev
   ```
4. Open http://localhost:5173.

## Tests

```bash
cd backend && mvn verify        # 19 API tests against the summit_test PostgreSQL database
cd frontend && npm test         # timezone helper tests
cd frontend && npm run build    # type-check and production build
```

The backend tests cover: public pages hide drafts and unpublished speakers, ETag caching, CSRF protection, registration/login/logout, login lockout after repeated failures, organiser-only admin access, organisers can't edit other events' data, input validation, bookmarks, GDPR export and account deletion, one-time password reset links, team creation and joining (with size limits and lead hand-over), the team sorter, voting rules and results, chat moderation and reactions, and clean-up of hackathon data when an account is deleted.

GitHub Actions runs all of this on every push (`.github/workflows/ci.yml`).

## Project layout

```
backend/
  src/main/java/ie/itlimerick/summit/
    account/        users, roles per event, consent records, GDPR export/delete
    agenda/         sessions and rooms
    announcement/   live updates
    common/         errors, tokens, shared validation
    config/         settings (AppProperties), ETag filter
    email/          password-reset email sender (webhook)
    chat/           attendee chat and reactions on updates
    event/          events
    hackathon/      participants, teams, team sorter, voting
    maintenance/    nightly clean-up, optional demo data
    schedule/       "My schedule" bookmarks
    security/       login sessions, CSRF, permissions
    speaker/        speakers
    sponsor/        partners
  src/main/resources/db/migration/   database schema (Flyway)
frontend/
  src/api/          API client and data hooks
  src/auth/         login state
  src/components/   layout and shared UI
  src/lib/          time zones, offline copy, bookmarks
  src/pages/        public pages, account, admin panel
  nginx/            web server config used in the Docker image
docs/
  HANDOVER.md       for the hosting company
  RUNBOOK.md        for event day
```

## Design decisions

- **One backend, one database.** Simple to run and monitor for a volunteer team. Code is split by feature so it can grow.
- **Login is built in.** Email and password, with database-backed sessions in an HttpOnly cookie. Sessions survive restarts and work with several server instances. There's no third-party login service to depend on.
- **Same origin.** nginx serves the site and forwards `/api` to the backend, so cookies are first-party and no CORS setup is needed.
- **Minimal personal data.** Browsing needs no account. Accounts hold only email, name, password hash, bookmarks and consent records.

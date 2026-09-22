# Multi-Platform Sales, Customer & Profit Tracker

React (Vite) + Spring Boot 3 + MySQL. See `docs/` for the ER diagram and requirements.

## Run locally (dev)

Prerequisites: Java 17, Maven, Node 20+, Docker.

```bash
cp .env.example .env                                   # then edit the secrets
docker compose -f docker-compose.dev.yml --env-file .env up -d   # MySQL

# backend (http://localhost:8080) - Flyway creates the schema on start
cd backend
set -a; . ../.env; set +a                              # export env vars (bash)
mvn spring-boot:run

# frontend (http://localhost:5173), proxies /api to the backend
cd frontend
npm install
npm run dev
```

Open http://localhost:5173, register a business, and you land on the dashboard.

## Auth API (Phase 1)
- `POST /api/auth/register` - `{businessName, fullName, email, password}` creates a tenant and its OWNER user
- `POST /api/auth/login` - `{email, password}`
- `GET /api/auth/me` - current user (requires `Authorization: Bearer <token>`)

## Catalog API (Phase 2a) - all require a Bearer token and are scoped to the caller's tenant
- `GET/POST /api/products`, `PUT/DELETE /api/products/{id}` - list supports `q` (name/SKU), `category`, `page`, `size`; delete is a soft delete
- `GET /api/products/categories`
- `GET/POST /api/customers`, `PUT/DELETE /api/customers/{id}` - list supports `q` (name/phone/address), `platformId`, `page`, `size`
- `GET/POST /api/platforms`, `PUT/DELETE /api/platforms/{id}` - seeded with Facebook Page and Daraz for every new workspace; delete deactivates

## Sales API (Phase 2b)
- `GET /api/orders` (`platformId`, `status`, `page`, `size`; newest first), `GET /api/orders/{id}`
- `POST /api/orders`, `PUT /api/orders/{id}` - body: `platformId`, `customerId` **or** `newCustomer`, `status`, `orderedAt`, `notes`, `items[{productId, quantity, soldPrice}]`
- `PATCH /api/orders/{id}/status`, `DELETE /api/orders/{id}`
- Each item snapshots the product's cost at sale time (kept on edit for unchanged lines). Line profit = (sold price - cost) x quantity; order totals are the sums.
- Statuses: `PAID`, `PENDING`, `RETURNED`, `CANCELLED`.

## Dashboard API (Phase 2c)
- `GET /api/dashboard` - all-time totals via SQL `SUM`/`GROUP BY`, plus the 8 most recent orders.
  - `realized`: PAID orders (revenue, cost, profit = revenue - cost)
  - `pending`: PENDING orders, shown separately as expected money (e.g. cash on delivery)
  - RETURNED and CANCELLED orders are excluded from money totals and only counted

## Reports API (Phase 3a)
- `GET /api/reports/summary?from=yyyy-MM-dd&to=yyyy-MM-dd&platformId=` - all params optional; `from`/`to` are inclusive
  local dates (omit either for an open-ended range). Returns `realized` (PAID), `pending`, returned/cancelled counts
  and a `byPlatform` breakdown of paid orders, all aggregated in SQL.
- `GET /api/reports/trend` (same params) - paid revenue/cost/profit per day for ranges up to 62 days, per month
  beyond that, zero-filled so quiet periods show as flat rather than missing. Returns `granularity` (`day`/`month`).
- `GET /api/reports/products` (same params) - `topSellers` (by units) and `lowestMargin` (loss-making first), 10 each,
  over paid orders. `marginPct` is null for a product that earned no revenue.
- `GET /api/reports/export.csv` (same params) - one row per order line, **all statuses** (filter the `Status` column to
  `PAID` to match the on-screen totals), oldest first, streamed page by page. UTF-8 with a BOM so Excel shows the taka
  sign and Bangla names; names starting with `=`, `+`, `-` or `@` get a leading apostrophe to block CSV formula injection.
- `GET /api/orders` accepts the same `from`/`to` filters.
- The Reports page charts these (Recharts is lazy-loaded with the page). Chart colors live in
  `frontend/src/components/charts/tokens.js`; every chart has a table view or is itself a table.
- **PDF** is the browser's *Print / Save as PDF* on the Reports page and on `/statement` (a print stylesheet hides the
  app chrome). It is deliberately not a server-side PDF library: the browser shapes Bangla script correctly, which
  common Java PDF libraries cannot.
- `/statement?month=yyyy-MM` is the monthly summary statement: total sold, total cost, net profit/loss, by platform.
- The frontend computes the presets (this month, last month, this year, all time, custom) in the user's local time.

## Expenses API (Phase 4a)
- `GET /api/expenses` (`type`, `orderId`, `from`, `to`, `page`, `size`; newest first), `GET /api/expenses/summary` (total + per type)
- `POST /api/expenses`, `PUT/DELETE /api/expenses/{id}` - types: `DELIVERY`, `PACKAGING`, `ADS`, `PLATFORM_COMMISSION`, `MISC`; `orderId` is optional
- **Platform commission is automatic.** Set a platform's *Commission %* (Platforms page) and every order on it gets a
  `PLATFORM_COMMISSION` expense = order revenue x rate, rounded half-up to 2 decimals, dated with the sale.
  - The rate is **snapshotted on the order** when it is recorded (`orders.commission_pct`), so changing the platform's
    rate later only affects new orders. Editing an order on the same platform keeps its original rate.
  - The expense exists while the order is `PAID` or `PENDING` and is removed when it is `RETURNED` or `CANCELLED`
    (the platform refunds the fee). Manual expenses, e.g. a delivery cost, stay - a returned parcel still cost you the delivery.
  - Automatic rows are read-only (`autoGenerated: true`); change the rate or the order instead. Existing orders
    (before this feature) have a 0% snapshot, so no commission is added retroactively.
- `GET /api/orders/{id}` now includes `commissionPct` and the order's `expenses`.

## Inventory API (Phase 4b)
- Stock is tracked only for products that have a stock quantity; a product without one is ignored everywhere.
- **Sales move stock automatically.** A `PAID` or `PENDING` order holds its quantities; `RETURNED`, `CANCELLED` or deleting
  the order gives them back. Editing an order moves only the difference, and re-saving never double counts. Selling past
  zero is allowed (the sale already happened), so stock can show negative as a shortfall.
- Products take a `lowStockThreshold`. `GET /api/dashboard` returns `lowStock` (products at or below their own threshold,
  lowest first, max 20) and the dashboard shows it as an alert; the Products list shows a *Low* badge.
- `GET /api/stock/adjustments` (`productId`, `reason`, `page`, `size`; newest first) is the audit log. Each row has the signed
  `change`, `stockAfter`, a note and, for automatic rows, the `orderId`.
- `POST /api/stock/adjustments` - `{productId, change, reason, note}`; `RESTOCK` must add, `DAMAGE` must remove, `CORRECTION`
  either. Manual adjustments cannot take stock below zero.
- A stock quantity typed into the product form is logged too (`INITIAL` when tracking starts, otherwise `CORRECTION`).
- Orders recorded before a product started tracking stock have no log rows, so the first time such an order is edited or
  its status changes it will take stock as if it were new.

## Net profit (Phase 4c)
- **Gross profit** is still revenue - cost of goods (`realized.profit`). **Net profit** = gross profit - expenses
  (`netProfit`), shown side by side on the dashboard, Reports and the monthly statement.
- `GET /api/reports/summary` adds `expenses {total, unallocated, byType[]}`, `netProfit`, and `expenses` / `netProfit` on
  every `byPlatform` row. `GET /api/reports/trend` points carry `expenses` and `netProfit` (the chart draws net as a dashed
  line). `GET /api/dashboard` adds all-time `expenses` and `netProfit`. All aggregated in SQL.
- **Which expenses count** (dated by `expenseDate`, so they follow the same range filter as sales):
  - every expense **except** those tied to a still-`PENDING` order - those belong to the pending (expected) figures and start
    counting when the order is paid. Pending profit on screen is therefore gross, before expenses.
  - expenses tied to a `RETURNED` / `CANCELLED` order **do** count: a returned parcel's delivery cost is a real loss (its
    automatic commission is already refunded and gone).
  - expenses tied to no order (ads, overhead) count in the total but have no platform, so they appear as `unallocated`
    and are left out when a platform filter is set. Platform rows plus `unallocated` add up to the total.
  - a platform with expenses but no paid orders still gets a row (zero revenue, negative net).
- The CSV export lists order lines with gross figures. Expenses are on the Expenses page.
- **Delivery the customer pays**: an order's optional `deliveryCharge` is what the customer paid for delivery on top of
  the products (V6 migration; existing orders are 0). It is income, so **net profit = gross profit + delivery charged -
  expenses**: a customer-paid delivery cancels out the courier's Delivery expense. It is kept apart from `revenue`, so
  product margins and platform commission stay on the products only. Only `PAID` orders count it (`realized.delivery`);
  `pending.delivery` is expected. Summary `byPlatform` rows and trend points carry `delivery` too, and the CSV has a
  `Delivery charge` column filled on each order's first line, so summing the column counts each charge once.

## Run with Docker (Phase 5a)
```bash
cp .env.example .env            # then edit the secrets
docker compose up -d --build    # MySQL + backend + frontend
```
Open http://localhost (set `WEB_PORT` in `.env` to use another port). `docker compose down` stops it; the database lives in the
named volume `mysql_data` and survives that. Only `docker compose down -v` deletes it.
- **backend** (`backend/Dockerfile`): multi-stage. A Maven + JDK 17 stage builds the jar, and the final image is a JRE-only
  Alpine image (about 365 MB) running as a non-root user with Spring's layered jar, so a code-only rebuild reuses the
  dependency layers. Tests are skipped in the image build (they need Docker) and belong in CI (Phase 5b). It runs the `prod`
  profile and reports health at `/actuator/health`.
- **frontend** (`frontend/Dockerfile`): a Node stage runs `npm ci && npm run build`; the final image is nginx serving the
  static files (about 75 MB), falling back to `index.html` for client-side routes and forwarding `/api` to the backend, so the
  browser sees a single origin. Phase 6 turns this nginx into the hardened public proxy with TLS.
- **mysql**: `mysql:8.4` with no published port; only the backend can reach it. The backend is not published either.
- Startup is ordered by healthchecks: MySQL healthy, then backend healthy (Flyway has run), then frontend.
- Missing `MYSQL_ROOT_PASSWORD`, `MYSQL_PASSWORD` or `JWT_SECRET` stops compose with a clear message rather than starting with
  a blank secret. Secrets are only read from `.env` / the environment, never baked into an image.
- `docker-compose.dev.yml` is unchanged: MySQL only, for running the backend and frontend from your IDE.

## CI/CD (Phase 5b)
GitHub Actions workflow at `.github/workflows/ci.yml`, on every push/PR to `main`:
- **Backend tests** - `mvn test` (Testcontainers starts its own throwaway MySQL; the JWT secret is inlined for the test
  profile, so the job needs no secrets - GitHub's `ubuntu-latest` runners already have Docker).
- **Frontend build & lint** - `npm ci`, `npm run lint` (oxlint), `npm run build`.
- **Image publish** (push to `main` only, after both jobs above pass) - builds the backend and frontend Dockerfiles and
  pushes them to GHCR as `ghcr.io/<owner>/<repo>-backend` / `-frontend`, tagged `latest` and the commit SHA. Uses the
  workflow's automatic `GITHUB_TOKEN` (`packages: write`) - no registry secret to configure.
- Actually deploying those images (SSH/webhook step, staging branch/environment) is deferred until a real server
  exists - that lands with Phase 6's Nginx/production setup rather than being stubbed out speculatively here.

## Configuration management (Phase 5c)
Built in from Phase 1b/5a and confirmed here rather than deferred:
- **Secrets are environment-variable-only.** `MYSQL_ROOT_PASSWORD`, `MYSQL_PASSWORD`, `JWT_SECRET` etc. have no defaults
  in `application.yml`/`application-prod.yml` or in `docker-compose.yml` (`${VAR:?set VAR in .env}`) - a missing one stops
  startup with a clear message instead of running with a blank secret. `.env` is gitignored; only `.env.example` (placeholder
  values) is tracked, and `.env` has never been committed.
- **Separate Spring profiles**: `application-dev.yml` (localhost defaults, for running from an IDE) and
  `application-prod.yml` (no defaults - every value must be supplied explicitly, `logging.level.root: INFO`). Docker
  Compose sets `SPRING_PROFILES_ACTIVE: prod`; the default profile for a bare `mvn spring-boot:run` is `dev`.

## Production TLS (Phase 6a)
The `frontend` container (Phase 5a) already is the reverse proxy - it serves the built React app and forwards `/api` to
the backend. This phase adds Let's Encrypt TLS in front of it, as an *overlay* on top of the normal compose file so
plain local `docker compose up` (no domain needed) is untouched:
```bash
# On the server (Linux, Docker installed, this repo checked out, DOMAIN's DNS A record already pointing here):
cp .env.example .env                    # then edit the secrets, plus DOMAIN and CERTBOT_EMAIL
./nginx/init-letsencrypt.sh             # one-time: issues the certificate
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```
- `nginx/prod.conf.template` is the TLS server config (HTTP on 80 redirects to HTTPS; HTTPS on 443 serves the app and
  proxies `/api`, same as `frontend/nginx.conf`). `init-letsencrypt.sh` renders it to the gitignored `nginx/prod.conf`
  with the real domain, then bootstraps the certificate: a dummy self-signed cert so nginx can start at all, nginx up
  and answering the ACME HTTP-01 challenge, the real certificate requested via `certbot` against that, then a reload.
  Set `CERTBOT_STAGING=1` in `.env` to rehearse the whole flow against Let's Encrypt's staging endpoint first (an
  untrusted cert, but no real rate limit) before running it for real.
- `docker-compose.prod.yml` adds the `certbot` service (renews twice daily; a no-op until a cert is within 30 days of
  expiry) and publishes 443 / mounts the rendered config and cert volumes into `frontend`. **A renewal only replaces
  the files on disk** - nginx keeps the old certificate loaded in memory until reloaded, so reload it after a renewal
  actually happens (`docker compose exec frontend nginx -s reload`), e.g. from a host cron job; that lands properly
  with Phase 8's operational runbook rather than being half-built here.
- Load balancing across multiple backend containers is Phase 6b; security headers (CSP, HSTS, X-Frame-Options) and
  rate limiting are Phase 6c.

## Load balancing (Phase 6b)
`docker-compose.prod.yml` also runs a second backend instance, `backend2` - identical image and env vars to `backend`,
including `JWT_SECRET`. That last part matters: auth is stateless JWT with no server-side session, so a token issued
by one instance must validate on the other, or a request round-robined to the second instance would wrongly look
unauthenticated. Both are covered by the same `depends_on: condition: service_healthy` used since Phase 5a (Spring
Boot Actuator's `/actuator/health`, already baked into `backend/Dockerfile`'s `HEALTHCHECK`) - `frontend` doesn't
start until both backends report healthy.

`nginx/prod.conf.template`'s `upstream backend_pool { server backend:8080 ...; server backend2:8080 ...; }` load-balances
between them - round-robin, nginx's default, no directive needed. This is open-source nginx, so there's no active
polling of `/actuator/health` from nginx itself (that's an nginx-plus feature); detection is passive, via each
server's `max_fails=3 fail_timeout=10s` - three failed proxy attempts within 10s mark that backend down for the same
10s, so live traffic stops going to it without anyone doing anything. Verified directly (round-robin distribution
across both instances, and 100% failover to the survivor with no errors reaching the client, immediately after
killing one) rather than just trusting the config on paper.
- **Caveat worth knowing**: nginx resolves `backend`/`backend2` to IPs once, when it starts or is reloaded - not on
  every request. If a backend container is later *recreated* (a redeploy, not just a restart) it can come back with a
  new internal IP that nginx won't discover until it's reloaded. The startup ordering above avoids this on a fresh
  `up`; for a rolling redeploy later, reload nginx afterwards - another item for Phase 8's runbook rather than solved
  here with heavier tooling (nginx-plus or a Lua resolver) this project doesn't otherwise need.

## Hardening (Phase 6c)
All in `nginx/prod.conf.template` and the new `nginx/security-headers.conf`, verified against a real nginx container
(not just read over) - correct headers on both a proxied `/api/` response and a static location that already had its
own `Cache-Control` (nginx's `add_header` doesn't inherit into a location that sets any header of its own, so the
headers file is `include`d inside every location rather than declared once at the server level), and a real
rate-limiting run (burst allowed through, then real `429`s, while an unrelated `/api/` path stayed unaffected).
- **Rate limiting**: `/api/auth/login` and `/api/auth/register` - the credential-guessing surface - are capped at 1
  request/second per client IP (`limit_req_zone`), with a burst of up to 5 let through immediately (`burst=5
  nodelay`) so a real person fumbling a password a couple of times never notices, while a scripted attacker is capped
  at ~3600 attempts/hour per IP. Over the limit returns `429`, not the default `503`. `/api/account/` (settings, which
  check the current password) shares the same limit. Every other endpoint is unaffected.
- **Security headers**: `Content-Security-Policy` (`'self'` throughout - the build has no CDNs, no inline
  `<script>`/`<style>`, and the frontend calls the API via a same-origin relative path, confirmed by reading the
  actual Vite build output and `frontend/src/api/client.js` rather than assumed; `style-src` also allows
  `'unsafe-inline'`, a deliberate, documented trade-off - see the comment in `nginx/security-headers.conf` for why),
  `Strict-Transport-Security` (1 year, `includeSubDomains`), `X-Frame-Options: DENY`, plus `X-Content-Type-Options`
  and `Referrer-Policy` (standard companions to the three the spec named, effectively free to add).
- **Log monitoring**: an extended access log format (`upstream=... rt=...s`) shows which of the two backends served
  each request and how long it took - directly useful for watching the Phase 6b load balancer live via
  `docker compose logs -f frontend` (nginx's official image already symlinks `access.log`/`error.log` to
  stdout/stderr, so nothing extra is needed to see them). `docker-compose.prod.yml` also caps `frontend`'s on-disk
  log size (`max-size: 10m, max-file: 5`) so a long-running deployment's logs can't grow without bound. A full log
  aggregation stack (Loki/Grafana or similar) is Phase 8, once there's an actual fleet of servers to justify it.

## Tenant isolation (Phase 7a)
Every table has carried a `tenant_id` since Phase 1a; this phase is the audit and enforcement, not the column.
- **Audit**: read every controller, service, repository and DTO in the backend looking specifically for a query,
  lookup or request field that could cross tenants. Result: every hand-written `@Query`/derived-query method already
  filtered by `tenant_id`, every controller took `tenantId` only from the JWT-derived principal
  (`@AuthenticationPrincipal AuthUser`, never a request field - no request DTO even has a `tenantId` field to begin
  with), and every cross-entity reference (an order's `platformId`/`customerId`, an expense's `orderId`, ...) is
  validated against the caller's tenant before being persisted, so the handful of plain `findById` calls that exist
  are all reads of an already-tenant-validated foreign key, not of caller input. No isolation bug was found - the
  manual discipline the codebase already had turned out to be solid.
- **Enforcement, systemically**: "carefully checked by hand" is still one missed `tenantId = :tenantId` away from a
  leak in code nobody's written yet, which is the gap the spec calls out. `TenantScopedRepositoryImpl`
  (`com.salestracker.tenant`) backs every Spring Data repository in the app (wired via
  `@EnableJpaRepositories(repositoryBaseClass = ...)` on `SalesTrackerApplication`) and overrides the plain,
  un-scoped methods `JpaRepository` provides for free - `findById`, `findAll`, `findAllById`, `existsById`,
  `deleteById`, `delete`, `deleteAllById`, `deleteAll` - to filter by the current request's tenant automatically.
  `TenantContext` is a request-scoped holder (a `ThreadLocal`) that `JwtAuthFilter` sets right after it resolves the
  JWT and always clears in a `finally` block; every tenant-owned entity implements the one-method `TenantOwned`
  interface so the base repository can check ownership generically. This is additive, not a replacement - the
  existing manual checks stay (they give a precise `400 "Unknown platform"` instead of a generic empty result, which
  matters for UX), so a future query gets two independent layers agreeing rather than one.
- **Proven, not just written**: `TenantIsolationIntegrationTest` covers the resources that didn't already have their
  own isolation spot-check (expense, stock and reports each got one alongside their own feature work) - product,
  customer, platform and order, through every verb, plus dashboard/report aggregates staying at zero for a tenant
  with no data despite another tenant having real paid revenue. One test goes further and proves the systemic guard
  itself: it calls the bare, un-scoped `ProductRepository.findById` directly - standing in for a future query nobody
  thought to scope - under a different tenant's `TenantContext`, and confirms `TenantScopedRepositoryImpl` still
  refuses to hand back the other tenant's row.
- Roles (Owner/Staff) and self-service onboarding are Phase 7b; free/paid tiers and a payment gateway are Phase 7c.

## Onboarding & roles (Phase 7b)
Self-service sign-up was already there since Phase 1b (`POST /api/auth/register` creates an isolated tenant, its
OWNER user, and seeds Facebook Page/Daraz). This phase is the Owner/Staff split and the first-run guidance:
- **Team management** (`/api/users`, Owner only) - an Owner adds a Staff account (`POST`), edits its name or resets
  its password (`PUT`), or revokes access (`DELETE`, a soft deactivation - the account and its history stay, it just
  can't log in). The Owner account itself can't be deactivated through this endpoint. A new `active` column
  (`V5__staff_accounts.sql`) backs it.
- **Revocation is immediate, not eventual**: a JWT normally lives 8 hours (`app.jwt.expiration-minutes`). Rather than
  only checking `active` at login, `JwtAuthFilter` re-checks it on every request, so deactivating someone invalidates
  an already-issued token right away instead of whenever it happens to expire. Verified directly: log in as Staff,
  deactivate them, confirm their existing token now gets `401` on the very next call.
- **The permission model** (settled by asking rather than guessing, since the spec only says "limited access to
  settings/reports"): Staff gets full day-to-day access - Products, Customers, Platforms, Orders, Stock, all of it,
  create/edit/delete. What's Owner-only is financial visibility: Dashboard, Reports, Statement and Expenses are
  `@PreAuthorize("hasRole('OWNER')")` at the controller level (`403` via a dedicated `AccessDeniedException` handler,
  same `{"message": ...}` shape as every other error); a product's cost price and an order's cost/profit/commission/
  expenses are stripped from the JSON at the controller boundary for a Staff caller (the service layer still computes
  real numbers - Dashboard and other owner-only features that legitimately need them are unaffected); and a
  Platform's commission rate can't be set or changed by Staff (creating one just forces it to 0%; editing one rejects
  the request with `403` if the rate actually changed, but allows the update if it didn't - a Staff member can still
  rename a platform).
  - **A real coherence bug this surfaced and fixed**: `costPrice` was a required field on every product create/edit.
    Once Staff can't see it, requiring them to blindly resubmit it on every edit just to rename a product would have
    either broken the flow or silently corrupted real cost data. Fixed by making it optional - omitted means "leave
    the existing cost alone" on an edit, or "0, pending the Owner" on a brand new product - not by leaving the
    mismatch in place.
- **Guided first-run flow**: the Dashboard checks for zero products (platforms already exist - they're seeded at
  registration) and shows a two-step "get your workspace ready" card linking to Platforms and Products, so a brand
  new Owner isn't left guessing why their first sale attempt turns up no products to pick from.
- Verified with a dedicated `RoleAccessIntegrationTest` (Owner-only team management, immediate revocation, hidden
  financials on both products and orders with the Owner's view of the identical rows proving the data isn't actually
  gone, full Staff CRUD access, the commission-rate boundary, and the cost-price-optional fix) - 69 backend tests
  pass in total, zero regressions from before this phase.

## Account settings
The **Settings** page (the top-bar link, or click your name) lets the signed-in user manage their own account:
- `PUT /api/account/profile` - `{fullName, email, currentPassword}`. Renaming needs no password; changing the email
  (the login) requires `currentPassword`, rejects an email already in use (`409`), and returns a fresh token plus user,
  since the token carries the email.
- `PUT /api/account/password` - `{currentPassword, newPassword}` (8-72 characters); `204` on success.
- `PUT /api/account/business` - `{name}`, **Owner only**: renames the store (tenant) for everyone on the team.
- A wrong current password is a `400`, not a `401`, so the frontend shows the error instead of logging the user out.
  Covered by `AccountIntegrationTest`.

## Backups & recovery (Phase 8a)
```bash
# .env needs BACKUP_S3_BUCKET, BACKUP_S3_ENDPOINT, BACKUP_S3_ACCESS_KEY, BACKUP_S3_SECRET_KEY - any
# S3-compatible bucket works (AWS S3, Backblaze B2, DigitalOcean Spaces, MinIO, ...), already created.
./scripts/backup-db.sh                    # mysqldump | gzip, streamed straight to the bucket, no temp file
./scripts/restore-db.sh <backup-filename>  # streamed straight back in - destructive, asks for confirmation first
```
- Both scripts stream through the pipe end to end (`mysqldump | gzip | aws s3 cp -` and the reverse) - no temp
  file ever touches disk, so there's nothing to clean up and no extra disk space needed for a large database.
  They use the `amazon/aws-cli` Docker image rather than requiring `awscli` installed on the host, consistent
  with the rest of this repo.
- Pruning old backups is a lifecycle/expiration rule on the bucket itself (every S3-compatible provider has
  one) rather than scripted deletion here - a bug in a delete script is a much worse day than a bigger
  storage bill.
- **"A backup you've never restored from is not a backup"**: this was verified for real, not just written and
  trusted - ran `backup-db.sh` against a real database, inserted a marker row afterward, ran `restore-db.sh`,
  and confirmed the marker was gone and every real row was back (a genuine round trip, not just "the command
  didn't error"). Worth repeating periodically against the real production bucket once this is actually
  deployed, so a restore never has to be figured out for the first time during an incident - an operational
  runbook covering that, deploys and rollbacks is Phase 8c.

## Monitoring & logging (Phase 8b)
Opt-in overlay - combine with whatever else is already running:
```bash
# .env needs GRAFANA_ADMIN_PASSWORD
docker compose -f docker-compose.yml -f docker-compose.prod.yml -f docker-compose.monitoring.yml up -d
```
- **Centralized logging**: Loki stores logs, Promtail ships them there, Grafana queries/visualizes them.
  Promtail discovers every container on the host through the Docker socket (`docker_sd_configs`) rather than
  naming any of them individually, so it automatically covers the backend, both load-balanced instances of
  it, and nginx (access + error) - a new container just shows up, nothing to update here when one is added.
  No host-level logging-driver plugin to install; everything is a container from the compose file.
  Verified for real, not just written and trusted: ran Loki, Promtail and a throwaway logging container in
  an isolated Docker network, confirmed Promtail auto-discovered it and shipped its actual log lines into
  Loki (queried Loki's HTTP API directly and got the real lines back), then confirmed Grafana's
  auto-provisioned datasource (`monitoring/grafana-datasources.yaml` - it's there on first boot, no manual
  setup through the UI) genuinely connects to Loki (`/api/datasources/.../health` → "Data source successfully
  connected").
- **Loki storage** is the simple single-process filesystem/tsdb setup (`monitoring/loki-config.yaml`), not
  S3-backed or clustered - this project's stance on scale throughout: a single VPS is enough until it
  demonstrably isn't. Logs expire themselves after 30 days (`retention_period: 720h`), no separate pruning
  step to remember.
- **Grafana is published on `127.0.0.1` only**, not the public domain - reach it over an SSH tunnel
  (`ssh -L 3000:localhost:3000 you@server`, then open `localhost:3000` locally) rather than adding another
  public login surface. Log in with `admin` / `GRAFANA_ADMIN_PASSWORD`.
- **Uptime monitoring**: `nginx/prod.conf.template` now proxies `/actuator/health` to the backend pool -
  one URL an external free service (UptimeRobot, healthchecks.io, Better Uptime, ...) can poll that only
  returns `200` if nginx *and* a real backend behind it are both actually up. Verified against a real nginx
  container with real resolvable backends, same as every other nginx change in this project. Setting up the
  external monitor itself is an account you create and point at `https://<your-domain>/actuator/health` -
  not something a script can do on your behalf.

## Operational runbook (Phase 8c)
`RUNBOOK.md` - the deploy procedure (a quick one, and a rolling one that redeploys `backend`/`backend2` one at
a time so the other keeps serving traffic the whole time - Phase 6b's two instances earning their keep),
rollback (including the one real nuance: Flyway migrations are forward-only, so rolling back *code* doesn't
roll back a *schema* change that already ran - documented honestly rather than glossed over), incident
response basics, the backup restore drill and its cron schedule, the certificate-reload reminder from Phase
6a, and a short, deliberately narrow note on when this single-VPS setup should actually change (a second
server, a hard zero-downtime requirement, or real auto-scaling need - not before any of those are genuinely
true).

## Tests
```bash
cd backend && mvn test      # integration tests start a throwaway MySQL via Testcontainers, so Docker must be running
```

Secrets (`MYSQL_*`, `JWT_SECRET`) come from environment variables only; `.env` is gitignored.

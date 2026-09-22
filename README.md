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
- The CSV export is unchanged: it lists order lines with gross figures. Expenses are on the Expenses page.

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

## Tests
```bash
cd backend && mvn test      # integration tests start a throwaway MySQL via Testcontainers, so Docker must be running
```

Secrets (`MYSQL_*`, `JWT_SECRET`) come from environment variables only; `.env` is gitignored.

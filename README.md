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
- `GET /api/orders` accepts the same `from`/`to` filters.
- The Reports page charts these (Recharts is lazy-loaded with the page). Chart colors live in
  `frontend/src/components/charts/tokens.js`; every chart has a table view or is itself a table.
- The frontend computes the presets (this month, last month, this year, all time, custom) in the user's local time.

## Tests
```bash
cd backend && mvn test      # integration tests start a throwaway MySQL via Testcontainers, so Docker must be running
```

Secrets (`MYSQL_*`, `JWT_SECRET`) come from environment variables only; `.env` is gitignored.

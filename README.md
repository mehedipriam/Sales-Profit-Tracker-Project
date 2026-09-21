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
- `GET /api/platforms` - seeded with Facebook Page and Daraz for every new workspace

Secrets (`MYSQL_*`, `JWT_SECRET`) come from environment variables only; `.env` is gitignored.

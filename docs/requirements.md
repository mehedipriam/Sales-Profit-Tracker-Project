# Requirements & Domain Notes (Phase 1a)

## Goals
- Record every sale once, regardless of platform (Facebook, Daraz, future platforms).
- Store customer name, phone and address against each order.
- Track real cost price vs. selling price; compute true profit/loss per sale and per platform.
- Monthly and yearly revenue/profit/loss, broken down by platform.
- Scale from a personal tool to multi-business SaaS without a rewrite.

## Entities
| Entity | Purpose |
|---|---|
| Tenant (Business) | An isolated workspace; owns all other data |
| User | Login account; role OWNER, ADMIN or STAFF |
| Platform | Sales channel (Facebook Page, Daraz, ...) with optional commission % |
| Product | Name, SKU, category, cost price, selling price, optional stock |
| Customer | Name, phone, address, source platform, notes |
| Order (table `orders`) | One sale: platform, customer, status, date |
| OrderItem | Product line: quantity, **cost price snapshot**, **actual sold price** |
| Expense | Delivery, packaging, ads, platform commission, misc; optionally linked to an order |

## Key rules
- One Tenant has many Products, Customers, Platforms, Orders, Expenses.
- Each Order belongs to exactly one Platform and one Customer, and has many OrderItems.
- OrderItem snapshots cost and sold price at sale time; later product price changes never alter history.
- Line profit = (sold_price - cost_price_snapshot) x quantity; order profit = sum of lines.
- Order status: PAID, PENDING (e.g. COD), RETURNED, CANCELLED. Later reports must not count all statuses as realized profit.
- `tenant_id` is on every table (the `tenants` table's own `id` is its tenant identity). Tenant enforcement logic is built in Phase 7.
- Schema is owned by Flyway migrations; JPA runs with `ddl-auto: validate`.

## Deferred to later phases (new migrations)
- Stock adjustment log (Phase 4b).
- Any extra fields needed by reports or billing tiers (Phases 3, 7).

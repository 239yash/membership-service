# Membership Service

Tiered subscription membership backend. Plans (Monthly/Quarterly/Yearly) × Tiers (Silver/Gold/Platinum) with configurable eligibility criteria and benefits per tier.

- Architecture and design: [DESIGN.md](DESIGN.md)
- Sample user journey: [BUSINESS_WALKTHROUGH.md](BUSINESS_WALKTHROUGH.md)
- End-to-end curl flow: [TESTING.md](TESTING.md)

## Stack
- Java 17 · Spring Boot 4.0.x · Maven · Lombok
- PostgreSQL 16 (JPA + Hibernate, `ddl-auto: validate`)
- Redis 7 (tier config cache)
- `schema.sql` + `data.sql` are the source of truth for DDL and seed data

## Quick start

```bash
# 1. infra
docker compose up -d

# 2. run the app (it runs schema.sql + data.sql on first boot)
./mvnw spring-boot:run
```

Embedded server on `http://localhost:8080`. Postgres on `localhost:5432`, Redis on `localhost:6379`.

Connection details (from `application.yaml`):
```
db:    membership / membership / membership
redis: localhost:6379 (no auth)
```

Verify the DB:
```bash
psql -h localhost -U membership -d membership -c "\dt"
psql -h localhost -U membership -d membership -c \
  "SELECT code, name, active_criterion_rule_id, active_benefit_config_id FROM membership_tier;"
```

## Seeded data

The app boots with:
- 3 plans — `MONTHLY` (₹199), `QUARTERLY` (₹499), `YEARLY` (₹1499)
- 3 tiers — `SILVER` (×1.00), `GOLD` (×1.50), `PLATINUM` (×2.50)
- 3 criterion rules — one starting rule per tier
- 3 benefit configs — one starting set per tier
- 3 users — Riya (id 100, `EARLY_ADOPTER`), Arjun (id 101), Meera (id 102, `VIP`)

Each tier points at its rule and benefit config via `membership_tier.active_criterion_rule_id` and `active_benefit_config_id`.

## API

All endpoints are under `/api/v1`. Every response is wrapped in:
```json
{ "success": true,  "data": { ... }, "error": null }
{ "success": false, "data": null,    "error": { "code": "...", "message": "...", "fieldErrors": { ... } } }
```

| Method | Path | Purpose |
|---|---|---|
| GET  | `/plans` | list active plans |
| GET  | `/tiers` | list active tiers with their current benefits and criteria |
| POST | `/users` | create a user |
| GET  | `/users/{userId}` | get a user |
| POST | `/subscriptions` | subscribe `{userId, planCode, tierCode}` |
| GET  | `/users/{userId}/subscription` | current live subscription |
| GET  | `/users/{userId}/subscriptions` | history with audit events |
| POST | `/subscriptions/{subscriptionId}/change-tier` | upgrade (immediate, prorated) or downgrade (scheduled) |
| POST | `/subscriptions/{subscriptionId}/cancel` | cancel at period end |
| POST | `/users/{userId}/orders` | place an order; tier eval runs inline after commit |
| POST | `/users/{userId}/checkout/preview` | apply current tier benefits to a sample cart |
| POST | `/admin/criteria` | create a criterion rule version, returns id |
| POST | `/admin/benefits` | create a benefit config version, returns id |
| POST | `/admin/tiers/{tierCode}/activate-criteria` | flip a tier's active criterion rule |
| POST | `/admin/tiers/{tierCode}/activate-benefits` | flip a tier's active benefit config |
| POST | `/admin/tier-sweep` | re-evaluate every live subscription |

Sample request/response shapes are in [TESTING.md](TESTING.md).

## Changing tier rules or benefits at runtime

Two ways:

**1. Via the admin API**

```http
POST /api/v1/admin/criteria
{ "ruleTree": { ... } }
→ 201 { "id": 4 }

POST /api/v1/admin/tiers/GOLD/activate-criteria
{ "criterionRuleId": 4 }
→ 200
```

**2. Directly via SQL** (sometimes simpler for ops)

```sql
BEGIN;
INSERT INTO criterion_rule (rule_tree, description, created_by)
VALUES ('{"op":"AND","children":[...]}'::jsonb, 'reason', 'someone@team')
RETURNING id;     -- say it returns 4

UPDATE membership_tier
SET    active_criterion_rule_id = 4
WHERE  code = 'GOLD';
COMMIT;
```

The admin API path publishes a `TierConfigChangedEvent` which invalidates the Redis cache for `tier:config:GOLD`. The raw SQL path **does not** — bypassing the app means the cache stays stale until you `DEL tier:config:GOLD` in Redis by hand, or restart the app.

## Project layout

```
src/main/java/com/work/membership_service/
├── MembershipServiceApplication.java
├── controller/        rest endpoints
├── service/           business logic
├── repository/        spring data jpa
├── model/entity/      jpa entities
├── engine/            criterion engine + benefit engine
├── exception/         custom exceptions + GlobalExceptionHandler
├── constant/          enums, records
├── event/             application events
└── concurrency/       striped locking

src/main/resources/
├── application.yaml
├── schema.sql
└── data.sql
```

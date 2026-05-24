# Membership Service

Tiered subscription membership backend. Plans (Monthly/Quarterly/Yearly) × Tiers (Silver/Gold/Platinum) with configurable eligibility criteria and benefits per tier.

For architecture and design details, see [DESIGN.md](DESIGN.md).

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
├── configuration/     spring config (async, redis, jackson)
├── event/             application events
└── concurrency/       striped locking

src/main/resources/
├── application.yaml
├── schema.sql
└── data.sql
```

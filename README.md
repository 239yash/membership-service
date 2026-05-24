# Membership Service

A tiered subscription membership backend. Plans (Monthly / Quarterly / Yearly) × Tiers (Silver / Gold / Platinum) with **configurable** eligibility criteria and benefits per tier, **auto-promotion** based on activity, and a clean lifecycle for upgrade / downgrade / cancel.

---

## 📚 Documentation map

Everything a reviewer needs is reachable from here.

| File | What you'll find | Read when |
|---|---|---|
| [DESIGN.md](DESIGN.md) | architecture, entity model, criteria & benefits configurability, tier-eval flow, concurrency story, proration math (with worked example) | you want the design intent |
| [BUSINESS_WALKTHROUGH.md](BUSINESS_WALKTHROUGH.md) | a 9-scene user journey end-to-end with real numbers | you want to see the business value |
| [TESTING.md](TESTING.md) | setup options, copy-paste curl flow for every endpoint with expected responses, DB + Redis inspection, error cases | you want to run / verify it yourself |
| [`src/main/resources/schema.sql`](src/main/resources/schema.sql) | DDL for all 8 tables (run on every boot) | you want the data model |
| [`src/main/resources/data.sql`](src/main/resources/data.sql) | seed inserts — plans, tiers, criterion rules, benefit configs, users (run after schema) | you want to know what's in the DB at startup |
| [`src/main/resources/application.yaml`](src/main/resources/application.yaml) | datasource, JPA, Redis, SQL-init configuration | you want to change ports / creds / DB |
| [`src/main/resources/logback-spring.xml`](src/main/resources/logback-spring.xml) | log pattern (IST time, traceId in MDC, class, message) | you want to tune logging |

---

## TL;DR — the core ideas

| Concept | What it means |
|---|---|
| **Plan vs Tier** | Plan = billing cadence + base price (Monthly/Quarterly/Yearly). Tier = level of benefits (Silver/Gold/Platinum). They are orthogonal. A subscription is `(plan, tier)`. |
| **Purchased tier = floor** | The tier the user paid for. Effective tier can rise above it via activity-based auto-promotion, never falls below it during the paid period. |
| **Configurable criteria** | Each tier's eligibility is a JSON rule tree (AND/OR/NOT + typed leaves) evaluated by `CriterionEngine`. New leaf type = add one Spring bean implementing `CriterionDefinition`. |
| **Configurable benefits** | Each tier's benefits are a JSON array of `{type, params}` built into strategy beans by `BenefitFactory`. |
| **Versioned + activated** | Criterion rules and benefit configs are append-only rows. `membership_tier` has FK pointers to the *active* version. Admin flips a pointer to "activate" a new version. Rollback = re-activate an older id. |
| **Tier evaluation after orders** | Order placement publishes an `OrderPlacedEvent`. An `AFTER_COMMIT` listener runs `TierEvaluationService.evaluate(userId)` inline — by the time the order response returns, the tier is up to date. The current order itself does not get the new tier (promotion applies to the next order). |
| **Concurrency** | Per-user `StripedLockRegistry` for in-JVM serialization + `@Version` on Subscription for the cross-JVM safety net. |
| **Redis cache** | One key per tier holds the active config (parsed rule + benefits). Cache-aside; invalidated by `TierConfigChangedEvent` from admin activations. |

Full design rationale: [DESIGN.md](DESIGN.md).

---

## Stack

- Java 17 · **Spring Boot 3.5.3** · Maven · Lombok
- PostgreSQL 16 (JPA + Hibernate, `ddl-auto: validate`)
- Redis 7 (tier config cache)
- `schema.sql` + `data.sql` are the source of truth for DDL and seed data

---

## Quick start

```bash
# 1. infra — choose one of:
docker compose up -d                                 # option A: docker
# option B (local PG + Redis already running): create role + db once:
#   psql -h localhost -d postgres -c "CREATE ROLE membership WITH LOGIN PASSWORD 'membership' CREATEDB;"
#   psql -h localhost -d postgres -c "CREATE DATABASE membership OWNER membership;"

# 2. run the app — schema.sql + data.sql execute automatically on boot
./mvnw spring-boot:run
```

Embedded server on `http://localhost:8080`. Postgres on `localhost:5432`, Redis on `localhost:6379`.

Defaults (from `application.yaml`):

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

Then jump into [TESTING.md](TESTING.md) for the full curl walkthrough.

---

## Database schema

Source of truth: [`src/main/resources/schema.sql`](src/main/resources/schema.sql).

8 tables:

| Table | Purpose |
|---|---|
| `membership_plan` | billing cadence + base price (3 seeded: MONTHLY, QUARTERLY, YEARLY) |
| `membership_tier` | tier metadata + FK pointers to the active criterion rule and benefit config |
| `criterion_rule` | append-only versioned, tier-agnostic eligibility rule trees (JSONB) |
| `benefit_config` | append-only versioned, tier-agnostic benefit arrays (JSONB) |
| `user_account` | users + cohorts (`text[]`) |
| `subscription` | live subscription state with `@Version` optimistic lock |
| `subscription_event` | append-only audit of every state change |
| `orders` | minimal — feeds the tier evaluation stats |

Schema is recreated on every boot (`schema.sql` starts with `DROP TABLE IF EXISTS … CASCADE`) — intentional for the demo, **destructive in production**. Real prod would manage schema via Flyway/Liquibase.

---

## Seed data

Source: [`src/main/resources/data.sql`](src/main/resources/data.sql).

The app boots with:

- **3 plans** — `MONTHLY` (₹199), `QUARTERLY` (₹499), `YEARLY` (₹1499)
- **3 tiers** — `SILVER` (×1.00), `GOLD` (×1.50), `PLATINUM` (×2.50)
- **3 criterion rules** — one starting rule per tier (silver = empty AND, gold = 5 orders/30d AND ≥10k/30d, platinum = 10 orders/30d AND (≥50k/30d OR cohort ∈ {VIP, EARLY_ADOPTER}))
- **3 benefit configs** — one starting set per tier
- **3 users** — Riya (id 100, `EARLY_ADOPTER`), Arjun (id 101), Meera (id 102, `VIP`)

Each tier's `active_criterion_rule_id` and `active_benefit_config_id` already point at the right seed rows, so the app is fully functional the moment it boots.

---

## API

All endpoints are under `/api/v1`. Every response follows the same envelope:

```json
{ "status": "SUCCESS", "data": <typed payload>, "error": null }
```
```json
{
  "status": "FAILURE",
  "data": null,
  "error": { "code": "VALIDATION_FAILED", "message": "...", "fieldErrors": { ... } }
}
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

Concrete request / response samples for each endpoint: [TESTING.md](TESTING.md).

---

## Changing tier rules or benefits at runtime

Two ways.

**1. Via the admin API** (preferred — invalidates the Redis cache automatically via event):

```http
POST /api/v1/admin/criteria
{ "ruleTree": { ... } }
→ 201 { "id": 4 }

POST /api/v1/admin/tiers/GOLD/activate-criteria
{ "criterionRuleId": 4 }
→ 200
```

**2. Directly via SQL** (handy for ops, but skips the cache invalidation):

```sql
BEGIN;
INSERT INTO criterion_rule (rule_tree, description, created_by)
VALUES ('{"op":"AND","children":[...]}'::jsonb, 'reason', 'someone@team')
RETURNING id;       -- say it returns 4

UPDATE membership_tier
SET    active_criterion_rule_id = 4
WHERE  code = 'GOLD';
COMMIT;
```

After the raw-SQL path, run `redis-cli DEL tier:config:GOLD` (or restart the app) to drop the stale cache entry.

---

## Logging

Configured in [`src/main/resources/logback-spring.xml`](src/main/resources/logback-spring.xml). Sample lines:

```
2026-05-24 20:13:24.033 [http-nio-8080-exec-2] [traceId=demo-trace-abc] INFO  c.w.m.c.f.RequestMethodLogFilter - [http] incoming method: GET, path: /api/v1/plans
2026-05-24 20:13:49.797 [http-nio-8080-exec-7] [traceId=719cf147-…]     INFO  c.w.m.s.tier.TierEvaluationService - [tier_eval] AUTO_PROMOTED user id: 100, sub id: 1, from tier id: 2 to tier id: 3, rule id: 3
```

- IST timestamps via `%d{..., Asia/Kolkata}`.
- `traceId` propagated through MDC by [`MdcFilter`](src/main/java/com/work/membership_service/configuration/filter/MdcFilter.java). Caller can pass an `X-Trace-Id` header; otherwise a UUID is generated. Echoed back on the response.
- Every request is bookended by a `[http] incoming` / `[http] completed` line from [`RequestMethodLogFilter`](src/main/java/com/work/membership_service/configuration/filter/RequestMethodLogFilter.java).
- Domain logs use a `[domain_tag]` prefix: `[order_flow]`, `[sub_lifecycle]`, `[tier_eval]`, `[admin_config]`, `[cache]`, `[checkout]`, `[err]`.

---

## Project layout

```
src/main/java/com/work/membership_service/
├── MembershipServiceApplication.java
├── configuration/filter/    MdcFilter, RequestMethodLogFilter
├── constant/                StringConstant + constant/enums, constant/record (none, ApiResponse lives in model)
├── concurrency/             StripedLockRegistry (per-user ReentrantLock + refcount eviction)
├── engine/
│   ├── criterion/           CriterionDefinition interface, registry, recursive engine, 4 leaf impls
│   ├── benefit/             Benefit interface, factory, 5 strategy impls, CartContext, BenefitOutcome
│   └── stats/               UserStats + UserStatsProvider (lazy windowed aggregates)
├── event/                   OrderPlacedEvent, TierChangedEvent, TierConfigChangedEvent
├── exception/               BaseException hierarchy + GlobalExceptionHandler
├── model/entity/            JPA entities + model/entity/record/ApiResponse
├── repository/              Spring Data JPA repos
├── service/
│   ├── admin/               TierConfigAdminService (create + activate)
│   ├── checkout/            CheckoutService (cart + benefits)
│   ├── order/               OrderService
│   ├── subscription/        SubscriptionService, audit, state machine, pricing/ProrationCalculator
│   ├── tier/                TierConfigService (Redis cache-aside), TierEvaluationService + listener,
│   │                        TierConfigCacheInvalidator, TierChangedNotifier
│   └── user/                UserService
└── web/
    ├── controller/          7 controllers covering all 16 endpoints
    └── dto/{request,response}/  request DTOs (with bean validation) + response DTOs

src/main/resources/
├── application.yaml
├── logback-spring.xml
├── schema.sql               source of truth for DDL
└── data.sql                 seed inserts
```

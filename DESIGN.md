# Design

A tiered subscription membership service. Users buy a plan + tier, get benefits, and can be auto-promoted (above their paid floor) based on activity.

---

## What we are modelling

Two orthogonal concepts the requirement conflates:

- **Plan** = billing cadence + price (Monthly / Quarterly / Yearly). Answers *"how often do you pay?"*
- **Tier** = level of benefits (Silver / Gold / Platinum). Answers *"what do you get?"*

A `Subscription = (user, plan, tier, dates, status)`. Keeping plan and tier separate avoids a combinatorial explosion of `MonthlySilver`, `MonthlyGold`, `YearlyPlatinum` etc.

Tier has two assignment modes:
- **Purchased** — what the user paid for. This is the **floor**.
- **Effective** — what they actually get benefits at. May be auto-promoted above the floor by recent activity.

Auto-evaluation can only raise effective tier above purchased; it never drops below the floor for the duration of the paid subscription.

---

## Domain entities

| Entity | Purpose |
|---|---|
| `MembershipPlan` | Code (MONTHLY/QUARTERLY/YEARLY), duration in days, base price |
| `MembershipTier` | Code (SILVER/GOLD/PLATINUM), rank, price multiplier, FK pointers to the currently active criterion rule + benefit config |
| `CriterionRule` | Tier-agnostic, immutable JSON tree of eligibility rules. `id` is the version |
| `BenefitConfig` | Tier-agnostic, immutable JSON array of benefits. `id` is the version |
| `UserAccount` | User + cohorts array |
| `Subscription` | Live state — purchased + effective tier, dates, status, `@Version` for optimistic lock |
| `SubscriptionEvent` | Append-only audit of every state change |
| `Order` | Minimal — feeds tier-eval stats |

---

## Tables (snake_case in DB, camelCase in code and JSON)

8 tables, all in `schema.sql`:

```
membership_plan          billing cadence + price
membership_tier          tier metadata + fk pointers to active rule and benefits
criterion_rule           versioned, tier-agnostic eligibility rule tree
benefit_config           versioned, tier-agnostic benefit array
user_account             user + cohorts
subscription             live subscription with @Version optimistic lock
subscription_event       append-only audit
orders                   feeds stats
```

---

## Configurability — criteria and benefits

Two-layer model:

### 1. `CriterionDefinition` — the *type*, lives in code
Spring beans. Each one knows its `type` (e.g. `MIN_ORDER_COUNT`), validates its params, and evaluates against `UserStats`. **This is the only extension point engineers touch.** Adding a new criterion type means adding one class.

Initial set:
- `MIN_ORDER_COUNT` — params: `{count, windowDays}`
- `MIN_ORDER_VALUE` — params: `{amount, windowDays}`
- `MIN_LIFETIME_ORDER_VALUE` — params: `{amount}`
- `COHORT_MEMBERSHIP` — params: `{cohorts: [...]}`

### 2. `CriterionRule.ruleTree` — the *configuration*, lives in DB as JSONB

Small recursive tree:

```jsonc
// branch
{ "op": "AND" | "OR" | "NOT", "children": [ ... ] }

// leaf
{ "leaf": "<typeName>", "params": { ... } }
```

Example — Gold's seeded rule:

```json
{
  "op": "AND",
  "children": [
    { "leaf": "MIN_ORDER_COUNT", "params": { "count": 5, "windowDays": 30 } },
    { "leaf": "MIN_ORDER_VALUE", "params": { "amount": 10000, "windowDays": 30 } }
  ]
}
```

Benefits work the same way — `BenefitConfig.benefits` is a JSONB array of `{type, params}` interpreted by typed `Benefit` strategy beans.

### Version model

- `id` of `criterion_rule` (and `benefit_config`) **is the version**.
- Rules are **immutable** — admin creates a new row, never updates an old one.
- `membership_tier.active_criterion_rule_id` and `active_benefit_config_id` are forward FK pointers — flipping a pointer is how a version is "activated".
- Rollback = activate an older id.

---

## Tier evaluation flow

```
POST /orders
   │
OrderService.placeOrder()
   ├─ persist Order  (transaction begins)
   ├─ publishEvent(OrderPlacedEvent)
   └─ transaction commits
                                     │  AFTER_COMMIT (sync)
                                     ▼
                     TierEvaluationListener
                                     │
                                     ▼
TierEvaluationService.evaluate(userId):
   1. stripedLock.lock(userId)
   2. load active subscription (exit if none)
   3. stats = userStatsProvider.compute(userId)
   4. iterate tiers by rank desc; pick first whose rule matches
   5. target = max(purchasedTier, qualifyingTier)
   6. if target != effective: update subscription with @Version check,
      append subscription_event, publish TierChangedEvent
   7. unlock
```

Key choices:
- **After-commit, synchronous**: tier eval runs only on durable orders (never on a transaction that rolls back), inline on the request thread so the caller sees the order response only after the tier is up to date.
- **Striped lock + @Version**: in-JVM serialization for the same user, plus an optimistic lock as the last-mile guarantee. Two evaluations on the same subscription cannot both silently succeed.
- **Current order does NOT get the new tier's benefits.** Promotions apply to the *next* order. Matches industry behavior; avoids re-pricing races.
- **Demotion is handled by a periodic sweep** (`POST /api/v1/admin/tier-sweep`). Effective tier ratchets up on every order; only the sweep can ratchet it back down — and never below the purchased floor.

---

## Subscription lifecycle

States: `ACTIVE` → `PENDING_DOWNGRADE` (on scheduled downgrade) → `ACTIVE` (after period end + downgrade applied)
or `ACTIVE` → `CANCELLED_AT_PERIOD_END` → `EXPIRED` (after period end)

Tier changes:
- **Upgrade** — immediate, **prorated charge** for the remaining days at the new tier's price delta. See below.
- **Downgrade** — takes effect at period end. `scheduled_tier_id` set, status flips to `PENDING_DOWNGRADE`. No refund.
- **Cancel** — at period end. Benefits stay live until `end_date`; status flips to `EXPIRED` afterwards.

### Proration math (upgrades)

The intuition: **the customer pays the new tier's rate for the days they will actually use the new tier; the days they already paid for at the old rate are credited back.**

Formula in `ProrationCalculator`:

```
chargeToday = (newPrice − oldPrice) × (daysRemaining / totalDays)
```

#### Worked example

A user paid ₹2000 for a year of Gold. After 100 days, she upgrades to Platinum, which would cost ₹4000 for a full year. 265 days remain in the period.

Two equivalent views:

**View 1 — credit + new charge (the mental model)**
- Days 101–365 of unused Gold = `2000 × 265/365 = ₹1452.05` (credit)
- Days 101–365 of Platinum     = `4000 × 265/365 = ₹2904.11`
- Charge today = `2904.11 − 1452.05 = ₹1452.05`

**View 2 — delta × fraction remaining (what the code computes)**
- delta         = `4000 − 2000 = 2000`
- fraction left = `265 / 365  = 0.7260`
- Charge today  = `2000 × 0.7260 = ₹1452.05`

Same number — the two terms in View 1 factor out to View 2.

#### Sanity check on total spend

What she pays across the full year:
- ₹2000 (original Gold subscription, paid up front)
- ₹1452.05 (proration today)
- **Total: ₹3452.05**

A hypothetical "bought Platinum from day 1" would have cost ₹4000. The ₹547.95 difference is exactly the cost of 100 days of Gold (`2000 × 100/365`) — the rate she actually paid for the time she was on Gold. Fair.

#### Guard conditions

- `delta ≤ 0` → return zero (caller asked for an "upgrade" to a tier that costs the same or less).
- `daysRemaining = 0` → return zero (period already over; renewal will charge the new tier next cycle).

Downgrades never enter this function — they're scheduled for period end with zero immediate charge.

---

## Redis-backed tier config cache

Cache-aside, one key per tier:

```
key:   tier:config:GOLD
value: { tierCode, rank, priceMultiplier, criterionRuleId, ruleTree,
         benefitConfigId, benefits }
```

- **No TTL** — invalidation is explicit via events.
- **Read path**: Redis GET → miss? join Postgres (`membership_tier` + `criterion_rule` + `benefit_config` by the FK pointers) → SET in Redis → return.
- **Invalidation**: admin activates a new version → `UPDATE membership_tier` → publish `TierConfigChangedEvent` (AFTER_COMMIT) → listener `DEL`s the Redis key.

---

## Concurrency

Three races to defend against:
1. **Two concurrent tier changes** on the same subscription.
2. **A tier change racing with a renewal**.
3. **Auto-promotion (from one user's order) racing with a manual change on the same subscription**.

Two layers:
- **Striped lock** — `ConcurrentHashMap<userId, ReentrantLock>` with reference-counted eviction. Serializes within one JVM.
- **`@Version` on Subscription** — the JPA optimistic lock. Survives across JVMs. If two writers somehow bypass the lock, only one's UPDATE succeeds; the other gets `OptimisticLockException` and retries.

---

## Endpoints (16 total)

| Method | Path | Purpose |
|---|---|---|
| GET  | /api/v1/plans | list plans |
| GET  | /api/v1/tiers | list tiers with active benefits + criteria |
| POST | /api/v1/users | create user |
| GET  | /api/v1/users/{userId} | get user |
| POST | /api/v1/subscriptions | subscribe (planCode + tierCode) |
| GET  | /api/v1/users/{userId}/subscription | current subscription |
| GET  | /api/v1/users/{userId}/subscriptions | history with audit events |
| POST | /api/v1/subscriptions/{subscriptionId}/change-tier | upgrade or downgrade |
| POST | /api/v1/subscriptions/{subscriptionId}/cancel | cancel at period end |
| POST | /api/v1/users/{userId}/orders | place order, triggers tier eval inline |
| POST | /api/v1/users/{userId}/checkout/preview | apply current tier benefits to a sample cart |
| POST | /api/v1/admin/criteria | create criterion rule, returns id |
| POST | /api/v1/admin/benefits | create benefit config, returns id |
| POST | /api/v1/admin/tiers/{tierCode}/activate-criteria | activate a criterion rule id on a tier |
| POST | /api/v1/admin/tiers/{tierCode}/activate-benefits | activate a benefit config id on a tier |
| POST | /api/v1/admin/tier-sweep | re-evaluate all active subscriptions |

---

## Stack

- Java 17, Spring Boot 4.0.x
- PostgreSQL 16 (JPA + Hibernate, `ddl-auto: validate` — schema is in `schema.sql`)
- Redis 7 (Lettuce client)
- Maven, Lombok
- No Flyway, no Kafka, no JWT — kept intentionally small

---

## Out of scope (call out so we agree)

- No real payment gateway — `pricePaid` is computed; a `FakePaymentGateway` is the seam.
- No JWT / auth — `userId` is a path/body param.
- No scheduler (`@Scheduled`) — the tier sweep is an admin endpoint, deterministic for the demo.
- No JUnit suite — verification is the curl flow in `TESTING.md`.
- No `tier_config_activation` audit table — rules are immutable, `subscription_event.metadata` records which rule id matched at promotion time. That is enough audit.

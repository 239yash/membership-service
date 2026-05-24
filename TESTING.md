# Testing guide

A copy-paste curl walkthrough that exercises every endpoint end to end, plus the actual responses you should see.

Pairs with [BUSINESS_WALKTHROUGH.md](BUSINESS_WALKTHROUGH.md) — same story, executable.

---

## Setup

Pick one of the two options for infrastructure.

### Option A — Docker (clean room)

```bash
docker compose up -d
```

Brings up postgres on `:5432` (db `membership`, user `membership`, password `membership`) and redis on `:6379`.

### Option B — Existing local Postgres + Redis

If you already have local Postgres + Redis running on the default ports:

```bash
# create the role + database (run once)
psql -h localhost -d postgres -c "CREATE ROLE membership WITH LOGIN PASSWORD 'membership' CREATEDB;"
psql -h localhost -d postgres -c "CREATE DATABASE membership OWNER membership;"

# verify
PGPASSWORD=membership psql -h localhost -U membership -d membership -c "SELECT current_user, current_database();"
```

### Boot the app

```bash
./mvnw spring-boot:run
```

Wait for `Started MembershipServiceApplication` in the logs. You should also see:

```
[criterion_registry] registered types: [MIN_ORDER_VALUE, MIN_ORDER_COUNT, COHORT_MEMBERSHIP, MIN_LIFETIME_ORDER_VALUE]
```

That's the strategy registry picking up all four criterion bean implementations.

In a separate shell:

```bash
BASE=http://localhost:8080/api/v1
```

The seed data already has:
- 3 users — Riya (id 100, cohort `EARLY_ADOPTER`), Arjun (id 101), Meera (id 102, `VIP`)
- 3 plans — MONTHLY (₹199), QUARTERLY (₹499), YEARLY (₹1499)
- 3 tiers — SILVER (×1.00), GOLD (×1.50), PLATINUM (×2.50)
- 3 criterion rules + 3 benefit configs, each tier already pointing at its starting active version

## Response envelope

Every endpoint returns the same shape:

```json
{ "status": "SUCCESS", "data": <typed payload>, "error": null }
{ "status": "FAILURE", "data": null, "error": { "code": "...", "message": "...", "fieldErrors": { ... } } }
```

---

## 1. Catalog

```bash
curl -s $BASE/plans | jq
curl -s $BASE/tiers | jq
```

Plans are simple records. Tiers inline the **currently active** rule tree and benefits array — these come straight from the Redis cache (cache-aside on top of Postgres).

Sample tier entry:

```json
{
  "id": 3, "code": "PLATINUM", "rank": 3, "priceMultiplier": 2.5,
  "activeCriterionRuleId": 3,
  "ruleTree": {
    "op": "AND",
    "children": [
      { "leaf": "MIN_ORDER_COUNT", "params": { "count": 10, "windowDays": 30 } },
      { "op": "OR", "children": [
        { "leaf": "MIN_ORDER_VALUE", "params": { "amount": 50000, "windowDays": 30 } },
        { "leaf": "COHORT_MEMBERSHIP", "params": { "cohorts": ["VIP","EARLY_ADOPTER"] } }
      ]}
    ]
  },
  "activeBenefitConfigId": 3,
  "benefits": [ ... ]
}
```

## 2. Create a fresh user (optional — three are already seeded)

```bash
curl -s -X POST $BASE/users \
  -H 'content-type: application/json' \
  -d '{"name":"Test","email":"test@example.com","cohorts":["BETA"]}'

curl -s $BASE/users/100
```

## 3. Subscribe — Riya picks Yearly + Gold

```bash
curl -s -X POST $BASE/subscriptions \
  -H 'content-type: application/json' \
  -d '{"userId":100,"planCode":"YEARLY","tierCode":"GOLD"}'
```

Response:

```json
{
  "status": "SUCCESS",
  "data": {
    "id": 1, "userId": 100, "planCode": "YEARLY",
    "purchasedTierCode": "GOLD", "effectiveTierCode": "GOLD",
    "status": "ACTIVE",
    "startDate": "...", "endDate": "<+365d>",
    "autoRenew": true, "pricePaid": 2248.5, "version": 0
  }
}
```

Note `pricePaid = 1499 × 1.5 = 2248.50`. `version: 0` is the JPA `@Version` optimistic lock.

Save the id:

```bash
SUB_ID=1
```

## 4. Current subscription

```bash
curl -s $BASE/users/100/subscription
```

Same shape as the subscribe response. Use this any time to see live state.

## 5. Place orders — drive auto-promotion

Each call returns 201 (created). Tier evaluation runs inline via a `@TransactionalEventListener(AFTER_COMMIT)` — by the time the order response returns, the tier is already updated.

```bash
for i in 1 2 3 4 5 6 7 8 9 10 11 12; do
  curl -s -X POST $BASE/users/100/orders \
    -H 'content-type: application/json' \
    -d '{"amount":5500,"category":"FOOD"}' > /dev/null
done

curl -s $BASE/users/100/subscription | jq '{purchasedTierCode, effectiveTierCode, version, status}'
```

Expected:

```json
{
  "purchasedTierCode": "GOLD",
  "effectiveTierCode": "PLATINUM",
  "version": 1,
  "status": "ACTIVE"
}
```

12 orders × ₹5500 = ₹66,000 in the rolling 30-day window. Platinum's rule needs `count ≥ 10 AND (value ≥ 50000 OR cohort ∈ {VIP, EARLY_ADOPTER})`. Both are satisfied (count 12, value 66000, plus Riya's cohort). She gets auto-promoted to Platinum *effective*, but her purchased tier (the floor) stays Gold — she keeps Platinum benefits as long as activity sustains the promotion.

Watch the app log:

```
[order_flow] placed order id: 12, user id: 100, amount: 5500, category: FOOD
[sub_audit] recorded sub id: 1, type: AUTO_PROMOTED, from tier id: 2, to tier id: 3
[tier_eval] AUTO_PROMOTED user id: 100, sub id: 1, from tier id: 2 to tier id: 3, rule id: 3
[tier_change] notify user id: 100, sub id: 1, from tier id: 2 -> to tier id: 3, kind: AUTO_PROMOTED
```

All on the same request thread (`nio-8080-exec-*`). The listener runs after the order's transaction commits, so a rolled-back order never triggers a promotion.

## 6. Audit trail

```bash
curl -s $BASE/users/100/subscriptions | jq '.data[0].events'
```

You should see two events: `CREATED` and `AUTO_PROMOTED`. The promotion row carries metadata identifying which rule matched:

```json
{
  "type": "AUTO_PROMOTED",
  "fromTierCode": "GOLD",
  "toTierCode": "PLATINUM",
  "reason": "auto-promoted by tier rule",
  "metadata": "{\"qualifyingTier\": \"PLATINUM\", \"criterionRuleId\": 3}",
  "occurredAt": "..."
}
```

Three months later you can still answer "why was she promoted on May 30?" by reading rule id 3 from `criterion_rule` — the row is immutable.

## 7. Checkout preview — Platinum benefits applied to a cart

```bash
curl -s -X POST $BASE/users/100/checkout/preview \
  -H 'content-type: application/json' \
  -d '{
    "items": [
      {"category":"FOOD","price":1000},
      {"category":"GROCERY","price":1500}
    ],
    "deliveryFee": 50
  }'
```

Response:

```json
{
  "status": "SUCCESS",
  "data": {
    "subtotal": 2500, "deliveryFee": 50,
    "appliedBenefits": [
      { "type":"FREE_DELIVERY",    "applies": true, "savings": 50,  "reason":"subtotal >= 0" },
      { "type":"EXTRA_DISCOUNT",   "applies": true, "savings": 375, "reason":"15% off everything" },
      { "type":"EXCLUSIVE_DEALS",  "applies": true, "savings": 0,   "reason":"exclusive deals unlocked" },
      { "type":"EARLY_ACCESS",     "applies": true, "savings": 0,   "reason":"early access of 24h" },
      { "type":"PRIORITY_SUPPORT", "applies": true, "savings": 0,   "reason":"priority support sla 5 min" }
    ],
    "totalSavings": 425,
    "finalPayable": 2125,
    "tierApplied": "PLATINUM"
  }
}
```

Each `Benefit` is a strategy bean; the configured list is built per-tier from the JSON config by `BenefitFactory` and applied to the cart.

## 8. Admin — create + activate a new criterion rule

Two-step pattern: **create** stores a new immutable row and returns its id; **activate** flips the tier's FK pointer to that id and fires a cache-invalidation event.

```bash
RULE_ID=$(curl -s -X POST $BASE/admin/criteria \
  -H 'content-type: application/json' \
  -d '{
    "ruleTree": {
      "op":"AND",
      "children":[
        {"leaf":"MIN_ORDER_COUNT","params":{"count":3,"windowDays":30}},
        {"leaf":"MIN_ORDER_VALUE","params":{"amount":6000,"windowDays":30}}
      ]
    },
    "description":"easier gold for q3",
    "createdBy":"growth-team"
  }' | jq -r '.data.id')
echo "new rule id: $RULE_ID"

curl -s -X POST $BASE/admin/tiers/GOLD/activate-criteria \
  -H 'content-type: application/json' \
  -d "{\"criterionRuleId\":$RULE_ID}"
```

Activation response:

```json
{
  "status": "SUCCESS",
  "data": {
    "tierCode": "GOLD",
    "configType": "CRITERIA",
    "previousVersionId": 2,
    "activeVersionId": 4,
    "activatedAt": "..."
  }
}
```

App logs:

```
[admin_config] created criterion rule id: 4, createdBy: growth-team
[admin_config] activated criterion tier code: GOLD, prev id: 2, new id: 4
[cache] invalidating tier config code: GOLD
[tier_config] invalidated cache code: GOLD, removed: true
[tier_config] cache miss code: GOLD, loading from db   ← next read repopulates
```

`GET /tiers` now returns the new rule tree for GOLD. Rolling back is the same call with the previous id:

```bash
curl -s -X POST $BASE/admin/tiers/GOLD/activate-criteria \
  -H 'content-type: application/json' \
  -d '{"criterionRuleId":2}'
```

The `criterion_rule` rows are append-only — every version is preserved.

## 9. Admin — create + activate new benefits

Identical shape, different payload:

```bash
BEN_ID=$(curl -s -X POST $BASE/admin/benefits \
  -H 'content-type: application/json' \
  -d '{
    "benefits": [
      {"type":"FREE_DELIVERY","params":{"minOrderValue":200}},
      {"type":"EXTRA_DISCOUNT","params":{"percent":12,"categories":["FOOD"]}}
    ],
    "description":"slimmer gold benefits",
    "createdBy":"growth-team"
  }' | jq -r '.data.id')

curl -s -X POST $BASE/admin/tiers/GOLD/activate-benefits \
  -H 'content-type: application/json' \
  -d "{\"benefitConfigId\":$BEN_ID}"
```

Both writes are validated against the in-app registries (`CriterionEngine.validate()`, `BenefitFactory.validate()`) before persisting — bad config is rejected with `400 VALIDATION_FAILED` at write time, never reaches the cache or runtime.

## 10. Manual upgrade — immediate, prorated

```bash
curl -s -X POST $BASE/subscriptions/$SUB_ID/change-tier \
  -H 'content-type: application/json' \
  -d '{"newTierCode":"PLATINUM"}'
```

Response:

```json
{
  "status": "SUCCESS",
  "data": {
    "subscriptionId": 1,
    "transition": "UPGRADE",
    "previousTierCode": "GOLD",
    "newTierCode": "PLATINUM",
    "appliedImmediately": true,
    "proratedCharge": 1494.89,
    "effectiveFrom": "..."
  }
}
```

Both `purchasedTierCode` and `effectiveTierCode` move to Platinum. `pricePaid` is bumped by the prorated delta. `version` increments (optimistic lock fired).

## 11. Manual downgrade — scheduled to end of period

```bash
curl -s -X POST $BASE/subscriptions/$SUB_ID/change-tier \
  -H 'content-type: application/json' \
  -d '{"newTierCode":"GOLD"}'
```

Response:

```json
{
  "transition": "DOWNGRADE",
  "previousTierCode": "PLATINUM",
  "newTierCode": "GOLD",
  "scheduledTierCode": "GOLD",
  "appliedImmediately": false,
  "proratedCharge": 0,
  "scheduledFor": "<period end>"
}
```

The subscription transitions to status `PENDING_DOWNGRADE`. `effectiveTierCode` stays at Platinum (user keeps the benefits she paid for through the period). `scheduledTierCode` is what will take effect on `endDate`.

## 12. Cancel — also end-of-period

```bash
curl -s -X POST $BASE/subscriptions/$SUB_ID/cancel
```

Status → `CANCELLED_AT_PERIOD_END`, `autoRenew` → `false`. Benefits live until `endDate`. Re-cancelling is idempotent (returns the same state).

## 13. Tier sweep — re-evaluate everyone

```bash
curl -s -X POST $BASE/admin/tier-sweep
```

```json
{ "status": "SUCCESS", "data": { "subscriptionsEvaluated": 1, "tierChanges": 0, "durationMs": 25 } }
```

Iterates all subscriptions in `ACTIVE` or `PENDING_DOWNGRADE` and re-runs `TierEvaluationService.evaluate(userId)` for each — same path the order listener uses. Demotes a user whose activity has dropped below their effective tier, never below the purchased floor. Cancelled / expired subs are skipped.

To see a demotion in action: simulate a quiet month by deleting recent orders, then re-sweep.

```bash
PGPASSWORD=membership psql -h localhost -U membership -d membership \
  -c "DELETE FROM orders WHERE user_id = 100;"

curl -s -X POST $BASE/admin/tier-sweep
curl -s $BASE/users/100/subscription | jq '{purchasedTierCode, effectiveTierCode}'
```

Effective tier falls back to the purchased tier — never below.

## 14. Error responses

**404 — resource missing**

```bash
curl -s -i -X POST $BASE/subscriptions \
  -H 'content-type: application/json' \
  -d '{"userId":9999,"planCode":"YEARLY","tierCode":"GOLD"}'
```

```
HTTP/1.1 404
{"status":"FAILURE","error":{"code":"NOT_FOUND","message":"user not found id: 9999"}}
```

**400 — bean validation (field errors collected)**

```bash
curl -s -i -X POST $BASE/subscriptions \
  -H 'content-type: application/json' \
  -d '{"userId":101}'
```

```
HTTP/1.1 400
{"status":"FAILURE","error":{
  "code":"VALIDATION_FAILED","message":"request body invalid",
  "fieldErrors":{"planCode":"planCode is required","tierCode":"tierCode is required"}}}
```

**409 — duplicate live subscription**

```bash
# subscribe Riya a second time while her first sub is live
curl -s -i -X POST $BASE/subscriptions \
  -H 'content-type: application/json' \
  -d '{"userId":100,"planCode":"MONTHLY","tierCode":"SILVER"}'
```

```
HTTP/1.1 409
{"status":"FAILURE","error":{"code":"CONFLICT","message":"user already has a live subscription id: 1"}}
```

**400 — admin posts a malformed rule tree**

```bash
curl -s -i -X POST $BASE/admin/criteria \
  -H 'content-type: application/json' \
  -d '{"ruleTree":{"op":"AND","children":[{"leaf":"UNKNOWN_TYPE","params":{}}]}}'
```

```
HTTP/1.1 400
{"status":"FAILURE","error":{"code":"VALIDATION_FAILED","message":"unknown criterion type: UNKNOWN_TYPE"}}
```

The rule is rejected before persisting — bad config never enters the cache or runtime path.

---

## Inspecting the database

```bash
PGPASSWORD=membership psql -h localhost -U membership -d membership

\dt
SELECT code, active_criterion_rule_id, active_benefit_config_id FROM membership_tier ORDER BY rank;
SELECT * FROM subscription;
SELECT id, type, from_tier_id, to_tier_id, reason, occurred_at
  FROM subscription_event ORDER BY occurred_at;
SELECT id, description, created_at FROM criterion_rule;
SELECT id, description, created_at FROM benefit_config;
```

Audit after a full run looks like:

```
 id |      type       | from | to |                              reason                               
----+-----------------+------+----+-------------------------------------------------------------------
  1 | CREATED         |      |  2 | initial subscription, plan: YEARLY, tier: GOLD
  2 | AUTO_PROMOTED   |    2 |  3 | auto-promoted by tier rule
  3 | TIER_UPGRADED   |    3 |  3 | tier upgrade applied immediately, prorated charge: 1494.89
  4 | TIER_DOWNGRADED |    3 |  2 | downgrade scheduled at period end: ...
  5 | CANCELLED       |      |    | cancellation scheduled at period end: ...
```

## Inspecting Redis

```bash
redis-cli KEYS 'tier:config:*'
redis-cli GET  'tier:config:GOLD' | jq
redis-cli DEL  'tier:config:GOLD'   # forces a miss on next read
```

The cache holds one key per tier — the full config (metadata + parsed rule tree + benefits) serialized as JSON. Activation events delete the key; the next read populates from Postgres.

## Cleanup

```bash
# stop the app (Ctrl-C in the running shell)

# if you used Docker:
docker compose down -v

# if you used local PG:
psql -h localhost -d postgres -c "DROP DATABASE membership;"
psql -h localhost -d postgres -c "DROP ROLE membership;"
redis-cli DEL $(redis-cli KEYS 'tier:config:*' | xargs)
```

# Business walkthrough

A story of one user, **Riya** (user id 100, cohort `EARLY_ADOPTER`), moving through the membership lifecycle.

Use this alongside [TESTING.md](TESTING.md) if you want to replay it with curl.

---

## 1. Riya subscribes to Gold yearly

```
POST /api/v1/subscriptions
{ "userId": 100, "planCode": "YEARLY", "tierCode": "GOLD" }
```

Server-side:
- Yearly plan base price = ₹1499. Gold's multiplier = 1.5. Price paid = **₹2248.50**.
- `subscription` row created: `purchasedTier=GOLD`, `effectiveTier=GOLD`, `status=ACTIVE`, valid 365 days.
- `subscription_event` row written: `CREATED`.

Riya's `effective_tier_id` matches her `purchased_tier_id` — she gets Gold benefits.

## 2. Riya places a few orders — no tier change yet

Each `POST /api/v1/users/100/orders` persists an order and fires an `OrderPlacedEvent`. An `AFTER_COMMIT` listener runs the tier evaluator inline before the response returns, so the call comes back with the order created and the tier already up to date.

After **4 orders** in the past 30 days totaling ₹8000:
- Gold criterion needs `count ≥ 5 AND value ≥ 10000` (from `criterion_rule` id=2).
- 4 < 5 fails → Gold not qualifying.
- Platinum needs even more → not qualifying.
- Silver always qualifies (empty AND).
- `qualifyingTier = SILVER`. `target = max(purchased=GOLD, qualifying=SILVER) = GOLD`.
- Already at Gold. **No change**, no audit row.

## 3. Riya places a 5th order — qualifies for Gold (but already there)

5 orders × avg ₹2k = ₹10500. Now Gold's rule matches.
- `qualifyingTier = GOLD`. `target = max(GOLD, GOLD) = GOLD`. **Still no change**.

The purchased-as-floor logic means earning the tier you already paid for does nothing visible.

## 4. Riya goes on a spree — promotes to Platinum

By month-end she's placed 12 orders totaling ₹62000.
- Platinum rule (criterion_rule id=3): `count ≥ 10 AND (value ≥ 50000 OR cohort in [VIP, EARLY_ADOPTER])`.
- 12 ≥ 10 ✅. 62000 ≥ 50000 ✅. Rule matches.
- `qualifyingTier = PLATINUM`. `target = max(GOLD, PLATINUM) = PLATINUM`.
- `effective_tier_id` updated to Platinum.
- `subscription_event` row written: `AUTO_PROMOTED` with `metadata = {"qualifyingTier":"PLATINUM","criterionRuleId":3}`.
- `TierChangedEvent` published — `TierChangedNotifier` logs it. In a real system this would push a notification, grant a coupon, etc.

Riya's `purchased_tier_id` is still Gold — she never paid for Platinum. Her checkout preview now shows Platinum benefits.

## 5. Checkout preview shows the upgrade in action

```
POST /api/v1/users/100/checkout/preview
{
  "items": [{"category":"FOOD","price":1000},{"category":"GROCERY","price":1500}],
  "deliveryFee": 50
}
```

With Platinum's benefits (`benefit_config` id=3):
- `FREE_DELIVERY` `minOrderValue=0` → applies, saves ₹50.
- `EXTRA_DISCOUNT` 15% on `*` (all) → 15% × 2500 = ₹375.
- `EXCLUSIVE_DEALS`, `EARLY_ACCESS`, `PRIORITY_SUPPORT` → non-monetary, included in metadata.

Total savings ₹425. Final payable ₹2125.

## 6. A quiet month — sweep demotes her back to Gold

A month passes. Riya only places 2 orders. Admin (or a cron) hits:

```
POST /api/v1/admin/tier-sweep
```

For each active subscription, the sweep re-evaluates:
- Riya's stats: 2 orders, ₹3000, EARLY_ADOPTER cohort.
- Platinum needs `count ≥ 10` → fails.
- Gold needs `count ≥ 5` → fails.
- Silver matches (empty AND).
- `qualifyingTier = SILVER`. `target = max(purchased=GOLD, SILVER) = GOLD`.
- Effective was Platinum, target is Gold → **AUTO_DEMOTED**.

She never falls below Gold — the purchased tier is the floor.

## 7. Growth team changes Gold's criteria (admin flow)

The team decides Gold should be easier to earn for a Q3 push.

```
POST /api/v1/admin/criteria
{
  "ruleTree": {
    "op": "AND",
    "children": [
      { "leaf": "MIN_ORDER_COUNT", "params": { "count": 3, "windowDays": 30 } },
      { "leaf": "MIN_ORDER_VALUE", "params": { "amount": 6000, "windowDays": 30 } }
    ]
  },
  "description": "easier Gold for Q3",
  "createdBy": "growth-team"
}
→ 201 { "id": 4 }
```

Just stored — not active anywhere. Then:

```
POST /api/v1/admin/tiers/GOLD/activate-criteria
{ "criterionRuleId": 4 }
```

In one transaction:
- `UPDATE membership_tier SET active_criterion_rule_id = 4 WHERE code = 'GOLD'`.
- After commit, `TierConfigChangedEvent("GOLD")` fires.
- `TierConfigCacheInvalidator` drops `tier:config:GOLD` from Redis.
- Next read repopulates with the new rule.

Rollback (e.g., the new rule is too generous) is a single call:
```
POST /api/v1/admin/tiers/GOLD/activate-criteria
{ "criterionRuleId": 2 }   // the previous version
```

## 8. Riya upgrades to Platinum manually

She enjoys the Platinum benefits and decides to pay for them.

```
POST /api/v1/subscriptions/<her sub id>/change-tier
{ "newTierCode": "PLATINUM" }
```

Server-side:
- Plan = Yearly, ₹1499. Days remaining ≈ 200 of 365.
- Old price = 1499 × 1.5 = 2248.50. New price = 1499 × 2.5 = 3747.50. Delta = 1499.
- Prorated charge = 1499 × 200/365 ≈ **₹821.37**.
- `purchased_tier_id` → Platinum, `effective_tier_id` → Platinum, any pending downgrade cleared, status → ACTIVE.
- `subscription_event`: `TIER_UPGRADED`.

She is now Platinum on the floor — auto-evaluation can't put her below it.

## 9. Riya cancels later

```
POST /api/v1/subscriptions/<her sub id>/cancel
```

- `status → CANCELLED_AT_PERIOD_END`, `auto_renew → false`.
- Benefits stay live until `end_date`.
- `subscription_event`: `CANCELLED`.

When `end_date` passes, a renewal/expiration job (not implemented in this build — out of scope) would flip her to `EXPIRED`.

---

## What this walkthrough demonstrates

| Concept | Where it shows up |
|---|---|
| Plan × Tier decoupling | step 1 (Yearly × Gold) |
| Purchased tier as floor | steps 2, 3, 6 |
| Auto-promotion on activity | step 4 |
| After-commit tier eval | step 4 (under the hood) |
| Audit trail via `subscription_event` | every state change |
| Auto-demotion via sweep | step 6 |
| Versioned criteria, runtime swap | step 7 |
| Forward-FK activation model | step 7 |
| Redis cache invalidation via event | step 7 |
| Prorated upgrade | step 8 |
| End-of-period cancellation | step 9 |

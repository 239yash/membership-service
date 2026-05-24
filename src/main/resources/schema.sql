-- ----------------------------------------------------------------
-- membership service schema
-- run order: this file runs before hibernate validation
-- ----------------------------------------------------------------

-- drop in reverse dependency order so this file is idempotent on a fresh db
DROP TABLE IF EXISTS subscription_event CASCADE;
DROP TABLE IF EXISTS subscription CASCADE;
DROP TABLE IF EXISTS orders CASCADE;
DROP TABLE IF EXISTS user_account CASCADE;
DROP TABLE IF EXISTS membership_tier CASCADE;
DROP TABLE IF EXISTS membership_plan CASCADE;
DROP TABLE IF EXISTS criterion_rule CASCADE;
DROP TABLE IF EXISTS benefit_config CASCADE;

-- ----------------------------------------------------------------
-- billing cadence and price
-- ----------------------------------------------------------------
CREATE TABLE membership_plan (
    id                  BIGSERIAL PRIMARY KEY,
    code                VARCHAR(32)  UNIQUE NOT NULL,
    name                VARCHAR(64)  NOT NULL,
    billing_frequency   VARCHAR(16)  NOT NULL,
    duration_days       INT          NOT NULL,
    base_price          NUMERIC(10,2) NOT NULL,
    active              BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ----------------------------------------------------------------
-- a criterion rule tree, tier-agnostic, immutable once written
-- id is the version
-- ----------------------------------------------------------------
CREATE TABLE criterion_rule (
    id            BIGSERIAL PRIMARY KEY,
    rule_tree     JSONB        NOT NULL,
    description   TEXT,
    created_by    VARCHAR(64),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ----------------------------------------------------------------
-- a benefit config (array of benefits), tier-agnostic, immutable
-- id is the version
-- ----------------------------------------------------------------
CREATE TABLE benefit_config (
    id            BIGSERIAL PRIMARY KEY,
    benefits      JSONB        NOT NULL,
    description   TEXT,
    created_by    VARCHAR(64),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ----------------------------------------------------------------
-- tier metadata, plus forward fk pointers to the active rule + benefits
-- admin flips a pointer to activate a different version
-- ----------------------------------------------------------------
CREATE TABLE membership_tier (
    id                          BIGSERIAL PRIMARY KEY,
    code                        VARCHAR(32) UNIQUE NOT NULL,
    name                        VARCHAR(64) NOT NULL,
    rank                        INT NOT NULL,
    price_multiplier            NUMERIC(4,2) NOT NULL,
    active                      BOOLEAN NOT NULL DEFAULT TRUE,
    active_criterion_rule_id    BIGINT REFERENCES criterion_rule(id),
    active_benefit_config_id    BIGINT REFERENCES benefit_config(id),
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ----------------------------------------------------------------
-- users + cohorts (cohorts as a text array, simple and queryable)
-- ----------------------------------------------------------------
CREATE TABLE user_account (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(128),
    email       VARCHAR(128) UNIQUE,
    cohorts     TEXT[]       NOT NULL DEFAULT '{}',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ----------------------------------------------------------------
-- live subscription, optimistic-locked via version column
-- one active row per user (enforced by partial unique index below)
-- ----------------------------------------------------------------
CREATE TABLE subscription (
    id                      BIGSERIAL PRIMARY KEY,
    user_id                 BIGINT       NOT NULL REFERENCES user_account(id),
    plan_id                 BIGINT       NOT NULL REFERENCES membership_plan(id),
    purchased_tier_id       BIGINT       NOT NULL REFERENCES membership_tier(id),
    effective_tier_id       BIGINT       NOT NULL REFERENCES membership_tier(id),
    scheduled_tier_id       BIGINT       REFERENCES membership_tier(id),
    status                  VARCHAR(32)  NOT NULL,
    start_date              TIMESTAMPTZ  NOT NULL,
    end_date                TIMESTAMPTZ  NOT NULL,
    auto_renew              BOOLEAN      NOT NULL DEFAULT TRUE,
    price_paid              NUMERIC(10,2) NOT NULL,
    version                 BIGINT       NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- only one live subscription per user
CREATE UNIQUE INDEX uq_one_live_sub_per_user
    ON subscription (user_id)
    WHERE status IN ('ACTIVE', 'PENDING_DOWNGRADE', 'CANCELLED_AT_PERIOD_END');

-- ----------------------------------------------------------------
-- append-only audit of every state change on a subscription
-- ----------------------------------------------------------------
CREATE TABLE subscription_event (
    id              BIGSERIAL PRIMARY KEY,
    subscription_id BIGINT       NOT NULL REFERENCES subscription(id),
    type            VARCHAR(32)  NOT NULL,
    from_tier_id    BIGINT       REFERENCES membership_tier(id),
    to_tier_id      BIGINT       REFERENCES membership_tier(id),
    reason          TEXT,
    metadata        JSONB,
    occurred_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_sub_event_sub_time ON subscription_event (subscription_id, occurred_at DESC);

-- ----------------------------------------------------------------
-- orders, minimal — just enough to feed tier evaluation
-- ----------------------------------------------------------------
CREATE TABLE orders (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES user_account(id),
    amount      NUMERIC(10,2) NOT NULL,
    category    VARCHAR(64),
    placed_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_orders_user_placed ON orders (user_id, placed_at DESC);

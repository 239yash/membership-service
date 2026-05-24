-- ----------------------------------------------------------------
-- seed data
-- runs after schema.sql, before hibernate validation
-- explicit ids so fk pointers are deterministic
-- ----------------------------------------------------------------

-- plans
INSERT INTO membership_plan (id, code, name, billing_frequency, duration_days, base_price, active) VALUES
    (1, 'MONTHLY',   'Monthly Pass',   'MONTHLY',   30,  199.00, TRUE),
    (2, 'QUARTERLY', 'Quarterly Pass', 'QUARTERLY', 90,  499.00, TRUE),
    (3, 'YEARLY',    'Yearly Pass',    'YEARLY',    365, 1499.00, TRUE);

-- criterion rules (one per tier as the starting active config)
-- silver: empty AND => always matches (the floor)
INSERT INTO criterion_rule (id, rule_tree, description, created_by) VALUES
    (1, '{"op":"AND","children":[]}'::jsonb,
        'silver floor rule, always matches', 'seed');

-- gold: at least 5 orders in 30d AND at least 10000 in 30d
INSERT INTO criterion_rule (id, rule_tree, description, created_by) VALUES
    (2, '{
            "op":"AND",
            "children":[
                {"leaf":"MIN_ORDER_COUNT","params":{"count":5,"windowDays":30}},
                {"leaf":"MIN_ORDER_VALUE","params":{"amount":10000,"windowDays":30}}
            ]
         }'::jsonb,
        'gold default rule', 'seed');

-- platinum: at least 10 orders in 30d AND (50000 in 30d OR in vip/early_adopter cohort)
INSERT INTO criterion_rule (id, rule_tree, description, created_by) VALUES
    (3, '{
            "op":"AND",
            "children":[
                {"leaf":"MIN_ORDER_COUNT","params":{"count":10,"windowDays":30}},
                {"op":"OR","children":[
                    {"leaf":"MIN_ORDER_VALUE","params":{"amount":50000,"windowDays":30}},
                    {"leaf":"COHORT_MEMBERSHIP","params":{"cohorts":["VIP","EARLY_ADOPTER"]}}
                ]}
            ]
         }'::jsonb,
        'platinum default rule', 'seed');

-- benefit configs (one per tier as the starting active config)
INSERT INTO benefit_config (id, benefits, description, created_by) VALUES
    (1, '[
            {"type":"FREE_DELIVERY","params":{"minOrderValue":500}}
        ]'::jsonb,
        'silver benefits', 'seed');

INSERT INTO benefit_config (id, benefits, description, created_by) VALUES
    (2, '[
            {"type":"FREE_DELIVERY","params":{"minOrderValue":300}},
            {"type":"EXTRA_DISCOUNT","params":{"percent":10,"categories":["FOOD","GROCERY"]}},
            {"type":"EXCLUSIVE_DEALS","params":{"dealIds":["DEAL_GOLD_001","DEAL_GOLD_002"]}}
        ]'::jsonb,
        'gold benefits', 'seed');

INSERT INTO benefit_config (id, benefits, description, created_by) VALUES
    (3, '[
            {"type":"FREE_DELIVERY","params":{"minOrderValue":0}},
            {"type":"EXTRA_DISCOUNT","params":{"percent":15,"categories":["*"]}},
            {"type":"EXCLUSIVE_DEALS","params":{"dealIds":["DEAL_PLAT_001","DEAL_PLAT_002"]}},
            {"type":"EARLY_ACCESS","params":{"hoursEarly":24}},
            {"type":"PRIORITY_SUPPORT","params":{"slaMinutes":5}}
        ]'::jsonb,
        'platinum benefits', 'seed');

-- tiers, pointing at their starting active rule + benefit ids
INSERT INTO membership_tier (id, code, name, rank, price_multiplier, active, active_criterion_rule_id, active_benefit_config_id) VALUES
    (1, 'SILVER',   'Silver',   1, 1.00, TRUE, 1, 1),
    (2, 'GOLD',     'Gold',     2, 1.50, TRUE, 2, 2),
    (3, 'PLATINUM', 'Platinum', 3, 2.50, TRUE, 3, 3);

-- sample users
INSERT INTO user_account (id, name, email, cohorts) VALUES
    (100, 'Riya',  'riya@example.com',  ARRAY['EARLY_ADOPTER']),
    (101, 'Arjun', 'arjun@example.com', ARRAY[]::TEXT[]),
    (102, 'Meera', 'meera@example.com', ARRAY['VIP']);

-- reset sequences past the explicit ids so new inserts get fresh ones
SELECT setval('membership_plan_id_seq', (SELECT max(id) FROM membership_plan));
SELECT setval('criterion_rule_id_seq',  (SELECT max(id) FROM criterion_rule));
SELECT setval('benefit_config_id_seq',  (SELECT max(id) FROM benefit_config));
SELECT setval('membership_tier_id_seq', (SELECT max(id) FROM membership_tier));
SELECT setval('user_account_id_seq',    (SELECT max(id) FROM user_account));

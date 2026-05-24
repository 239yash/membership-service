package com.work.membership_service.engine.criterion;

import com.fasterxml.jackson.databind.JsonNode;
import com.work.membership_service.engine.stats.UserStats;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// recursive evaluator for the rule tree
// tree node shapes:
//   branch -> { "op": "AND"|"OR"|"NOT", "children": [...] }
//   leaf   -> { "leaf": "<type>", "params": { ... } }
@Component
@RequiredArgsConstructor
@Slf4j
public class CriterionEngine {

    private static final int MAX_DEPTH = 6;

    private final CriterionDefinitionRegistry registry;

    // evaluate against a user's stats; returns true if the tree matches
    public boolean evaluate(JsonNode tree, UserStats stats) {
        return evaluate(tree, stats, 0);
    }

    // validate tree shape and that every leaf type and its params are good
    // called at admin-create time so bad rules never reach evaluation
    public void validate(JsonNode tree) {
        validate(tree, 0);
    }

    private boolean evaluate(JsonNode tree, UserStats stats, int depth) {
        if (depth > MAX_DEPTH) {
            throw new IllegalArgumentException("rule tree too deep, max " + MAX_DEPTH);
        }

        if (isLeaf(tree)) {
            String type = tree.get("leaf").asText();
            JsonNode params = tree.get("params");
            boolean result = registry.get(type).evaluate(stats, params);
            log.debug("[criterion_eval] leaf type: {}, user id: {}, result: {}", type, stats.userId(), result);
            return result;
        }

        String op = requireOp(tree);
        JsonNode children = tree.get("children");

        return switch (op) {
            case "AND" -> {
                // empty AND matches everything; used by the silver floor rule
                boolean all = true;
                for (JsonNode c : children) {
                    if (!evaluate(c, stats, depth + 1)) {
                        all = false;
                        break;
                    }
                }
                yield all;
            }
            case "OR" -> {
                boolean any = false;
                for (JsonNode c : children) {
                    if (evaluate(c, stats, depth + 1)) {
                        any = true;
                        break;
                    }
                }
                yield any;
            }
            case "NOT" -> {
                if (children == null || children.size() != 1) {
                    throw new IllegalArgumentException("NOT must have exactly one child");
                }
                yield !evaluate(children.get(0), stats, depth + 1);
            }
            default -> throw new IllegalArgumentException("unknown op: " + op);
        };
    }

    private void validate(JsonNode tree, int depth) {
        if (depth > MAX_DEPTH) {
            throw new IllegalArgumentException("rule tree too deep, max " + MAX_DEPTH);
        }
        if (tree == null || tree.isNull()) {
            throw new IllegalArgumentException("rule tree node is null");
        }

        if (isLeaf(tree)) {
            String type = tree.get("leaf").asText();
            JsonNode params = tree.get("params");
            registry.get(type).validateParams(params);
            return;
        }

        String op = requireOp(tree);
        JsonNode children = tree.get("children");
        if (!"AND".equals(op) && !"OR".equals(op) && !"NOT".equals(op)) {
            throw new IllegalArgumentException("unknown op: " + op);
        }
        if (children == null || !children.isArray()) {
            throw new IllegalArgumentException("op " + op + " requires a children array");
        }
        if ("NOT".equals(op) && children.size() != 1) {
            throw new IllegalArgumentException("NOT must have exactly one child");
        }
        for (JsonNode c : children) {
            validate(c, depth + 1);
        }
    }

    private static boolean isLeaf(JsonNode tree) {
        return tree.has("leaf");
    }

    private static String requireOp(JsonNode tree) {
        if (!tree.has("op")) {
            throw new IllegalArgumentException("node is neither a leaf nor a branch");
        }
        return tree.get("op").asText();
    }
}

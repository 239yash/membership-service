package com.work.membership_service.engine.criterion;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// collects every CriterionDefinition bean into a type-keyed map
// adding a new criterion type means adding one bean; nothing else changes
@Component
@Slf4j
public class CriterionDefinitionRegistry {

    private final Map<String, CriterionDefinition> byType;

    public CriterionDefinitionRegistry(List<CriterionDefinition> defs) {
        Map<String, CriterionDefinition> map = new HashMap<>();
        for (CriterionDefinition d : defs) {
            CriterionDefinition prev = map.put(d.type(), d);
            if (prev != null) {
                throw new IllegalStateException("duplicate criterion type registered: " + d.type());
            }
        }
        this.byType = Map.copyOf(map);
        log.info("[criterion_registry] registered types: {}", this.byType.keySet());
    }

    public CriterionDefinition get(String type) {
        CriterionDefinition d = byType.get(type);
        if (d == null) {
            throw new IllegalArgumentException("unknown criterion type: " + type);
        }
        return d;
    }

    public Set<String> types() {
        return byType.keySet();
    }
}

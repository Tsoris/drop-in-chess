package com.dropinchess.positionenrichment;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Validates generated context independently of the API's structured-output enforcement. */
final class EnrichmentContextValidator {
    private EnrichmentContextValidator() {}

    static void validate(Map<String, Object> context, String id, Set<String> allowedEvidenceIds) {
        if (context.size() != 3) throw new IllegalArgumentException("Unexpected context fields for " + id);
        Map<String, Object> opening = object(context.get("openingContext"), "openingContext for " + id);
        Map<String, Object> guide = object(context.get("positionGuide"), "positionGuide for " + id);
        Map<String, Object> plans = object(context.get("possiblePlans"), "possiblePlans for " + id);
        Map<String, Object> whitePlan = object(plans.get("white"), "White plan for " + id);
        Map<String, Object> blackPlan = object(plans.get("black"), "Black plan for " + id);
        string(opening.get("summary"), "opening summary for " + id);
        string(guide.get("summary"), "position summary for " + id);
        string(whitePlan.get("summary"), "White plan summary for " + id);
        string(blackPlan.get("summary"), "Black plan summary for " + id);
        evidenceIds(guide.get("evidenceIds"), "position guide", id, allowedEvidenceIds);
        evidenceIds(whitePlan.get("evidenceIds"), "White plan", id, allowedEvidenceIds);
        evidenceIds(blackPlan.get("evidenceIds"), "Black plan", id, allowedEvidenceIds);
        Object rawThemes = guide.get("themes");
        if (!(rawThemes instanceof List<?> themes) || themes.size() < 2 || themes.size() > 5
                || themes.stream().anyMatch(theme -> !(theme instanceof String text) || text.isBlank())) {
            throw new IllegalArgumentException("Expected two to five themes for " + id);
        }
    }

    private static void evidenceIds(Object value, String section, String id, Set<String> allowed) {
        if (!(value instanceof List<?> ids) || ids.isEmpty() || ids.size() > 5) {
            throw new IllegalArgumentException("Expected one to five evidence IDs for " + section + " in " + id);
        }
        for (Object rawId : ids) {
            if (!(rawId instanceof String evidenceId) || !allowed.contains(evidenceId)) {
                throw new IllegalArgumentException("Unsupported evidence ID " + rawId + " for " + section + " in " + id);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value, String name) {
        if (!(value instanceof Map<?, ?>)) throw new IllegalArgumentException("Missing or invalid " + name);
        return (Map<String, Object>) value;
    }

    private static String string(Object value, String name) {
        if (!(value instanceof String text) || text.isBlank()) throw new IllegalArgumentException("Missing or invalid " + name);
        return text;
    }
}

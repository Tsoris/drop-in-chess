package com.dropinchess.positionenrichment;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds a second Batch API file that fact-checks generated enrichment text. */
final class EnrichmentVerificationBatchBuilder {
    private final JsonMapper mapper = JsonMapper.builder().build();

    int build(Path positionsFile, Path generationResults, Path outputFile, String model) throws IOException {
        if (model == null || model.isBlank()) throw new IllegalArgumentException("model is required");
        Map<String, Object> collection = mapper.readValue(positionsFile.toFile(), new TypeReference<>() {});
        Map<String, Map<String, Object>> positions = indexPositions(collection);
        List<EnrichmentBatchResults.Generated> candidates = EnrichmentBatchResults.read(generationResults, "position-");

        Path output = outputFile.toAbsolutePath();
        if (output.getParent() != null) Files.createDirectories(output.getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            for (EnrichmentBatchResults.Generated candidate : candidates) {
                Map<String, Object> position = positions.get(candidate.id());
                if (position == null) throw new IllegalArgumentException("Generation result does not match position " + candidate.id());
                String fen = EnrichmentBatchResults.string(position.get("fen"), "FEN for " + candidate.id());
                EnrichmentFacts.FactSet facts = EnrichmentFacts.fromFen(fen);
                EnrichmentContextValidator.validate(candidate.output(), candidate.id(), facts.allowedEvidenceIds());
                writer.write(mapper.writeValueAsString(request(position, candidate, facts, model)));
                writer.newLine();
            }
        }
        return candidates.size();
    }

    private Map<String, Map<String, Object>> indexPositions(Map<String, Object> collection) {
        Object rawPositions = collection.get("positions");
        if (!(rawPositions instanceof List<?> positionList) || positionList.isEmpty()) {
            throw new IllegalArgumentException("Collection has no positions");
        }
        Map<String, Map<String, Object>> positions = new HashMap<>();
        for (Object value : positionList) {
            Map<String, Object> position = EnrichmentBatchResults.object(value, "position");
            String id = EnrichmentBatchResults.string(position.get("id"), "position ID");
            if (positions.put(id, position) != null) throw new IllegalArgumentException("Duplicate position ID " + id);
        }
        return positions;
    }

    private Map<String, Object> request(Map<String, Object> position, EnrichmentBatchResults.Generated candidate,
                                        EnrichmentFacts.FactSet facts, String model) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("input", List.of(
                Map.of("role", "system", "content", systemPrompt()),
                Map.of("role", "user", "content", verificationPrompt(position, candidate.output(), facts))));
        body.put("reasoning", Map.of("effort", "low"));
        body.put("max_output_tokens", 1200);
        body.put("text", Map.of("format", responseFormat()));
        return Map.of(
                "custom_id", "verify-position-" + candidate.id(),
                "method", "POST",
                "url", "/v1/responses",
                "body", body);
    }

    private String systemPrompt() {
        return """
                You are a strict factual reviewer for short chess explanations. Check every statement against the
                supplied verified evidence, opening metadata, and move history. Reject unsupported or contradictory
                material claims, check status, king locations, bishop-square colors, tactical claims, best moves,
                forcing lines, engine evaluations, and forced results. Plans may be broad and plausible, but every
                cited evidence ID must actually support the plan. Approve only when all three sections are safe to
                show to a player. Return concise issue descriptions when rejecting.
                """.strip();
    }

    private String verificationPrompt(Map<String, Object> position, Map<String, Object> candidate,
                                      EnrichmentFacts.FactSet facts) throws IOException {
        Map<String, Object> source = EnrichmentBatchResults.object(position.get("source"), "position source");
        String opening = optionalString(source.get("opening"), "unspecified");
        String eco = optionalString(source.get("eco"), "unknown");
        String variation = optionalString(source.get("variation"), "none");
        Object rawMoves = source.get("movesSan");
        if (!(rawMoves instanceof List<?> moves)) throw new IllegalArgumentException("Missing move history");
        return """
                FEN: %s
                Phase: %s

                Verified evidence:
                %s
                [OPENING_METADATA] Opening=%s; ECO=%s; Variation=%s
                [MOVE_HISTORY] %s

                Candidate context:
                %s

                Set approved=true only if every factual statement is supported, every evidence ID is relevant,
                the checked side acknowledges an immediate check before longer-term plans, and the text contains
                no tactical solution, best move, forcing line, engine evaluation, or forced-result claim.
                """.formatted(
                position.get("fen"), position.get("phase"), facts.promptText(), opening, eco, variation,
                moves.stream().map(String::valueOf).reduce((left, right) -> left + " " + right).orElse("none"),
                mapper.writeValueAsString(candidate));
    }

    private Map<String, Object> responseFormat() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of(
                "approved", Map.of("type", "boolean"),
                "issues", Map.of("type", "array", "items", Map.of("type", "string"), "maxItems", 5)));
        schema.put("required", List.of("approved", "issues"));
        schema.put("additionalProperties", false);
        return Map.of("type", "json_schema", "name", "enrichment_verification", "strict", true, "schema", schema);
    }

    private String optionalString(Object value, String fallback) {
        return value instanceof String text && !text.isBlank() ? text : fallback;
    }
}

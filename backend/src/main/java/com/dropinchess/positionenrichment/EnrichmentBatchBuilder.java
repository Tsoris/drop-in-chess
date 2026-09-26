package com.dropinchess.positionenrichment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Builds an OpenAI Batch API JSONL file without making a network request. */
public final class EnrichmentBatchBuilder {
    static final String PROMPT_VERSION = "5";
    static final String DEFAULT_MODEL = "gpt-6-luna";

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EnrichmentPosition(String id, String phase, String endgameType, String fen, Source source, Object context) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        record Source(List<String> movesSan, int ply, String eco, String opening, String variation) {}
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CollectionFile(int schemaVersion, boolean complete, List<EnrichmentPosition> positions) {}

    private final JsonMapper mapper = JsonMapper.builder().build();

    public int build(Path positionsFile, Path outputFile, int limit, String model) throws IOException {
        if (limit < 1) throw new IllegalArgumentException("limit must be positive");
        if (model == null || model.isBlank()) throw new IllegalArgumentException("model is required");

        CollectionFile collection = mapper.readValue(positionsFile.toFile(), CollectionFile.class);
        if (collection.schemaVersion() != 1 || !collection.complete()
                || collection.positions() == null || collection.positions().isEmpty()) {
            throw new IllegalArgumentException("Expected a complete, nonempty schema-version-1 collection");
        }

        List<EnrichmentPosition> selected = selectPositions(collection.positions(), limit);
        return writeRequests(selected, outputFile, model);
    }

    public int buildMissing(Path positionsFile, Path outputFile, String model) throws IOException {
        if (model == null || model.isBlank()) throw new IllegalArgumentException("model is required");
        CollectionFile collection = mapper.readValue(positionsFile.toFile(), CollectionFile.class);
        if (collection.schemaVersion() != 1 || !collection.complete()
                || collection.positions() == null || collection.positions().isEmpty()) {
            throw new IllegalArgumentException("Expected a complete, nonempty schema-version-1 collection");
        }
        List<EnrichmentPosition> missing = collection.positions().stream()
                .filter(position -> position.context() == null)
                .toList();
        if (missing.isEmpty()) throw new IllegalArgumentException("Collection has no positions missing context");
        return writeRequests(missing, outputFile, model);
    }

    int buildSelected(Path positionsFile, Path outputFile, java.util.Set<String> positionIds, String model) throws IOException {
        if (positionIds == null || positionIds.isEmpty()) throw new IllegalArgumentException("No position IDs selected");
        if (model == null || model.isBlank()) throw new IllegalArgumentException("model is required");
        CollectionFile collection = mapper.readValue(positionsFile.toFile(), CollectionFile.class);
        if (collection.positions() == null || collection.positions().isEmpty()) {
            throw new IllegalArgumentException("Collection has no positions");
        }
        List<EnrichmentPosition> selected = collection.positions().stream()
                .filter(position -> positionIds.contains(position.id())).toList();
        if (selected.size() != positionIds.size()) {
            throw new IllegalArgumentException("One or more selected position IDs are missing from the collection");
        }
        return writeRequests(selected, outputFile, model);
    }

    private int writeRequests(List<EnrichmentPosition> selected, Path outputFile, String model) throws IOException {
        Path absoluteOutput = outputFile.toAbsolutePath();
        if (absoluteOutput.getParent() != null) Files.createDirectories(absoluteOutput.getParent());
        try (BufferedWriter output = Files.newBufferedWriter(absoluteOutput, StandardCharsets.UTF_8)) {
            for (EnrichmentPosition selectedPosition : selected) {
                EnrichmentPosition position = requireValid(selectedPosition);
                output.write(mapper.writeValueAsString(batchRequest(position, model)));
                output.newLine();
            }
        }
        return selected.size();
    }

    private List<EnrichmentPosition> selectPositions(List<EnrichmentPosition> positions, int limit) {
        if (limit >= positions.size()) return List.copyOf(positions);
        int targetMiddlegames = (limit + 1) / 2;
        int targetEndgames = limit / 2;
        List<EnrichmentPosition> selected = new ArrayList<>(limit);
        positions.stream().filter(position -> "MIDDLEGAME".equals(position.phase()))
                .limit(targetMiddlegames).forEach(selected::add);

        Map<String, List<EnrichmentPosition>> endgamesByType = new LinkedHashMap<>();
        positions.stream().filter(position -> "ENDGAME".equals(position.phase())).forEach(position ->
                endgamesByType.computeIfAbsent(fallback(position.endgameType(), "UNKNOWN"), ignored -> new ArrayList<>())
                        .add(position));
        Map<String, Integer> nextIndex = new LinkedHashMap<>();
        while (selected.size() < targetMiddlegames + targetEndgames) {
            boolean added = false;
            for (var entry : endgamesByType.entrySet()) {
                int index = nextIndex.getOrDefault(entry.getKey(), 0);
                if (index < entry.getValue().size() && selected.size() < targetMiddlegames + targetEndgames) {
                    selected.add(entry.getValue().get(index));
                    nextIndex.put(entry.getKey(), index + 1);
                    added = true;
                }
            }
            if (!added) break;
        }

        if (selected.size() < limit) {
            var selectedIds = new LinkedHashSet<>(selected.stream().map(EnrichmentPosition::id).toList());
            positions.stream().filter(position -> !selectedIds.contains(position.id()))
                    .limit(limit - selected.size()).forEach(selected::add);
        }
        return List.copyOf(selected);
    }

    private EnrichmentPosition requireValid(EnrichmentPosition position) {
        if (position == null || blank(position.id()) || blank(position.phase()) || blank(position.fen())
                || position.source() == null || position.source().movesSan() == null
                || position.source().movesSan().isEmpty()) {
            throw new IllegalArgumentException("Position is missing enrichment input fields");
        }
        return position;
    }

    private Map<String, Object> batchRequest(EnrichmentPosition position, String model) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("input", List.of(
                Map.of("role", "system", "content", systemPrompt()),
                Map.of("role", "user", "content", buildPrompt(position))));
        body.put("reasoning", Map.of("effort", "low"));
        body.put("max_output_tokens", 1200);
        body.put("text", Map.of("format", responseFormat(EnrichmentFacts.fromFen(position.fen()).allowedEvidenceIds())));

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("custom_id", "position-" + position.id());
        request.put("method", "POST");
        request.put("url", "/v1/responses");
        request.put("body", body);
        return request;
    }

    private Map<String, Object> responseFormat(java.util.Set<String> allowedEvidenceIds) {
        Map<String, Object> summary = objectSchema(Map.of("summary", Map.of("type", "string")), List.of("summary"));
        Map<String, Object> evidenceIds = Map.of(
                "type", "array",
                "items", Map.of("type", "string", "enum", allowedEvidenceIds.stream().sorted().toList()),
                "minItems", 1,
                "maxItems", 5);
        Map<String, Object> guide = objectSchema(Map.of(
                "summary", Map.of("type", "string"),
                "themes", Map.of("type", "array", "items", Map.of("type", "string"),
                        "minItems", 2, "maxItems", 5),
                "evidenceIds", evidenceIds),
                List.of("summary", "themes", "evidenceIds"));
        Map<String, Object> plan = objectSchema(Map.of(
                "summary", Map.of("type", "string"),
                "evidenceIds", evidenceIds),
                List.of("summary", "evidenceIds"));
        Map<String, Object> plans = objectSchema(Map.of("white", plan, "black", plan),
                List.of("white", "black"));
        return Map.of(
                "type", "json_schema",
                "name", "position_context",
                "strict", true,
                "schema", objectSchema(Map.of(
                        "openingContext", summary,
                        "positionGuide", guide,
                        "possiblePlans", plans),
                        List.of("openingContext", "positionGuide", "possiblePlans")));
    }

    private Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        schema.put("additionalProperties", false);
        return schema;
    }

    private String systemPrompt() {
        return """
                You explain chess positions to players who are dropped into a game already in progress.
                Use only facts supported by the supplied evidence. Never contradict a verified evidence item.
                Do not reveal tactical solutions, state an engine score, name a best move, give a forcing line,
                or claim a forced result. Describe possible plans as broad ideas, not exact move instructions,
                and do not imply that a plan is engine-approved. Do not invent material, check, king-location,
                or bishop-color claims. Keep the language useful to a non-expert chess player.
                """.strip();
    }

    String buildPrompt(EnrichmentPosition position) {
        EnrichmentPosition.Source source = position.source();
        String opening = fallback(source.opening(), "an unspecified opening");
        String eco = fallback(source.eco(), "unknown");
        String variation = blank(source.variation()) ? "" : "\nVariation: " + source.variation();
        String endgame = blank(position.endgameType()) ? "" : "\nEndgame category: " + position.endgameType();

        EnrichmentFacts.FactSet facts = EnrichmentFacts.fromFen(position.fen());
        return """
                Opening: %s
                ECO: %s%s
                Phase: %s%s
                Side to move and board state are encoded in this FEN:
                %s

                Verified evidence calculated from the FEN:
                %s

                [OPENING_METADATA] Opening=%s; ECO=%s%s

                [MOVE_HISTORY] Moves played to reach the position (%d plies): %s

                Produce three independently revealable player-facing sections:
                1. openingContext.summary: one or two sentences explaining how the opening developed into this position.
                2. positionGuide.summary: two concise sentences describing the current strategic landscape,
                   including only relevant pawn structure, space, piece activity, important squares, or king safety.
                Also give two to five short positionGuide.themes labels and cite one to five evidence IDs that
                directly support the summary.
                3. possiblePlans.white and possiblePlans.black: each contains a one-sentence summary describing a broad,
                   plausible plan. Explain the goal without giving a best move, tactical answer, forcing sequence,
                   or engine evaluation, and cite one to five evidence IDs that directly support that plan.
                If CHECK_STATUS says a side is in check, that side's plan must first acknowledge that the check
                must be answered before describing any longer-term goal.
                """.formatted(
                opening, eco, variation, position.phase(), endgame, position.fen(), facts.promptText(),
                opening, eco, variation, source.ply(), String.join(" ", source.movesSan()));
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String fallback(String value, String fallback) { return blank(value) ? fallback : value; }
}

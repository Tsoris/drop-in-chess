package com.dropinchess.positionenrichment;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Validates Batch API results and adds context to a new copy of the position collection. */
public final class EnrichmentBatchImporter {
    static final String UNAVAILABLE_MESSAGE = "Unable to generate reliable positional guidance for this position.";
    public record VerifiedMergeResult(int merged, List<String> rejectedIds) {}
    private record VerificationDecision(boolean approved, List<String> issues) {}

    private final JsonMapper mapper = JsonMapper.builder().build();

    public int merge(Path positionsFile, Path batchResults, Path outputFile) throws IOException {
        Map<String, Object> collection = readObject(positionsFile);
        Object rawPositions = collection.get("positions");
        if (!(rawPositions instanceof List<?> positions) || positions.isEmpty()) {
            throw new IllegalArgumentException("Collection has no positions");
        }

        Map<String, Map<String, Object>> positionsById = new HashMap<>();
        for (Object rawPosition : positions) {
            Map<String, Object> position = object(rawPosition, "position");
            String id = string(position.get("id"), "position ID");
            if (positionsById.put(id, position) != null) throw new IllegalArgumentException("Duplicate position ID " + id);
        }

        int merged = 0;
        try (var results = mapper.readerFor(new TypeReference<Map<String, Object>>() {}).readValues(batchResults.toFile())) {
            while (results.hasNextValue()) {
                Map<String, Object> result = object(results.nextValue(), "batch result");
                String customId = string(result.get("custom_id"), "custom_id");
                if (!customId.startsWith("position-")) throw new IllegalArgumentException("Unexpected custom_id " + customId);
                String id = customId.substring("position-".length());
                Map<String, Object> position = positionsById.get(id);
                if (position == null) throw new IllegalArgumentException("Batch result does not match position " + id);
                if (position.containsKey("context")) throw new IllegalArgumentException("Position already has context " + id);

                Map<String, Object> response = object(result.get("response"), "response for " + id);
                Number statusCode = number(response.get("status_code"), "status code for " + id);
                if (statusCode.intValue() != 200) throw new IllegalArgumentException("OpenAI request failed for " + id + " with HTTP " + statusCode);
                Map<String, Object> body = object(response.get("body"), "response body for " + id);
                String responseStatus = string(body.get("status"), "response status for " + id);
                if (!"completed".equals(responseStatus)) {
                    String reason = incompleteReason(body);
                    throw new IllegalArgumentException("OpenAI response is " + responseStatus + " for " + id + reason);
                }
                String outputText = outputText(body, id);
                Map<String, Object> generated = mapper.readValue(outputText, new TypeReference<>() {});
                EnrichmentFacts.FactSet facts = EnrichmentFacts.fromFen(string(position.get("fen"), "FEN for " + id));
                EnrichmentContextValidator.validate(generated, id, facts.allowedEvidenceIds());

                Map<String, Object> context = new LinkedHashMap<>(generated);
                context.put("availability", "AVAILABLE");
                context.put("quality", "UNVERIFIED");
                context.put("verifiedFacts", facts.storedFacts());
                context.put("generation", Map.of(
                        "model", string(body.get("model"), "model for " + id),
                        "promptVersion", EnrichmentBatchBuilder.PROMPT_VERSION,
                        "generatedAt", Instant.now().toString(),
                        "reviewStatus", "UNREVIEWED"));
                position.put("context", context);
                merged++;
            }
        }
        if (merged == 0) throw new IllegalArgumentException("Batch result file contains no successful position responses");
        writeAtomically(collection, outputFile);
        return merged;
    }

    public VerifiedMergeResult mergeVerified(Path positionsFile, Path generationResults,
                                             Path verificationResults, Path outputFile) throws IOException {
        Map<String, Object> collection = readObject(positionsFile);
        Object rawPositions = collection.get("positions");
        if (!(rawPositions instanceof List<?> positions) || positions.isEmpty()) {
            throw new IllegalArgumentException("Collection has no positions");
        }
        Map<String, Map<String, Object>> positionsById = new HashMap<>();
        for (Object rawPosition : positions) {
            Map<String, Object> position = object(rawPosition, "position");
            String id = string(position.get("id"), "position ID");
            if (positionsById.put(id, position) != null) throw new IllegalArgumentException("Duplicate position ID " + id);
        }

        Map<String, EnrichmentBatchResults.Generated> generatedById = new HashMap<>();
        for (EnrichmentBatchResults.Generated generated : EnrichmentBatchResults.read(generationResults, "position-")) {
            if (generatedById.put(generated.id(), generated) != null) {
                throw new IllegalArgumentException("Duplicate generation result for " + generated.id());
            }
        }
        Map<String, EnrichmentBatchResults.Generated> verificationById = new HashMap<>();
        for (EnrichmentBatchResults.Generated verification : EnrichmentBatchResults.readVerification(verificationResults)) {
            if (verificationById.put(verification.id(), verification) != null) {
                throw new IllegalArgumentException("Duplicate verification result for " + verification.id());
            }
        }
        if (!verificationById.keySet().equals(generatedById.keySet())) {
            throw new IllegalArgumentException("Generation and verification result IDs do not match");
        }

        int merged = 0;
        List<String> rejected = new java.util.ArrayList<>();
        for (var entry : generatedById.entrySet()) {
            String id = entry.getKey();
            Map<String, Object> position = positionsById.get(id);
            if (position == null) throw new IllegalArgumentException("Generation result does not match position " + id);
            if (position.containsKey("context") && !hasUnavailableContext(position, id)) {
                throw new IllegalArgumentException("Position already has available context " + id);
            }
            EnrichmentBatchResults.Generated verification = verificationById.get(id);
            if (verification == null) throw new IllegalArgumentException("Missing verification result for " + id);
            VerificationDecision decision = verificationDecision(verification.output(), id);
            EnrichmentBatchResults.Generated generated = entry.getValue();
            EnrichmentFacts.FactSet facts = EnrichmentFacts.fromFen(string(position.get("fen"), "FEN for " + id));
            if (!decision.approved()) {
                rejected.add(id);
                position.put("context", unavailableContext(generated, verification, facts, decision.issues()));
                continue;
            }

            EnrichmentContextValidator.validate(generated.output(), id, facts.allowedEvidenceIds());
            Map<String, Object> context = new LinkedHashMap<>(generated.output());
            context.put("availability", "AVAILABLE");
            context.put("quality", "AI_VERIFIED");
            context.put("verifiedFacts", facts.storedFacts());
            context.put("generation", Map.of(
                    "model", generated.model(),
                    "promptVersion", EnrichmentBatchBuilder.PROMPT_VERSION,
                    "generatedAt", Instant.now().toString(),
                    "reviewStatus", "UNREVIEWED",
                    "verification", Map.of(
                            "status", "AI_APPROVED",
                            "model", verification.model())));
            position.put("context", context);
            merged++;
        }
        writeAtomically(collection, outputFile);
        return new VerifiedMergeResult(merged, List.copyOf(rejected));
    }

    private boolean hasUnavailableContext(Map<String, Object> position, String id) {
        Map<String, Object> context = object(position.get("context"), "existing context for " + id);
        return "UNAVAILABLE".equals(context.get("availability"));
    }

    private Map<String, Object> unavailableContext(EnrichmentBatchResults.Generated generated,
                                                   EnrichmentBatchResults.Generated verification,
                                                   EnrichmentFacts.FactSet facts, List<String> issues) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("availability", "UNAVAILABLE");
        context.put("quality", "AI_REJECTED");
        context.put("message", UNAVAILABLE_MESSAGE);
        context.put("verifiedFacts", facts.storedFacts());
        context.put("generation", Map.of(
                "model", generated.model(),
                "promptVersion", EnrichmentBatchBuilder.PROMPT_VERSION,
                "generatedAt", Instant.now().toString(),
                "reviewStatus", "REJECTED",
                "verification", Map.of(
                        "status", "AI_REJECTED",
                        "model", verification.model(),
                        "issues", issues)));
        return context;
    }

    private VerificationDecision verificationDecision(Map<String, Object> verification, String id) {
        if (verification.size() != 2 || !(verification.get("approved") instanceof Boolean approved)) {
            throw new IllegalArgumentException("Invalid verification result for " + id);
        }
        Object rawIssues = verification.get("issues");
        if (!(rawIssues instanceof List<?> issues) || issues.size() > 5
                || issues.stream().anyMatch(issue -> !(issue instanceof String text) || text.isBlank())) {
            throw new IllegalArgumentException("Invalid verification issues for " + id);
        }
        if (approved && !issues.isEmpty()) {
            throw new IllegalArgumentException("Approved verification still contains issues for " + id);
        }
        if (!approved && issues.isEmpty()) {
            throw new IllegalArgumentException("Rejected verification must explain at least one issue for " + id);
        }
        @SuppressWarnings("unchecked")
        List<String> issueTexts = (List<String>) issues;
        return new VerificationDecision(approved, List.copyOf(issueTexts));
    }

    private String incompleteReason(Map<String, Object> body) {
        Object detailsValue = body.get("incomplete_details");
        if (!(detailsValue instanceof Map<?, ?> details)) return "";
        Object reason = details.get("reason");
        return reason instanceof String text && !text.isBlank() ? ": " + text : "";
    }

    private String outputText(Map<String, Object> body, String id) {
        Object rawOutput = body.get("output");
        if (!(rawOutput instanceof List<?> output)) throw new IllegalArgumentException("Missing output for " + id);
        for (Object itemValue : output) {
            Map<String, Object> item = object(itemValue, "output item for " + id);
            if (!"message".equals(item.get("type"))) continue;
            Object rawContent = item.get("content");
            if (!(rawContent instanceof List<?> content)) continue;
            for (Object contentValue : content) {
                Map<String, Object> part = object(contentValue, "content item for " + id);
                if ("output_text".equals(part.get("type"))) return string(part.get("text"), "output text for " + id);
            }
        }
        throw new IllegalArgumentException("No output_text for " + id);
    }

    private Map<String, Object> readObject(Path path) throws IOException {
        return mapper.readValue(path.toFile(), new TypeReference<>() {});
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> object(Object value, String name) {
        if (!(value instanceof Map<?, ?>)) throw new IllegalArgumentException("Missing or invalid " + name);
        return (Map<String, Object>) value;
    }

    private String string(Object value, String name) {
        if (!(value instanceof String text) || text.isBlank()) throw new IllegalArgumentException("Missing or invalid " + name);
        return text;
    }

    private Number number(Object value, String name) {
        if (!(value instanceof Number number)) throw new IllegalArgumentException("Missing or invalid " + name);
        return number;
    }

    private void writeAtomically(Map<String, Object> collection, Path outputFile) throws IOException {
        Path output = outputFile.toAbsolutePath();
        if (output.getParent() != null) Files.createDirectories(output.getParent());
        Path temporary = Files.createTempFile(output.getParent(), "positions-enriched-", ".tmp");
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), collection);
            try { Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}

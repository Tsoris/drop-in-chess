package com.dropinchess.positionenrichment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class EnrichmentBatchImporterTest {
    @TempDir Path temporaryDirectory;

    @Test void mergesStructuredOutputWithoutChangingOtherPositionFields() throws Exception {
        Path positions = temporaryDirectory.resolve("positions.json");
        Path results = temporaryDirectory.resolve("results.jsonl");
        Path enriched = temporaryDirectory.resolve("enriched.json");
        Files.writeString(positions, """
                {"schemaVersion":1,"complete":true,"positions":[
                  {"id":"abc","phase":"MIDDLEGAME","fen":"8/8/8/8/8/4k3/8/4K3 w - - 0 1","source":{"movesSan":["e4"]}}
                ]}
                """);
        String generated = "{\"openingContext\":{\"summary\":\"Opening explanation.\"},"
                + "\"positionGuide\":{\"summary\":\"Position explanation.\",\"themes\":[\"space\",\"king safety\"],"
                + "\"evidenceIds\":[\"WHITE_MATERIAL\"]},"
                + "\"possiblePlans\":{\"white\":{\"summary\":\"Improve the active pieces.\",\"evidenceIds\":[\"WHITE_MATERIAL\"]},"
                + "\"black\":{\"summary\":\"Challenge the center.\",\"evidenceIds\":[\"BLACK_MATERIAL\"]}}}";
        var mapper = JsonMapper.builder().build();
        var result = java.util.Map.of(
                "custom_id", "position-abc",
                "response", java.util.Map.of(
                        "status_code", 200,
                        "body", java.util.Map.of(
                                "status", "completed",
                                "model", "gpt-6-luna-2026-01-01",
                                "output", java.util.List.of(java.util.Map.of(
                                        "type", "message",
                                        "content", java.util.List.of(java.util.Map.of("type", "output_text", "text", generated)))))));
        Files.writeString(results, mapper.writeValueAsString(result) + System.lineSeparator());

        int count = new EnrichmentBatchImporter().merge(positions, results, enriched);

        assertEquals(1, count);
        JsonNode root = mapper.readTree(enriched.toFile());
        JsonNode position = root.get("positions").get(0);
        assertEquals("8/8/8/8/8/4k3/8/4K3 w - - 0 1", position.get("fen").asText());
        assertEquals("Opening explanation.", position.get("context").get("openingContext").get("summary").asText());
        assertEquals("Improve the active pieces.", position.get("context").get("possiblePlans").get("white").get("summary").asText());
        assertEquals("AVAILABLE", position.get("context").get("availability").asText());
        assertEquals("UNVERIFIED", position.get("context").get("quality").asText());
        assertFalse(position.get("context").get("verifiedFacts").get("sideToMoveInCheck").asBoolean());
        assertEquals("UNREVIEWED", position.get("context").get("generation").get("reviewStatus").asText());
        assertEquals("5", position.get("context").get("generation").get("promptVersion").asText());
    }

    @Test void readsPrettyPrintedConcatenatedResults() throws Exception {
        Path positions = temporaryDirectory.resolve("positions.json");
        Path results = temporaryDirectory.resolve("results.jsonl");
        Path enriched = temporaryDirectory.resolve("enriched.json");
        Files.writeString(positions, "{\"positions\":["
                + "{\"id\":\"abc\",\"fen\":\"8/8/8/8/8/4k3/8/4K3 w - - 0 1\"},"
                + "{\"id\":\"def\",\"fen\":\"8/8/8/8/8/4k3/8/4K3 w - - 0 1\"}]}");
        Files.writeString(results, successfulResult("abc", "First") + System.lineSeparator()
                + successfulResult("def", "Second"));

        int count = new EnrichmentBatchImporter().merge(positions, results, enriched);

        assertEquals(2, count);
        JsonNode root = JsonMapper.builder().build().readTree(enriched.toFile());
        assertEquals("First opening.", root.get("positions").get(0).get("context").get("openingContext").get("summary").asText());
        assertEquals("Second opening.", root.get("positions").get(1).get("context").get("openingContext").get("summary").asText());
    }

    @Test void reportsIncompleteResponseReason() throws Exception {
        Path positions = temporaryDirectory.resolve("positions.json");
        Path results = temporaryDirectory.resolve("results.jsonl");
        Files.writeString(positions, "{\"positions\":[{\"id\":\"abc\"}]}");
        Files.writeString(results, """
                {"custom_id":"position-abc","response":{"status_code":200,"body":{
                  "status":"incomplete","incomplete_details":{"reason":"max_output_tokens"}}}}
                """);

        var error = assertThrows(IllegalArgumentException.class,
                () -> new EnrichmentBatchImporter().merge(positions, results, temporaryDirectory.resolve("out.json")));

        assertTrue(error.getMessage().contains("incomplete"));
        assertTrue(error.getMessage().contains("max_output_tokens"));
    }

    @Test void rejectsFailedBatchResponses() throws Exception {
        Path positions = temporaryDirectory.resolve("positions.json");
        Path results = temporaryDirectory.resolve("results.jsonl");
        Files.writeString(positions, "{\"positions\":[{\"id\":\"abc\"}]}");
        Files.writeString(results, "{\"custom_id\":\"position-abc\",\"response\":{\"status_code\":400,\"body\":{}}}\n");

        var error = assertThrows(IllegalArgumentException.class,
                () -> new EnrichmentBatchImporter().merge(positions, results, temporaryDirectory.resolve("out.json")));

        assertTrue(error.getMessage().contains("HTTP 400"));
    }

    @Test void verifiedMergePublishesOnlyApprovedDescriptions() throws Exception {
        Path positions = temporaryDirectory.resolve("positions.json");
        Path generated = temporaryDirectory.resolve("generated.jsonl");
        Path verified = temporaryDirectory.resolve("verified.jsonl");
        Path enriched = temporaryDirectory.resolve("enriched.json");
        String fen = "8/8/8/8/8/4k3/8/4K3 w - - 0 1";
        Files.writeString(positions, "{\"positions\":[{\"id\":\"abc\",\"fen\":\"" + fen
                + "\"},{\"id\":\"def\",\"fen\":\"" + fen + "\"}]}");
        Files.writeString(generated, successfulResult("abc", "First") + System.lineSeparator()
                + successfulResult("def", "Second"));
        Files.writeString(verified, verificationResult("abc", true, java.util.List.of()) + System.lineSeparator()
                + verificationResult("def", false, java.util.List.of("Unsupported material claim.")));

        var result = new EnrichmentBatchImporter().mergeVerified(positions, generated, verified, enriched);

        assertEquals(1, result.merged());
        assertEquals(java.util.List.of("def"), result.rejectedIds());
        JsonNode root = JsonMapper.builder().build().readTree(enriched.toFile());
        assertTrue(root.get("positions").get(0).has("context"));
        assertEquals("AVAILABLE", root.get("positions").get(0).get("context").get("availability").asText());
        assertEquals("AI_VERIFIED", root.get("positions").get(0).get("context").get("quality").asText());
        assertEquals("AI_APPROVED", root.get("positions").get(0).get("context")
                .get("generation").get("verification").get("status").asText());
        JsonNode rejectedContext = root.get("positions").get(1).get("context");
        assertEquals("UNAVAILABLE", rejectedContext.get("availability").asText());
        assertEquals("AI_REJECTED", rejectedContext.get("quality").asText());
        assertEquals(EnrichmentBatchImporter.UNAVAILABLE_MESSAGE, rejectedContext.get("message").asText());
        assertFalse(rejectedContext.has("positionGuide"));

        Path retryGenerated = temporaryDirectory.resolve("retry-generated.jsonl");
        Path retryVerified = temporaryDirectory.resolve("retry-verified.jsonl");
        Path completed = temporaryDirectory.resolve("completed.json");
        Files.writeString(retryGenerated, successfulResult("def", "Retry"));
        Files.writeString(retryVerified, verificationResult("def", true, java.util.List.of()));

        var retryResult = new EnrichmentBatchImporter().mergeVerified(
                enriched, retryGenerated, retryVerified, completed);

        assertEquals(1, retryResult.merged());
        JsonNode completedRoot = JsonMapper.builder().build().readTree(completed.toFile());
        assertEquals("AVAILABLE", completedRoot.get("positions").get(1).get("context").get("availability").asText());
        assertEquals("Retry opening.", completedRoot.get("positions").get(1).get("context")
                .get("openingContext").get("summary").asText());
    }

    @Test void incompleteVerificationBecomesUnavailableContextInsteadOfAbortingMerge() throws Exception {
        Path positions = temporaryDirectory.resolve("positions.json");
        Path generated = temporaryDirectory.resolve("generated.jsonl");
        Path verified = temporaryDirectory.resolve("verified.jsonl");
        Path enriched = temporaryDirectory.resolve("enriched.json");
        Files.writeString(positions, "{\"positions\":[{\"id\":\"abc\",\"fen\":\"8/8/8/8/8/4k3/8/4K3 w - - 0 1\"}]}");
        Files.writeString(generated, successfulResult("abc", "First"));
        Files.writeString(verified, """
                {"custom_id":"verify-position-abc","response":{"status_code":200,"body":{
                  "status":"incomplete","model":"gpt-6-luna",
                  "incomplete_details":{"reason":"max_output_tokens"}}}}
                """);

        var result = new EnrichmentBatchImporter().mergeVerified(positions, generated, verified, enriched);

        assertEquals(0, result.merged());
        assertEquals(java.util.List.of("abc"), result.rejectedIds());
        JsonNode context = JsonMapper.builder().build().readTree(enriched.toFile())
                .get("positions").get(0).get("context");
        assertEquals("UNAVAILABLE", context.get("availability").asText());
        assertEquals("AI_REJECTED", context.get("quality").asText());
        assertTrue(context.get("generation").get("verification").get("issues").get(0)
                .asText().contains("max_output_tokens"));
    }

    private String successfulResult(String id, String label) throws Exception {
        var generated = java.util.Map.of(
                "openingContext", java.util.Map.of("summary", label + " opening."),
                "positionGuide", java.util.Map.of("summary", label + " position.",
                        "themes", java.util.List.of("space", "king safety"),
                        "evidenceIds", java.util.List.of("WHITE_MATERIAL")),
                "possiblePlans", java.util.Map.of(
                        "white", java.util.Map.of("summary", label + " White plan.",
                                "evidenceIds", java.util.List.of("WHITE_MATERIAL")),
                        "black", java.util.Map.of("summary", label + " Black plan.",
                                "evidenceIds", java.util.List.of("BLACK_MATERIAL"))));
        var result = java.util.Map.of(
                "custom_id", "position-" + id,
                "response", java.util.Map.of("status_code", 200, "body", java.util.Map.of(
                        "status", "completed", "model", "gpt-6-luna",
                        "output", java.util.List.of(java.util.Map.of("type", "message", "content",
                                java.util.List.of(java.util.Map.of("type", "output_text", "text",
                                        JsonMapper.builder().build().writeValueAsString(generated))))))));
        return JsonMapper.builder().build().writerWithDefaultPrettyPrinter().writeValueAsString(result);
    }

    private String verificationResult(String id, boolean approved, java.util.List<String> issues) throws Exception {
        var output = java.util.Map.of("approved", approved, "issues", issues);
        var result = java.util.Map.of(
                "custom_id", "verify-position-" + id,
                "response", java.util.Map.of("status_code", 200, "body", java.util.Map.of(
                        "status", "completed", "model", "gpt-6-luna",
                        "output", java.util.List.of(java.util.Map.of("type", "message", "content",
                                java.util.List.of(java.util.Map.of("type", "output_text", "text",
                                        JsonMapper.builder().build().writeValueAsString(output))))))));
        return JsonMapper.builder().build().writeValueAsString(result);
    }
}

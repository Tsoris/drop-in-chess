package com.dropinchess.positionenrichment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EnrichmentVerificationBatchBuilderTest {
    @TempDir Path temporaryDirectory;

    @Test void buildsFactCheckingRequestFromGeneratedContext() throws Exception {
        Path positions = temporaryDirectory.resolve("positions.json");
        Path results = temporaryDirectory.resolve("generation-results.jsonl");
        Path requests = temporaryDirectory.resolve("verification-requests.jsonl");
        Files.writeString(positions, """
                {"positions":[{"id":"abc","phase":"ENDGAME","fen":"6k1/7P/8/8/5K2/8/8/8 b - - 0 77",
                "source":{"movesSan":["h7"],"eco":"C67","opening":"Ruy Lopez","variation":"Berlin"}}]}
                """);
        Map<String, Object> context = Map.of(
                "openingContext", Map.of("summary", "The opening simplified."),
                "positionGuide", Map.of("summary", "Black is in check.", "themes", List.of("check", "promotion"),
                        "evidenceIds", List.of("CHECK_STATUS")),
                "possiblePlans", Map.of(
                        "white", Map.of("summary", "Support the pawn.", "evidenceIds", List.of("WHITE_MATERIAL")),
                        "black", Map.of("summary", "Answer the check first.", "evidenceIds", List.of("CHECK_STATUS"))));
        Files.writeString(results, batchResult("position-abc", context));

        int count = new EnrichmentVerificationBatchBuilder().build(positions, results, requests, "gpt-6-luna");

        assertEquals(1, count);
        var request = JsonMapper.builder().build().readTree(Files.readString(requests));
        assertEquals("verify-position-abc", request.get("custom_id").asText());
        String prompt = request.get("body").get("input").get(1).get("content").asText();
        assertTrue(prompt.contains("Black is currently in check"));
        assertTrue(prompt.contains("Candidate context"));
        assertEquals("boolean", request.get("body").get("text").get("format").get("schema")
                .get("properties").get("approved").get("type").asText());
        assertEquals(1200, request.get("body").get("max_output_tokens").asInt());
    }

    private String batchResult(String customId, Map<String, Object> output) throws Exception {
        var mapper = JsonMapper.builder().build();
        var result = Map.of(
                "custom_id", customId,
                "response", Map.of("status_code", 200, "body", Map.of(
                        "status", "completed", "model", "gpt-6-luna",
                        "output", List.of(Map.of("type", "message", "content",
                                List.of(Map.of("type", "output_text", "text", mapper.writeValueAsString(output))))))));
        return mapper.writeValueAsString(result);
    }
}

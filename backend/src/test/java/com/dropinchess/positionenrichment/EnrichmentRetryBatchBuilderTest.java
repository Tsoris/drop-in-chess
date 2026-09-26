package com.dropinchess.positionenrichment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EnrichmentRetryBatchBuilderTest {
    @TempDir Path temporaryDirectory;

    @Test void rebuildsOnlyRejectedPositions() throws Exception {
        Path positions = temporaryDirectory.resolve("positions.json");
        Path verification = temporaryDirectory.resolve("verification.jsonl");
        Path retries = temporaryDirectory.resolve("retries.jsonl");
        String position = "\"phase\":\"MIDDLEGAME\",\"fen\":\"8/8/8/8/8/4k3/8/4K3 w - - 0 1\","
                + "\"source\":{\"movesSan\":[\"e4\"],\"ply\":1}";
        Files.writeString(positions, "{\"schemaVersion\":1,\"complete\":true,\"positions\":["
                + "{\"id\":\"abc\"," + position + "},{\"id\":\"def\"," + position + "}]}");
        Files.writeString(verification, verificationResult("abc", true, List.of()) + System.lineSeparator()
                + verificationResult("def", false, List.of("Incorrect material claim.")));

        int count = new EnrichmentRetryBatchBuilder().build(positions, verification, retries, "gpt-6-luna");

        assertEquals(1, count);
        var request = JsonMapper.builder().build().readTree(Files.readString(retries));
        assertEquals("position-def", request.get("custom_id").asText());
    }

    private String verificationResult(String id, boolean approved, List<String> issues) throws Exception {
        var mapper = JsonMapper.builder().build();
        Map<String, Object> output = Map.of("approved", approved, "issues", issues);
        Map<String, Object> result = Map.of(
                "custom_id", "verify-position-" + id,
                "response", Map.of("status_code", 200, "body", Map.of(
                        "status", "completed", "model", "gpt-6-luna",
                        "output", List.of(Map.of("type", "message", "content",
                                List.of(Map.of("type", "output_text", "text", mapper.writeValueAsString(output))))))));
        return mapper.writeValueAsString(result);
    }
}

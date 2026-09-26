package com.dropinchess.positionenrichment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class EnrichmentBatchBuilderTest {
    @TempDir Path temporaryDirectory;

    @Test void writesOneStructuredResponsesRequestPerPosition() throws Exception {
        Path input = temporaryDirectory.resolve("positions.json");
        Path output = temporaryDirectory.resolve("requests.jsonl");
        Files.writeString(input, """
                {"schemaVersion":1,"complete":true,"positions":[
                  {"id":"abc","phase":"MIDDLEGAME","endgameType":null,"fen":"8/8/8/8/8/8/4k3/4K3 w - - 0 1",
                   "source":{"movesSan":["e4","c5"],"ply":2,"eco":"B20","opening":"Sicilian Defense","variation":null}},
                  {"id":"def","phase":"ENDGAME","endgameType":"ROOK","fen":"8/8/8/8/8/8/4k3/R3K2r b - - 0 1",
                   "source":{"movesSan":["d4","d5"],"ply":2,"eco":"D00","opening":"Queen's Pawn Game","variation":"Main Line"}}
                ]}
                """);

        int count = new EnrichmentBatchBuilder().build(input, output, 2, "gpt-6-luna");

        assertEquals(2, count);
        var lines = Files.readAllLines(output);
        assertEquals(2, lines.size());
        JsonNode request = JsonMapper.builder().build().readTree(lines.getFirst());
        assertEquals("position-abc", request.get("custom_id").asText());
        assertEquals("/v1/responses", request.get("url").asText());
        assertEquals("gpt-6-luna", request.get("body").get("model").asText());
        assertEquals("low", request.get("body").get("reasoning").get("effort").asText());
        assertEquals(1200, request.get("body").get("max_output_tokens").asInt());
        assertTrue(request.get("body").get("input").get(1).get("content").asText().contains("e4 c5"));
        String prompt = request.get("body").get("input").get(1).get("content").asText();
        assertTrue(prompt.contains("[SIDE_TO_MOVE] White"));
        assertTrue(prompt.contains("[WHITE_MATERIAL] White: king=e1 (1)"));
        assertTrue(prompt.contains("[BLACK_MATERIAL] Black: king=e2 (1)"));
        assertTrue(prompt.contains("[MATERIAL_COMPARISON]"));
        assertTrue(prompt.contains("both kings are on the center files"));
        JsonNode format = request.get("body").get("text").get("format");
        assertEquals("json_schema", format.get("type").asText());
        assertTrue(format.get("strict").asBoolean());
        assertFalse(format.get("schema").get("additionalProperties").asBoolean());
        assertTrue(format.get("schema").get("properties").has("possiblePlans"));
        assertTrue(format.get("schema").get("properties").get("possiblePlans")
                .get("properties").has("white"));
        assertTrue(format.get("schema").get("properties").get("possiblePlans")
                .get("properties").has("black"));
        assertTrue(format.get("schema").get("properties").get("positionGuide")
                .get("properties").has("evidenceIds"));
        assertEquals("position-def", JsonMapper.builder().build().readTree(lines.get(1)).get("custom_id").asText());
    }

    @Test void pilotBalancesPhasesAndVariesEndgameTypes() throws Exception {
        Path input = temporaryDirectory.resolve("positions.json");
        Path output = temporaryDirectory.resolve("requests.jsonl");
        StringBuilder positions = new StringBuilder();
        for (int index = 0; index < 8; index++) {
            if (!positions.isEmpty()) positions.append(',');
            positions.append(position("m" + index, "MIDDLEGAME", null));
        }
        for (int index = 0; index < 4; index++) {
            positions.append(',').append(position("r" + index, "ENDGAME", "ROOK"));
            positions.append(',').append(position("b" + index, "ENDGAME", "BISHOP"));
        }
        Files.writeString(input, "{\"schemaVersion\":1,\"complete\":true,\"positions\":[" + positions + "]}");

        new EnrichmentBatchBuilder().build(input, output, 6, "gpt-6-luna");

        var requests = Files.readAllLines(output).stream().map(line -> {
            try { return JsonMapper.builder().build().readTree(line); }
            catch (Exception failure) { throw new RuntimeException(failure); }
        }).toList();
        assertEquals(3, requests.stream().filter(request -> request.get("custom_id").asText().startsWith("position-m")).count());
        assertTrue(requests.stream().anyMatch(request -> request.get("custom_id").asText().startsWith("position-r")));
        assertTrue(requests.stream().anyMatch(request -> request.get("custom_id").asText().startsWith("position-b")));
    }

    @Test void rejectsPartialCollections() throws Exception {
        Path input = temporaryDirectory.resolve("positions.json");
        Files.writeString(input, "{\"schemaVersion\":1,\"complete\":false,\"positions\":[]}");

        var error = assertThrows(IllegalArgumentException.class,
                () -> new EnrichmentBatchBuilder().build(input, temporaryDirectory.resolve("out.jsonl"), 1, "gpt-6-luna"));

        assertTrue(error.getMessage().contains("complete"));
    }

    @Test void fullBuildSkipsPositionsThatAlreadyHaveContext() throws Exception {
        Path input = temporaryDirectory.resolve("positions.json");
        Path output = temporaryDirectory.resolve("requests.jsonl");
        String enriched = position("done", "MIDDLEGAME", null)
                .replace("\"source\":", "\"context\":{\"availability\":\"AVAILABLE\"},\"source\":");
        String missing = position("missing", "ENDGAME", "PAWN_ONLY");
        Files.writeString(input, "{\"schemaVersion\":1,\"complete\":true,\"positions\":["
                + enriched + "," + missing + "]}");

        int count = new EnrichmentBatchBuilder().buildMissing(input, output, "gpt-6-luna");

        assertEquals(1, count);
        var request = JsonMapper.builder().build().readTree(Files.readString(output));
        assertEquals("position-missing", request.get("custom_id").asText());
    }

    private String position(String id, String phase, String endgameType) {
        String type = endgameType == null ? "null" : "\"" + endgameType + "\"";
        return "{\"id\":\"" + id + "\",\"phase\":\"" + phase + "\",\"endgameType\":" + type
                + ",\"fen\":\"8/8/8/8/8/8/4k3/4K3 w - - 0 1\",\"source\":{\"movesSan\":[\"e4\"],\"ply\":1}}";
    }
}

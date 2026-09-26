package com.dropinchess.positionenrichment;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Reads completed structured outputs from an OpenAI Batch API result file. */
final class EnrichmentBatchResults {
    record Generated(String id, String model, Map<String, Object> output) {}

    private EnrichmentBatchResults() {}

    static List<Generated> read(Path path, String customIdPrefix) throws IOException {
        return read(path, customIdPrefix, false);
    }

    static List<Generated> readVerification(Path path) throws IOException {
        return read(path, "verify-position-", true);
    }

    private static List<Generated> read(Path path, String customIdPrefix,
                                        boolean incompleteAsRejection) throws IOException {
        JsonMapper mapper = JsonMapper.builder().build();
        List<Generated> generated = new ArrayList<>();
        try (var results = mapper.readerFor(new TypeReference<Map<String, Object>>() {}).readValues(path.toFile())) {
            while (results.hasNextValue()) {
                Map<String, Object> result = object(results.nextValue(), "batch result");
                String customId = string(result.get("custom_id"), "custom_id");
                if (!customId.startsWith(customIdPrefix)) {
                    throw new IllegalArgumentException("Unexpected custom_id " + customId);
                }
                String id = customId.substring(customIdPrefix.length());
                Map<String, Object> response = object(result.get("response"), "response for " + id);
                Number statusCode = number(response.get("status_code"), "status code for " + id);
                if (statusCode.intValue() != 200) {
                    throw new IllegalArgumentException("OpenAI request failed for " + id + " with HTTP " + statusCode);
                }
                Map<String, Object> body = object(response.get("body"), "response body for " + id);
                String responseStatus = string(body.get("status"), "response status for " + id);
                if (!"completed".equals(responseStatus)) {
                    if (incompleteAsRejection && "incomplete".equals(responseStatus)) {
                        String reason = incompleteReason(body);
                        String issue = "Verification did not complete" + (reason.isBlank() ? "." : reason + ".");
                        generated.add(new Generated(id, string(body.get("model"), "model for " + id),
                                Map.of("approved", false, "issues", List.of(issue))));
                        continue;
                    }
                    throw new IllegalArgumentException("OpenAI response is " + responseStatus + " for " + id
                            + incompleteReason(body));
                }
                String outputText = outputText(body, id);
                Map<String, Object> output = mapper.readValue(outputText, new TypeReference<>() {});
                generated.add(new Generated(id, string(body.get("model"), "model for " + id), output));
            }
        }
        if (generated.isEmpty()) throw new IllegalArgumentException("Batch result file contains no successful responses");
        return List.copyOf(generated);
    }

    private static String incompleteReason(Map<String, Object> body) {
        Object detailsValue = body.get("incomplete_details");
        if (!(detailsValue instanceof Map<?, ?> details)) return "";
        Object reason = details.get("reason");
        return reason instanceof String text && !text.isBlank() ? ": " + text : "";
    }

    private static String outputText(Map<String, Object> body, String id) {
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

    @SuppressWarnings("unchecked")
    static Map<String, Object> object(Object value, String name) {
        if (!(value instanceof Map<?, ?>)) throw new IllegalArgumentException("Missing or invalid " + name);
        return (Map<String, Object>) value;
    }

    static String string(Object value, String name) {
        if (!(value instanceof String text) || text.isBlank()) throw new IllegalArgumentException("Missing or invalid " + name);
        return text;
    }

    private static Number number(Object value, String name) {
        if (!(value instanceof Number number)) throw new IllegalArgumentException("Missing or invalid " + name);
        return number;
    }
}

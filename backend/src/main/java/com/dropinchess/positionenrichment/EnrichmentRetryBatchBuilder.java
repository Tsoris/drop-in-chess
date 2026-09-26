package com.dropinchess.positionenrichment;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Rebuilds generation requests only for positions rejected by the verification batch. */
final class EnrichmentRetryBatchBuilder {
    int build(Path positionsFile, Path verificationResults, Path outputFile, String model) throws IOException {
        Set<String> rejectedIds = new LinkedHashSet<>();
        for (EnrichmentBatchResults.Generated verification
                : EnrichmentBatchResults.readVerification(verificationResults)) {
            if (!approved(verification.output(), verification.id())) rejectedIds.add(verification.id());
        }
        if (rejectedIds.isEmpty()) throw new IllegalArgumentException("Verification batch rejected no positions");
        return new EnrichmentBatchBuilder().buildSelected(positionsFile, outputFile, rejectedIds, model);
    }

    private boolean approved(Map<String, Object> verification, String id) {
        if (verification.size() != 2 || !(verification.get("approved") instanceof Boolean approved)) {
            throw new IllegalArgumentException("Invalid verification result for " + id);
        }
        Object rawIssues = verification.get("issues");
        if (!(rawIssues instanceof List<?> issues) || issues.size() > 5
                || issues.stream().anyMatch(issue -> !(issue instanceof String text) || text.isBlank())) {
            throw new IllegalArgumentException("Invalid verification issues for " + id);
        }
        if (approved && !issues.isEmpty()) throw new IllegalArgumentException("Approved result has issues for " + id);
        if (!approved && issues.isEmpty()) throw new IllegalArgumentException("Rejected result has no issue for " + id);
        return approved;
    }
}

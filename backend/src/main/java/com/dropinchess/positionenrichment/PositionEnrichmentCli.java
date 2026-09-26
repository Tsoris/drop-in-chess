package com.dropinchess.positionenrichment;

import java.nio.file.Path;

/** IntelliJ-friendly entry point for the local build and merge steps. */
public final class PositionEnrichmentCli {
    private PositionEnrichmentCli() {}

    public static void main(String[] args) throws Exception {
        if (args.length == 0) throw usage();
        switch (args[0]) {
            case "build" -> {
                if (args.length < 3 || args.length > 5) throw usage();
                int limit = args.length >= 4 ? Integer.parseInt(args[3]) : 25;
                String model = args.length == 5 ? args[4] : EnrichmentBatchBuilder.DEFAULT_MODEL;
                int count = new EnrichmentBatchBuilder().build(Path.of(args[1]), Path.of(args[2]), limit, model);
                System.out.printf("Wrote %d enrichment requests to %s%n", count, Path.of(args[2]).toAbsolutePath());
            }
            case "build-missing" -> {
                if (args.length < 3 || args.length > 4) throw usage();
                String model = args.length == 4 ? args[3] : EnrichmentBatchBuilder.DEFAULT_MODEL;
                int count = new EnrichmentBatchBuilder().buildMissing(Path.of(args[1]), Path.of(args[2]), model);
                System.out.printf("Wrote %d missing-context requests to %s%n", count, Path.of(args[2]).toAbsolutePath());
            }
            case "merge" -> {
                if (args.length != 4) throw usage();
                int count = new EnrichmentBatchImporter().merge(Path.of(args[1]), Path.of(args[2]), Path.of(args[3]));
                System.out.printf("Merged %d descriptions into %s%n", count, Path.of(args[3]).toAbsolutePath());
            }
            case "build-verification" -> {
                if (args.length < 4 || args.length > 5) throw usage();
                String model = args.length == 5 ? args[4] : EnrichmentBatchBuilder.DEFAULT_MODEL;
                int count = new EnrichmentVerificationBatchBuilder().build(
                        Path.of(args[1]), Path.of(args[2]), Path.of(args[3]), model);
                System.out.printf("Wrote %d verification requests to %s%n", count, Path.of(args[3]).toAbsolutePath());
            }
            case "merge-verified" -> {
                if (args.length != 5) throw usage();
                var result = new EnrichmentBatchImporter().mergeVerified(
                        Path.of(args[1]), Path.of(args[2]), Path.of(args[3]), Path.of(args[4]));
                System.out.printf("Merged %d AI-approved descriptions into %s%n",
                        result.merged(), Path.of(args[4]).toAbsolutePath());
                if (!result.rejectedIds().isEmpty()) {
                    System.out.printf("Rejected %d positions: %s%n",
                            result.rejectedIds().size(), String.join(", ", result.rejectedIds()));
                }
            }
            case "build-retries" -> {
                if (args.length < 4 || args.length > 5) throw usage();
                String model = args.length == 5 ? args[4] : EnrichmentBatchBuilder.DEFAULT_MODEL;
                int count = new EnrichmentRetryBatchBuilder().build(
                        Path.of(args[1]), Path.of(args[2]), Path.of(args[3]), model);
                System.out.printf("Wrote %d retry requests to %s%n", count, Path.of(args[3]).toAbsolutePath());
            }
            case "report-verification" -> {
                if (args.length != 2) throw usage();
                int approved = 0;
                int rejected = 0;
                for (var result : EnrichmentBatchResults.readVerification(Path.of(args[1]))) {
                    Object approvedValue = result.output().get("approved");
                    Object issuesValue = result.output().get("issues");
                    if (!(approvedValue instanceof Boolean isApproved) || !(issuesValue instanceof java.util.List<?> issues)) {
                        throw new IllegalArgumentException("Invalid verification result for " + result.id());
                    }
                    if (isApproved) {
                        approved++;
                    } else {
                        rejected++;
                        System.out.printf("Rejected %s:%n", result.id());
                        for (Object issue : issues) System.out.printf("  - %s%n", issue);
                    }
                }
                System.out.printf("Verification totals: approved=%d, rejected=%d%n", approved, rejected);
            }
            default -> throw usage();
        }
    }

    private static IllegalArgumentException usage() {
        return new IllegalArgumentException("Usage: build <positions.json> <requests.jsonl> [limit=25] [model=gpt-6-luna]"
                + " OR build-missing <positions.json> <requests.jsonl> [model=gpt-6-luna]"
                + " OR build-verification <positions.json> <generation-results.jsonl> <verification-requests.jsonl> [model=gpt-6-luna]"
                + " OR merge <positions.json> <batch-results.jsonl> <positions-enriched.json>"
                + " OR merge-verified <positions.json> <generation-results.jsonl> <verification-results.jsonl> <positions-enriched.json>"
                + " OR build-retries <positions.json> <verification-results.jsonl> <retry-requests.jsonl> [model=gpt-6-luna]"
                + " OR report-verification <verification-results.jsonl>");
    }
}

package com.dropinchess.positiongen;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.game.Game;
import com.github.bhlangonijr.chesslib.pgn.PgnIterator;

import java.util.*;

import com.dropinchess.positiongen.PositionClassifier.Phase;

/**
 * Offline engine-filtered position generation with resumable JSON checkpoints.
 */
public class PositionGenerator {
    public static void main(String[] args) throws Exception {
        long runStartedNanos = System.nanoTime();
        if (args.length < 1 || args.length > 11) {
            throw new IllegalArgumentException("Arguments: PGN-path [maxGames=100] [seed=42] [minimumMove=15] [rangeDivisor=3] [endgameMaterial=13] [stockfishPath=auto] [depth=12] [maxEvaluationsPerPhase=20] [outputPath=generated/positions.json] [targetPerPhase=500]");
        }
        int maxGames = args.length > 1 ? Integer.parseInt(args[1]) : 100;
        long seed = args.length > 2 ? Long.parseLong(args[2]) : 42;
        int minimumMove = args.length > 3 ? Integer.parseInt(args[3]) : 15;
        int divisor = args.length > 4 ? Integer.parseInt(args[4]) : 3;
        int material = args.length > 5 ? Integer.parseInt(args[5]) : 13;
        if (maxGames < 1) throw new IllegalArgumentException("maxGames must be positive");
        java.nio.file.Path enginePath = args.length > 6 ? java.nio.file.Path.of(args[6]) : defaultEngine();
        int depth = args.length > 7 ? Integer.parseInt(args[7]) : 12;
        int maxEvaluations = args.length > 8 ? Integer.parseInt(args[8]) : 20;
        if (depth < 1 || depth > 128 || maxEvaluations < 1)
            throw new IllegalArgumentException("Invalid engine search limits");
        java.nio.file.Path output = java.nio.file.Path.of(args.length > 9 ? args[9] : "generated/positions.json");
        int target = args.length > 10 ? Integer.parseInt(args[10]) : 500;
        if (target < 1) throw new IllegalArgumentException("Target must be positive");
        String sourceHash;
        try (var input = java.nio.file.Files.newInputStream(java.nio.file.Path.of(args[0]))) {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[65536];
            int count;
            while ((count = input.read(buffer)) >= 0) digest.update(buffer, 0, count);
            sourceHash = HexFormat.of().formatHex(digest.digest());
        }
        int selectedMid = 0, selectedEnd = 0, evaluations = 0;
        PositionClassifier classifier = new PositionClassifier(minimumMove, material);

        int read = 0, skipped = 0, forward = 0, backward = 0, midGames = 0, endGames = 0;
        List<Integer> lengths = new ArrayList<>();
        Map<PositionClassifier.EndgameType, Integer> endTypes = new EnumMap<>(PositionClassifier.EndgameType.class);
        System.out.printf("ENGINE-FILTERED TRIAL: seed=%d, minimumMove=%d, divisor=%d, material=%d%n", seed, minimumMove, divisor, material);
        try (StockfishClient engine = new StockfishClient(enginePath, java.time.Duration.ofSeconds(60)); PgnIterator games = new PgnIterator(args[0])) {
            System.out.printf("Engine=%s depth=%d, balance=+/-50 cp, max evaluations/phase=%d%n", engine.version(), depth, maxEvaluations);
            var settings = new PositionWriter.Generation(java.nio.file.Path.of(args[0]).getFileName().toString(), sourceHash,
                    engine.version(), depth, 50, seed, minimumMove, divisor, material, maxEvaluations, target, 1, 64, "per-game-v1-endgame-halfmove-lt20");
            var writer = new PositionWriter(output, settings, runStartedNanos);
            System.out.printf("JSON=%s, resuming after game %d%n", output.toAbsolutePath(), writer.processedGames());
            for (Game game : games) {
                if (read >= maxGames || writer.complete()) break;
                read++;
                if (read <= writer.processedGames()) continue;
                CandidateSearch search = new CandidateSearch(minimumMove, divisor, new Random(seed ^ (0x9E3779B97F4A7C15L * read)));
                try {
                    // The initial trial handles standard-start games only, making source-relative plies unambiguous.
                    if (game.getFen() != null && !game.getFen().isBlank()) {
                        throw new IllegalArgumentException("Custom starting FEN is not supported by this trial");
                    }
                    String variant = game.getProperty() == null ? null : game.getProperty().get("Variant");
                    if (variant != null && !variant.equalsIgnoreCase("Standard")) {
                        throw new IllegalArgumentException("Unsupported variant: " + variant);
                    }
                    game.loadMoveText();
                    Board board = new Board();
                    List<CandidateSearch.Candidate> positions = new ArrayList<>();
                    int ply = 0;
                    for (var move : game.getHalfMoves()) {
                        if (!board.doMove(move, true))
                            throw new IllegalStateException("Illegal move at ply " + (ply + 1));
                        ply++;
                        Phase phase = classifier.classify(board, ply);
                        positions.add(new CandidateSearch.Candidate(ply, board.getFen(), phase, phase == Phase.ENDGAME ? classifier.endgameType(board) : null));
                    }
                    CandidateSearch.Plan mid = search.middlegame(positions, ply);
                    CandidateSearch.Plan end = search.endgame(positions);
                    lengths.add(ply);
                    if (mid.randomStartPly() >= 0) {
                        if (mid.direction() == CandidateSearch.Direction.BACKWARD) backward++;
                        else forward++;
                    }
                    if (!mid.candidates().isEmpty()) midGames++;
                    if (!end.candidates().isEmpty()) {
                        endGames++;
                        endTypes.merge(end.candidates().getFirst().endgameType(), 1, Integer::sum);
                    }
                    System.out.printf("Game %d: %.1f moves | mid start ply=%d %s, %d candidates | end start ply=%d, %d candidates%n", read, ply / 2.0, mid.randomStartPly(), mid.direction(), mid.candidates().size(), end.randomStartPly(), end.candidates().size());
                    var movesSan = Arrays.asList(game.getHalfMoves().toSanArray());
                    var midResult = writer.needs(Phase.MIDDLEGAME) ? PositionSelector.select(mid, fen -> engine.evaluate(fen, depth), maxEvaluations, 50, writer::contains) : new PositionSelector.Result(null, 0);
                    saveSelection(writer, settings, game, read, movesSan, midResult, mid);
                    var endResult = writer.needs(Phase.ENDGAME) ? PositionSelector.select(end, fen -> engine.evaluate(fen, depth), maxEvaluations, 50, writer::contains) : new PositionSelector.Result(null, 0);
                    saveSelection(writer, settings, game, read, movesSan, endResult, end);
                    evaluations += midResult.evaluated() + endResult.evaluated();
                    if (midResult.selection() != null) selectedMid++;
                    if (endResult.selection() != null) selectedEnd++;
                    printSelection("MIDDLEGAME", midResult, mid);
                    printSelection("ENDGAME", endResult, end);
                } catch (java.io.IOException engineFailure) {
                    throw engineFailure; // Never misreport a failed engine as an unbalanced position or skip every remaining game.
                } catch (Exception exception) {
                    skipped++;
                    System.err.printf("Skipping game %d: %s%n", read, exception.getMessage());
                }
                writer.checkpoint(read);
                if (read >= maxGames) break;
            }
        }
        Collections.sort(lengths);
        System.out.printf("Games read=%d, skipped=%d, with mid candidates=%d, with end candidates=%d%n", read, skipped, midGames, endGames);
        System.out.printf("Middlegame search directions: forward=%d, backward=%d%n", forward, backward);
        if (!lengths.isEmpty()) {
            System.out.printf("Game length in moves: p10=%.1f, median=%.1f, p90=%.1f%n", percentile(lengths, .10) / 2.0, percentile(lengths, .50) / 2.0, percentile(lengths, .90) / 2.0);
        }
        System.out.println("First endgame candidate types: " + endTypes);
        System.out.printf("Balanced selections: mid=%d, end=%d; engine evaluations=%d%n", selectedMid, selectedEnd, evaluations);
        System.out.println("Selections saved to JSON with global deduplication and phase quotas. Incomplete collections can be resumed.");
    }

    private static void saveSelection(PositionWriter writer, PositionWriter.Generation settings, Game game, int gameIndex,
            List<String> movesSan, PositionSelector.Result result, CandidateSearch.Plan plan) throws java.io.IOException {
        var selected = result.selection();
        if (selected == null) return;
        var candidate = selected.candidate();
        String url = game.getProperty() == null ? null : game.getProperty().get("LichessURL");
        var position = new GeneratedPosition(PositionWriter.id(candidate.fen()), candidate.phase(), candidate.endgameType(), candidate.fen(),
                new GeneratedPosition.Source(gameIndex, url, new Board().getFen(), movesSan.subList(0, candidate.ply()), candidate.ply(),
                        game.getEco(), game.getOpening(), game.getVariation()),
                new GeneratedPosition.Analysis(selected.evaluation().whiteCp(), selected.evaluation().depth()),
                new GeneratedPosition.Selection(plan.randomStartPly(), plan.direction(), selected.evaluated()));
        PositionWriter.validate(position, settings);
        if (!writer.add(position)) throw new java.io.IOException("Selected position violates collection constraints");
    }

    private static java.nio.file.Path defaultEngine() {
        String name = "stockfish/stockfish-windows-x86-64-universal.exe";
        for (var path : List.of(java.nio.file.Path.of(name), java.nio.file.Path.of("..").resolve(name))) {
            if (java.nio.file.Files.isRegularFile(path)) return path;
        }
        throw new IllegalArgumentException("Stockfish not found; provide its path as argument 7");
    }

    private static void printSelection(String phase, PositionSelector.Result result, CandidateSearch.Plan plan) {
        var selected = result.selection();
        if (selected == null) {
            System.out.printf("  %s: no balanced candidate after %d evaluations%n", phase, result.evaluated());
        } else {
            System.out.printf("  %s: ply=%d start=%d direction=%s scoreWhiteCp=%d depth=%d evaluations=%d type=%s%n    FEN: %s%n", phase, selected.candidate().ply(), plan.randomStartPly(), plan.direction(), selected.evaluation().whiteCp(), selected.evaluation().depth(), result.evaluated(), selected.candidate().endgameType(), selected.candidate().fen());
        }
    }

    private static int percentile(List<Integer> sorted, double fraction) {
        return sorted.get(Math.max(0, (int) Math.ceil(sorted.size() * fraction) - 1));
    }
}

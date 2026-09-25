package com.dropinchess.positiongen;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.move.MoveList;
import tools.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** Atomic per-game snapshots also serve as resumable checkpoints. */
public final class PositionWriter {
    public record Generation(String sourceFile, String sourceSha256, String engine, int depth,
            int balanceLimitCp, long seed, int minimumMove, int rangeDivisor, int endgameMaterial,
            int maxEvaluationsPerPhase, int targetPerPhase, int threads, int hashMb, String samplingVersion) {}
    public record Collection(int schemaVersion, Generation generation, int processedGames,
            boolean complete, Long elapsedMillis, List<GeneratedPosition> positions) {}
    private final Path path;
    private final Generation settings;
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final List<GeneratedPosition> positions = new ArrayList<>();
    private final Set<String> identities = new HashSet<>();
    private int processedGames;
    private final java.util.function.LongSupplier nanoClock;
    private final long runStartedNanos;
    private Long previousElapsedMillis = 0L;

    public PositionWriter(Path path, Generation settings) throws IOException {
        this(path, settings, System.nanoTime(), System::nanoTime);
    }

    public PositionWriter(Path path, Generation settings, long runStartedNanos) throws IOException {
        this(path, settings, runStartedNanos, System::nanoTime);
    }

    PositionWriter(Path path, Generation settings, long runStartedNanos, java.util.function.LongSupplier nanoClock) throws IOException {
        this.nanoClock = nanoClock;
        this.runStartedNanos = runStartedNanos;
        this.path = path.toAbsolutePath();
        this.settings = settings;
        if (Files.exists(this.path)) {
            Collection saved = mapper.readValue(this.path.toFile(), Collection.class);
            if (saved.schemaVersion() != 1 || !saved.generation().equals(settings)) {
                throw new IOException("Output settings/source differ; choose a new output path to start a new collection");
            }
            if (saved.processedGames() < 0) throw new IOException("Invalid checkpoint");
            for (var position : saved.positions()) {
                validate(position, settings);
                if (position.source().gameIndex() > saved.processedGames() || !add(position)) throw new IOException("Invalid duplicate/quota in checkpoint");
            }
            previousElapsedMillis = saved.elapsedMillis();
            if (previousElapsedMillis != null && previousElapsedMillis < 0) throw new IOException("Invalid elapsed time");
            processedGames = saved.processedGames();
            if (saved.complete() != complete()) throw new IOException("Invalid completion flag");
        }
    }
    public static String identity(String fen) {
        // Ignore counters and normalize en passant if there is no legal en passant capture.
        Board board = new Board();
        board.loadFromFen(fen);
        String[] fields = fen.split(" ");
        if (!fields[3].equals("-")) {
            boolean legalEp = board.legalMoves().stream().anyMatch(move ->
                    move.getTo().toString().equalsIgnoreCase(fields[3])
                    && board.getPiece(move.getFrom()).toString().contains("PAWN")
                    && move.getFrom().toString().charAt(0) != move.getTo().toString().charAt(0));
            if (!legalEp) fields[3] = "-";
        }
        return String.join(" ", Arrays.copyOf(fields, 4));
    }
    public static String id(String fen) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(identity(fen).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    public Long elapsedMillis() {
        return previousElapsedMillis == null ? null
                : previousElapsedMillis + java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(nanoClock.getAsLong() - runStartedNanos);
    }

    public boolean contains(String fen) { return identities.contains(identity(fen)); }
    public long count(PositionClassifier.Phase phase) { return positions.stream().filter(p -> p.phase() == phase).count(); }
    public boolean needs(PositionClassifier.Phase phase) { return count(phase) < settings.targetPerPhase(); }
    public boolean complete() { return !needs(PositionClassifier.Phase.MIDDLEGAME) && !needs(PositionClassifier.Phase.ENDGAME); }
    public int processedGames() { return processedGames; }
    public boolean add(GeneratedPosition position) {
        if (!needs(position.phase()) || positions.stream().anyMatch(p -> p.source().gameIndex() == position.source().gameIndex() && p.phase() == position.phase())) return false;
        if (!identities.add(identity(position.fen()))) return false;
        positions.add(position);
        return true;
    }
    public void checkpoint(int gameIndex) throws IOException {
        Path parent = path.getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "positions-", ".tmp");
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(),
                    new Collection(1, settings, gameIndex, complete(), elapsedMillis(), List.copyOf(positions)));
            try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException unsupported) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
            processedGames = gameIndex;
        } finally { Files.deleteIfExists(temporary); }
    }
    public static void validate(GeneratedPosition position, Generation settings) throws IOException {
        try {
            var source = position.source();
            if (source.ply() != source.movesSan().size() || source.gameIndex() < 1) throw new IllegalArgumentException("Invalid history length/source");
            MoveList moves = new MoveList(source.startingFen());
            moves.loadFromSan(String.join(" ", source.movesSan()));
            Board board = new Board();
            board.loadFromFen(source.startingFen());
            for (var move : moves) if (!board.doMove(move, true)) throw new IllegalArgumentException("Illegal history move");
            if (!board.getFen().equals(position.fen())) throw new IllegalArgumentException("History does not reproduce FEN");
            var classifier = new PositionClassifier(settings.minimumMove(), settings.endgameMaterial());
            if (classifier.classify(board, source.ply()) != position.phase()
                    || (position.phase() != PositionClassifier.Phase.MIDDLEGAME && position.phase() != PositionClassifier.Phase.ENDGAME)) throw new IllegalArgumentException("Invalid phase");
            if (Math.abs((long) position.analysis().whiteScoreCp()) > settings.balanceLimitCp()
                    || position.analysis().depth() < settings.depth()) throw new IllegalArgumentException("Invalid analysis");
            if (!PositionSelector.hasRecentProgress(new CandidateSearch.Candidate(source.ply(), position.fen(), position.phase(), position.endgameType()))) throw new IllegalArgumentException("Endgame halfmove clock must be below 20");
            if (!position.id().equals(id(position.fen()))) throw new IllegalArgumentException("Invalid ID");
        } catch (Exception failure) { throw new IOException("Invalid saved position: " + position.id(), failure); }
    }
}



package com.dropinchess.positiongen;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PositionWriterTest {
    @TempDir Path directory;
    PositionWriter.Generation settings() {
        return new PositionWriter.Generation("test.pgn", "hash", "test", 12, 50, 42, 1, 3, 13, 20, 1, 1, 64, "per-game-v1");
    }
    GeneratedPosition position() {
        var board = new com.github.bhlangonijr.chesslib.Board();
        String start = board.getFen();
        board.doMove("e4"); board.doMove("e5");
        return new GeneratedPosition(PositionWriter.id(board.getFen()), PositionClassifier.Phase.MIDDLEGAME, null, board.getFen(),
                new GeneratedPosition.Source(1, null, start, List.of("e4", "e5"), 2, "C20", "King Pawn Game", null),
                new GeneratedPosition.Analysis(20, 12), new GeneratedPosition.Selection(2, CandidateSearch.Direction.FORWARD, 1));
    }
    @Test void persistsHistoryAndRestoresQuotaAndDeduplication() throws Exception {
        var path = directory.resolve("positions.json");
        var writer = new PositionWriter(path, settings());
        PositionWriter.validate(position(), settings());
        assertTrue(writer.add(position()));
        assertFalse(writer.add(position()));
        writer.checkpoint(1);
        var mapper = tools.jackson.databind.json.JsonMapper.builder().build();
        var saved = mapper.readValue(path.toFile(), PositionWriter.Collection.class);
        assertEquals("C20", saved.positions().getFirst().source().eco());
        assertEquals("King Pawn Game", saved.positions().getFirst().source().opening());
        assertNull(saved.positions().getFirst().source().variation());
        var restored = new PositionWriter(path, settings());
        assertEquals(1, restored.processedGames());
        assertTrue(restored.contains(position().fen()));
        assertFalse(restored.needs(PositionClassifier.Phase.MIDDLEGAME));
        assertTrue(restored.needs(PositionClassifier.Phase.ENDGAME));
        assertFalse(restored.complete());
    }
    @Test void olderJsonWithoutOpeningFieldsStillLoads() throws Exception {
        var path = directory.resolve("legacy.json");
        var writer = new PositionWriter(path, settings());
        writer.add(position()); writer.checkpoint(1);
        var mapper = tools.jackson.databind.json.JsonMapper.builder().build();
        var root = mapper.readTree(path.toFile());
        var source = (tools.jackson.databind.node.ObjectNode) root.get("positions").get(0).get("source");
        source.remove("eco"); source.remove("opening"); source.remove("variation");
        mapper.writeValue(path.toFile(), root);
        var restored = new PositionWriter(path, settings());
        assertEquals(1, restored.processedGames());
        var old = mapper.readValue(path.toFile(), PositionWriter.Collection.class);
        assertNull(old.positions().getFirst().source().opening());
    }

    @Test void elapsedTimeAccumulatesAcrossResumeWithoutDoubleCountingCheckpoints() throws Exception {
        var path = directory.resolve("timed.json");
        var clock = new java.util.concurrent.atomic.AtomicLong(0);
        var writer = new PositionWriter(path, settings(), 0, clock::get);
        clock.set(2_000_000_000L); writer.checkpoint(1);
        clock.set(5_000_000_000L); writer.checkpoint(2);
        var mapper = tools.jackson.databind.json.JsonMapper.builder().build();
        assertEquals(5000L, mapper.readValue(path.toFile(), PositionWriter.Collection.class).elapsedMillis());
        clock.set(100_000_000_000L);
        var resumed = new PositionWriter(path, settings(), clock.get(), clock::get);
        clock.set(103_000_000_000L); resumed.checkpoint(3);
        assertEquals(8000L, mapper.readValue(path.toFile(), PositionWriter.Collection.class).elapsedMillis());
    }

    @Test void legacyUnknownDurationRemainsUnknownOnResume() throws Exception {
        var path = directory.resolve("untimed.json");
        var writer = new PositionWriter(path, settings()); writer.checkpoint(1);
        var mapper = tools.jackson.databind.json.JsonMapper.builder().build();
        var root = (tools.jackson.databind.node.ObjectNode) mapper.readTree(path.toFile());
        root.remove("elapsedMillis"); mapper.writeValue(path.toFile(), root);
        var resumed = new PositionWriter(path, settings()); resumed.checkpoint(2);
        assertNull(mapper.readValue(path.toFile(), PositionWriter.Collection.class).elapsedMillis());
    }

    @Test void ignoresCountersAndUncapturableEnPassant() {
        String placement = "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq ";
        assertEquals(PositionWriter.identity(placement + "e6 0 2"), PositionWriter.identity(placement + "- 9 20"));
        assertNotEquals(PositionWriter.identity("7k/8/8/3pP3/8/8/8/K7 w - d6 0 2"),
                PositionWriter.identity("7k/8/8/3pP3/8/8/8/K7 w - - 0 2"));
    }
    @Test void rejectsWrongHistoryAndConfiguration() throws Exception {
        var good = position();
        var wrong = new GeneratedPosition(good.id(), good.phase(), null, good.fen(),
                new GeneratedPosition.Source(1, null, good.source().startingFen(), List.of("d4", "d5"), 2, null, null, null), good.analysis(), good.selection());
        assertThrows(java.io.IOException.class, () -> PositionWriter.validate(wrong, settings()));
        var path = directory.resolve("positions.json");
        var writer = new PositionWriter(path, settings()); writer.add(good); writer.checkpoint(1);
        var changed = new PositionWriter.Generation("other.pgn", "other", "test", 12, 50, 42, 1, 3, 13, 20, 1, 1, 64, "per-game-v1");
        assertThrows(java.io.IOException.class, () -> new PositionWriter(path, changed));
    }
    @Test void duplicatesDoNotConsumeEngineBudget() throws Exception {
        var one = new CandidateSearch.Candidate(30, "duplicate", PositionClassifier.Phase.MIDDLEGAME, null);
        var two = new CandidateSearch.Candidate(31, "new", PositionClassifier.Phase.MIDDLEGAME, null);
        var plan = new CandidateSearch.Plan(30, CandidateSearch.Direction.FORWARD, List.of(one, two));
        var result = PositionSelector.select(plan, fen -> { assertEquals("new", fen); return new StockfishClient.Evaluation(0, false, 12); }, 1, 50, "duplicate"::equals);
        assertEquals(two, result.selection().candidate());
        assertEquals(1, result.evaluated());
    }
}



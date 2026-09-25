package com.dropinchess.positiongen;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class StockfishClientTest {
    @Test void normalizesBlackScoreAndIncludesBoundary() {
        var score = StockfishClient.parseScore("info depth 12 score cp -50 nodes 123", true);
        assertEquals(50, score.whiteCp());
        assertTrue(score.balanced(50));
        assertFalse(new StockfishClient.Evaluation(51, false, 12).balanced(50));
    }
    @Test void excludesMateAndIncompleteBounds() {
        assertFalse(StockfishClient.parseScore("info depth 12 score mate 3", false).balanced(50));
        assertNull(StockfishClient.parseScore("info depth 12 score cp 0 lowerbound", false));
        assertNull(StockfishClient.parseScore("info string score cp 0", false));
    }
    private CandidateSearch.Plan plan() {
        var candidate = new CandidateSearch.Candidate(30, "fen", PositionClassifier.Phase.MIDDLEGAME, null);
        return new CandidateSearch.Plan(30, CandidateSearch.Direction.FORWARD, List.of(candidate, candidate, candidate));
    }
    @Test void selectsFirstBalancedAndStops() throws Exception {
        var calls = new AtomicInteger();
        var result = PositionSelector.select(plan(), fen -> new StockfishClient.Evaluation(calls.incrementAndGet() == 1 ? 100 : 0, false, 12), 20, 50);
        assertNotNull(result.selection());
        assertEquals(2, result.evaluated());
        assertEquals(2, calls.get());
    }
    @Test void honorsBudgetAndPropagatesFailure() throws Exception {
        var result = PositionSelector.select(plan(), fen -> new StockfishClient.Evaluation(100, false, 12), 1, 50);
        assertNull(result.selection());
        assertEquals(1, result.evaluated());
        assertThrows(java.io.IOException.class, () -> PositionSelector.select(plan(), fen -> { throw new java.io.IOException("engine died"); }, 20, 50));
    }
}

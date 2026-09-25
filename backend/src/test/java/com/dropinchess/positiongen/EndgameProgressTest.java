package com.dropinchess.positiongen;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class EndgameProgressTest {
    private CandidateSearch.Candidate candidate(int clock, PositionClassifier.Phase phase) {
        return new CandidateSearch.Candidate(183, "8/1p6/2p3k1/1bP1B1Pp/1P5K/8/8/8 b - - " + clock + " 92", phase, null);
    }
    @Test void rejectsTwentyAndOlderButAcceptsNineteen() {
        assertTrue(PositionSelector.hasRecentProgress(candidate(19, PositionClassifier.Phase.ENDGAME)));
        assertFalse(PositionSelector.hasRecentProgress(candidate(20, PositionClassifier.Phase.ENDGAME)));
        assertFalse(PositionSelector.hasRecentProgress(candidate(43, PositionClassifier.Phase.ENDGAME)));
        assertTrue(PositionSelector.hasRecentProgress(candidate(43, PositionClassifier.Phase.MIDDLEGAME)));
        assertTrue(PositionSelector.hasRecentProgress(candidate(0, PositionClassifier.Phase.ENDGAME)));
    }
    @Test void rejectedPositionsDoNotConsumeBudgetAndSearchContinues() throws Exception {
        var stale = candidate(43, PositionClassifier.Phase.ENDGAME);
        var fresh = candidate(0, PositionClassifier.Phase.ENDGAME);
        var plan = new CandidateSearch.Plan(183, CandidateSearch.Direction.FORWARD, List.of(stale, fresh));
        var result = PositionSelector.select(plan, fen -> {
            assertEquals(fresh.fen(), fen);
            return new StockfishClient.Evaluation(0, false, 12);
        }, 1, 50);
        assertEquals(fresh, result.selection().candidate());
        assertEquals(1, result.evaluated());
    }
}

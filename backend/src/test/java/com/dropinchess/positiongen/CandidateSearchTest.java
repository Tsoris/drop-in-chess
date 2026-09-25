package com.dropinchess.positiongen;

import com.github.bhlangonijr.chesslib.Board;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.dropinchess.positiongen.PositionClassifier.Phase.*;
import static com.dropinchess.positiongen.CandidateSearch.Direction.*;

class CandidateSearchTest {
    private CandidateSearch.Candidate candidate(int ply, PositionClassifier.Phase phase) {
        return new CandidateSearch.Candidate(ply, "test-fen-" + ply, phase, null);
    }

    @Test void forwardStopsAtEndgame() {
        var positions = List.of(candidate(30, MIDDLEGAME), candidate(31, MIDDLEGAME), candidate(32, ENDGAME));
        var plan = CandidateSearch.fromStart(positions, 30, 30, FORWARD);
        assertEquals(List.of(30, 31), plan.candidates().stream().map(CandidateSearch.Candidate::ply).toList());
    }

    @Test void backwardStartsAtLatestMiddleAndHonorsLowerBound() {
        var positions = List.of(candidate(29, MIDDLEGAME), candidate(30, MIDDLEGAME), candidate(31, MIDDLEGAME), candidate(32, ENDGAME));
        var plan = CandidateSearch.fromStart(positions, 32, 30, BACKWARD);
        assertEquals(List.of(31, 30), plan.candidates().stream().map(CandidateSearch.Candidate::ply).toList());
    }

    @Test void shortGameHasNoMiddleSearch() {
        assertTrue(new CandidateSearch(15, 3, new Random(42)).middlegame(List.of(), 10).candidates().isEmpty());
    }

    @Test void seedIsReproducibleAndRangeUsesGameLength() {
        var positions = new ArrayList<CandidateSearch.Candidate>();
        for (int ply = 1; ply <= 120; ply++) positions.add(candidate(ply, ply < 30 ? EARLY : MIDDLEGAME));
        var one = new CandidateSearch(15, 3, new Random(42)).middlegame(positions, 120);
        var two = new CandidateSearch(15, 3, new Random(42)).middlegame(positions, 120);
        assertEquals(one, two);
        assertTrue(one.randomStartPly() >= 30 && one.randomStartPly() <= 70);
    }

    @Test void endgameOnlyIncludesPlayableEndgamesFromRandomStart() {
        var positions = List.of(candidate(30, MIDDLEGAME), candidate(31, ENDGAME), candidate(32, ENDGAME), candidate(33, TERMINAL));
        var plan = new CandidateSearch(15, 3, new Random(42)).endgame(positions);
        assertFalse(plan.candidates().isEmpty());
        assertTrue(plan.candidates().stream().allMatch(p -> p.phase() == ENDGAME && p.ply() >= plan.randomStartPly()));
    }

    @Test void classifierExcludesDrawsAndRequiresBothSidesUnderThreshold() {
        var classifier = new PositionClassifier(15, 13);
        var board = new Board();
        assertEquals(EARLY, classifier.classify(board, 29));
        assertEquals(MIDDLEGAME, classifier.classify(board, 30));
        board.loadFromFen("7k/8/8/8/8/8/8/K7 w - - 0 1");
        assertEquals(TERMINAL, classifier.classify(board, 60));
        board.loadFromFen("7k/7p/8/8/8/8/P7/KR6 w - - 0 1");
        assertEquals(ENDGAME, classifier.classify(board, 60));
        assertEquals(PositionClassifier.EndgameType.ROOK, classifier.endgameType(board));
        board.loadFromFen("6rk/6pp/8/8/8/8/PP6/KRRQ4 w - - 0 1");
        assertEquals(MIDDLEGAME, classifier.classify(board, 60));
    }
}

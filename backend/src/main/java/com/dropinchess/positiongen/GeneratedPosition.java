package com.dropinchess.positiongen;

import java.util.List;

public record GeneratedPosition(String id, PositionClassifier.Phase phase,
        PositionClassifier.EndgameType endgameType, String fen, Source source,
        Analysis analysis, Selection selection) {
    public record Source(int gameIndex, String gameUrl, String startingFen, List<String> movesSan, int ply,
                         String eco, String opening, String variation) {
        public Source { movesSan = List.copyOf(movesSan); }
    }
    public record Analysis(int whiteScoreCp, int depth) {}
    public record Selection(int randomStartPly, CandidateSearch.Direction direction, int evaluations) {}
}


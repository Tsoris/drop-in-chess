package com.dropinchess.positiongen;

import java.io.IOException;

public final class PositionSelector {
    public static final int ENDGAME_HALFMOVE_LIMIT = 20;

    /** FEN's halfmove clock resets on either a pawn move or a capture. */
    static boolean hasRecentProgress(CandidateSearch.Candidate candidate) {
        return candidate.phase() != PositionClassifier.Phase.ENDGAME
                || Integer.parseInt(candidate.fen().split(" ")[4]) < ENDGAME_HALFMOVE_LIMIT;
    }
    @FunctionalInterface public interface Evaluator {
        StockfishClient.Evaluation evaluate(String fen) throws IOException;
    }
    public record Selection(CandidateSearch.Candidate candidate, StockfishClient.Evaluation evaluation, int evaluated) {}
    public record Result(Selection selection, int evaluated) {}

    public static Result select(CandidateSearch.Plan plan, Evaluator engine, int maxCandidates, int balanceCp) throws IOException {
        return select(plan, engine, maxCandidates, balanceCp, fen -> false);
    }

    public static Result select(CandidateSearch.Plan plan, Evaluator engine, int maxCandidates, int balanceCp, java.util.function.Predicate<String> alreadySaved) throws IOException {
        if (maxCandidates < 1 || balanceCp < 0) throw new IllegalArgumentException("Invalid selection limits");
        int evaluated = 0;
        for (var candidate : plan.candidates()) {
            if (evaluated >= maxCandidates) break;
            if (!hasRecentProgress(candidate) || alreadySaved.test(candidate.fen())) continue;
            var score = engine.evaluate(candidate.fen());
            evaluated++;
            if (score.balanced(balanceCp)) return new Result(new Selection(candidate, score, evaluated), evaluated);
        }
        return new Result(null, evaluated);
    }
}



package com.dropinchess.positiongen;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import com.dropinchess.positiongen.PositionClassifier.Phase;
import com.dropinchess.positiongen.PositionClassifier.EndgameType;

/** Builds search orders only: candidates are not yet engine-approved positions. */
public class CandidateSearch {
    public record Candidate(int ply, String fen, Phase phase, EndgameType endgameType) {}
    public enum Direction { FORWARD, BACKWARD }
    public record Plan(int randomStartPly, Direction direction, List<Candidate> candidates) {
        public Plan { candidates = List.copyOf(candidates); }
    }

    private final int minimumMove;
    private final int rangeDivisor;
    private final Random random;

    public CandidateSearch(int minimumMove, int rangeDivisor, Random random) {
        if (minimumMove < 1 || rangeDivisor < 1) throw new IllegalArgumentException("Invalid search settings");
        this.minimumMove = minimumMove;
        this.rangeDivisor = rangeDivisor;
        this.random = random;
    }

    public Plan middlegame(List<Candidate> positions, int totalPlies) {
        int minimumPly = minimumMove * 2;
        int latestPly = Math.min(totalPlies, (minimumMove + (totalPlies / 2) / rangeDivisor) * 2);
        if (latestPly < minimumPly) return new Plan(-1, Direction.FORWARD, List.of());
        int start = random.nextInt(minimumPly, latestPly + 1);
        Candidate startingPosition = positions.stream().filter(p -> p.ply() == start).findFirst().orElseThrow();
        Direction direction = startingPosition.phase() == Phase.ENDGAME || startingPosition.phase() == Phase.TERMINAL
                ? Direction.BACKWARD : Direction.FORWARD;
        return fromStart(positions, start, minimumPly, direction);
    }

    // Package-visible for deterministic boundary tests.
    static Plan fromStart(List<Candidate> positions, int start, int minimumPly, Direction direction) {
        List<Candidate> ordered = new ArrayList<>();
        if (direction == Direction.BACKWARD) {
            for (int i = positions.size() - 1; i >= 0; i--) {
                Candidate position = positions.get(i);
                if (position.ply() <= start && position.ply() >= minimumPly && position.phase() == Phase.MIDDLEGAME) {
                    ordered.add(position);
                }
            }
        } else {
            for (Candidate position : positions) {
                if (position.ply() < start) continue;
                if (position.phase() == Phase.ENDGAME || position.phase() == Phase.TERMINAL) break;
                if (position.phase() == Phase.MIDDLEGAME) ordered.add(position);
            }
        }
        return new Plan(start, direction, ordered);
    }

    public Plan endgame(List<Candidate> positions) {
        List<Candidate> eligible = positions.stream().filter(p -> p.phase() == Phase.ENDGAME).toList();
        if (eligible.isEmpty()) return new Plan(-1, Direction.FORWARD, List.of());
        int start = random.nextInt(eligible.size());
        // Search forward from the random eligible point, without wrapping to earlier positions.
        return new Plan(eligible.get(start).ply(), Direction.FORWARD, eligible.subList(start, eligible.size()));
    }
}

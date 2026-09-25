package com.dropinchess.positiongen;

import com.github.bhlangonijr.chesslib.Board;

/** Material-based heuristic, independent of engine evaluation. */
public class PositionClassifier {
    public enum Phase { EARLY, MIDDLEGAME, ENDGAME, TERMINAL }
    public enum EndgameType { PAWN_ONLY, ROOK, BISHOP, KNIGHT, MINOR_PIECE, QUEEN, MIXED }

    private final int minimumPly;
    private final int endgameMaterial;

    public PositionClassifier(int minimumFullMoves, int endgameMaterial) {
        if (minimumFullMoves < 1 || endgameMaterial < 0) {
            throw new IllegalArgumentException("Invalid phase thresholds");
        }
        this.minimumPly = minimumFullMoves * 2;
        this.endgameMaterial = endgameMaterial;
    }

    public Phase classify(Board board, int ply) {
        if (board.isMated() || board.isDraw()) return Phase.TERMINAL;
        int white = 0, black = 0;
        for (char piece : board.getFen().split(" ")[0].toCharArray()) {
            int value = switch (Character.toLowerCase(piece)) {
                case 'n', 'b' -> 3;
                case 'r' -> 5;
                case 'q' -> 9;
                default -> 0;
            };
            if (Character.isUpperCase(piece)) white += value;
            else black += value;
        }
        if (white <= endgameMaterial && black <= endgameMaterial) return Phase.ENDGAME;
        return ply >= minimumPly ? Phase.MIDDLEGAME : Phase.EARLY;
    }

    public EndgameType endgameType(Board board) {
        String pieces = board.getFen().split(" ")[0].toLowerCase();
        boolean rook = pieces.contains("r"), bishop = pieces.contains("b");
        boolean knight = pieces.contains("n"), queen = pieces.contains("q");
        int types = (rook ? 1 : 0) + (bishop ? 1 : 0) + (knight ? 1 : 0) + (queen ? 1 : 0);
        if (types == 0) return EndgameType.PAWN_ONLY;
        if (!rook && !queen && bishop && knight) return EndgameType.MINOR_PIECE;
        if (types > 1) return EndgameType.MIXED;
        if (rook) return EndgameType.ROOK;
        if (bishop) return EndgameType.BISHOP;
        if (knight) return EndgameType.KNIGHT;
        return EndgameType.QUEEN;
    }
}

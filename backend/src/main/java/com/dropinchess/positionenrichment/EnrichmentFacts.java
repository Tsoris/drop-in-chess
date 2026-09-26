package com.dropinchess.positionenrichment;

import com.github.bhlangonijr.chesslib.Board;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Calculates objective chess facts used by prompts, validation, and stored metadata. */
final class EnrichmentFacts {
    static final String OPENING_METADATA = "OPENING_METADATA";
    static final String MOVE_HISTORY = "MOVE_HISTORY";

    record FactSet(Map<String, String> evidence, Map<String, Object> storedFacts) {
        String promptText() {
            return evidence.entrySet().stream()
                    .map(entry -> "[" + entry.getKey() + "] " + entry.getValue())
                    .reduce((left, right) -> left + "\n" + right)
                    .orElseThrow();
        }

        Set<String> allowedEvidenceIds() {
            var ids = new LinkedHashSet<>(evidence.keySet());
            ids.add(OPENING_METADATA);
            ids.add(MOVE_HISTORY);
            return Set.copyOf(ids);
        }
    }

    private EnrichmentFacts() {}

    static FactSet fromFen(String fen) {
        String[] fields = fen.split(" ");
        if (fields.length != 6) throw new IllegalArgumentException("Invalid FEN for enrichment: " + fen);
        Map<Character, List<String>> squares = pieceSquares(fields[0], fen);
        String sideToMove = "w".equals(fields[1]) ? "White" : "Black";
        String whiteMaterial = sideFacts("White", squares, "KQRBNP");
        String blackMaterial = sideFacts("Black", squares, "kqrbnp");
        String materialComparison = materialComparison(squares);
        String whiteKing = squares.get('K').getFirst();
        String blackKing = squares.get('k').getFirst();
        String kingRelationship = kingRelationship(whiteKing, blackKing);
        boolean sideToMoveInCheck = sideToMoveInCheck(fen);
        String checkStatus = sideToMoveInCheck
                ? sideToMove + " is currently in check and must answer the check before pursuing a longer-term plan"
                : sideToMove + " is not currently in check";
        String castlingRights = "-".equals(fields[2]) ? "none" : fields[2];
        String bishopColors = bishopColors(squares);

        Map<String, String> evidence = new LinkedHashMap<>();
        evidence.put("SIDE_TO_MOVE", sideToMove);
        evidence.put("CHECK_STATUS", checkStatus);
        evidence.put("WHITE_MATERIAL", whiteMaterial);
        evidence.put("BLACK_MATERIAL", blackMaterial);
        evidence.put("MATERIAL_COMPARISON", materialComparison);
        evidence.put("WHITE_KING", "White king: " + whiteKing + " (" + boardRegion(whiteKing) + ")");
        evidence.put("BLACK_KING", "Black king: " + blackKing + " (" + boardRegion(blackKing) + ")");
        evidence.put("KING_RELATIONSHIP", kingRelationship);
        evidence.put("BISHOP_COLORS", bishopColors);
        evidence.put("CASTLING_RIGHTS", castlingRights);

        Map<String, Object> stored = new LinkedHashMap<>();
        stored.put("sideToMove", sideToMove);
        stored.put("sideToMoveInCheck", sideToMoveInCheck);
        stored.put("materialComparison", materialComparison);
        stored.put("whiteKing", whiteKing);
        stored.put("blackKing", blackKing);
        stored.put("kingRelationship", kingRelationship);
        stored.put("bishopColors", bishopColors);
        stored.put("castlingRights", castlingRights);
        return new FactSet(
                java.util.Collections.unmodifiableMap(new LinkedHashMap<>(evidence)),
                java.util.Collections.unmodifiableMap(new LinkedHashMap<>(stored)));
    }

    private static Map<Character, List<String>> pieceSquares(String placement, String fen) {
        Map<Character, List<String>> squares = new LinkedHashMap<>();
        for (char piece : "KQRBNPkqrbnp".toCharArray()) squares.put(piece, new ArrayList<>());
        String[] ranks = placement.split("/");
        if (ranks.length != 8) throw new IllegalArgumentException("Invalid FEN board for enrichment: " + fen);
        for (int rankIndex = 0; rankIndex < ranks.length; rankIndex++) {
            int file = 0;
            for (char value : ranks[rankIndex].toCharArray()) {
                if (Character.isDigit(value)) {
                    file += Character.digit(value, 10);
                } else {
                    if (!squares.containsKey(value) || file > 7) {
                        throw new IllegalArgumentException("Invalid FEN piece placement for enrichment: " + fen);
                    }
                    squares.get(value).add("" + (char) ('a' + file) + (8 - rankIndex));
                    file++;
                }
            }
            if (file != 8) throw new IllegalArgumentException("Invalid FEN rank for enrichment: " + fen);
        }
        if (squares.get('K').size() != 1 || squares.get('k').size() != 1) {
            throw new IllegalArgumentException("FEN must contain exactly one king per side: " + fen);
        }
        return squares;
    }

    private static boolean sideToMoveInCheck(String fen) {
        Board board = new Board();
        board.loadFromFen(fen);
        return board.isKingAttacked();
    }

    private static String sideFacts(String side, Map<Character, List<String>> squares, String pieces) {
        String[] names = {"king", "queens", "rooks", "bishops", "knights", "pawns"};
        List<String> facts = new ArrayList<>();
        for (int index = 0; index < pieces.length(); index++) {
            List<String> locations = squares.get(pieces.charAt(index));
            facts.add(names[index] + "=" + (locations.isEmpty() ? "none" : String.join(",", locations))
                    + " (" + locations.size() + ")");
        }
        return side + ": " + String.join("; ", facts);
    }

    private static String materialComparison(Map<Character, List<String>> squares) {
        char[] whitePieces = "QRBNP".toCharArray();
        char[] blackPieces = "qrbnp".toCharArray();
        String[] names = {"queens", "rooks", "bishops", "knights", "pawns"};
        List<String> comparisons = new ArrayList<>();
        for (int index = 0; index < names.length; index++) {
            int whiteCount = squares.get(whitePieces[index]).size();
            int blackCount = squares.get(blackPieces[index]).size();
            if (whiteCount == blackCount) {
                comparisons.add(names[index] + " equal (" + whiteCount + " each)");
            } else {
                String leader = whiteCount > blackCount ? "White" : "Black";
                int difference = Math.abs(whiteCount - blackCount);
                String pieceName = difference == 1 ? names[index].substring(0, names[index].length() - 1) : names[index];
                comparisons.add(leader + " has " + difference + " more " + pieceName
                        + " (White " + whiteCount + ", Black " + blackCount + ")");
            }
        }
        return String.join("; ", comparisons);
    }

    private static String kingRelationship(String whiteSquare, String blackSquare) {
        String whiteRegion = boardRegion(whiteSquare);
        String blackRegion = boardRegion(blackSquare);
        String relationship;
        if (whiteRegion.equals(blackRegion)) {
            relationship = "both kings are on the " + whiteRegion;
        } else if (!"center files".equals(whiteRegion) && !"center files".equals(blackRegion)) {
            relationship = "the kings are on opposite wings";
        } else {
            relationship = "at least one king is on the center files";
        }
        return "White " + whiteSquare + " (" + whiteRegion + "), Black " + blackSquare
                + " (" + blackRegion + "); " + relationship;
    }

    private static String boardRegion(String square) {
        char file = square.charAt(0);
        if (file <= 'c') return "queenside";
        if (file >= 'f') return "kingside";
        return "center files";
    }

    private static String bishopColors(Map<Character, List<String>> squares) {
        return "White bishops: " + coloredSquares(squares.get('B'))
                + "; Black bishops: " + coloredSquares(squares.get('b'));
    }

    private static String coloredSquares(List<String> squares) {
        if (squares.isEmpty()) return "none";
        return squares.stream().map(square -> square + " (" + squareColor(square) + ")")
                .reduce((left, right) -> left + ", " + right).orElseThrow();
    }

    private static String squareColor(String square) {
        int file = square.charAt(0) - 'a' + 1;
        int rank = Character.digit(square.charAt(1), 10);
        return (file + rank) % 2 == 0 ? "dark" : "light";
    }
}

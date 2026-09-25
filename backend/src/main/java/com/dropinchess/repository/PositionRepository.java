package com.dropinchess.repository;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Piece;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** Loads the published collection once. Gameplay never launches Stockfish or reads the PGN. */
@Repository
public class PositionRepository {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Position(String id, String phase, String fen) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CollectionFile(int schemaVersion, boolean complete, List<Position> positions) {}

    private final List<Position> positions;

    public PositionRepository(@Value("${dropinchess.positions.location:classpath:positions/positions.json}") Resource resource) throws IOException {
        try (var input = resource.getInputStream()) {
            var collection = JsonMapper.builder().build().readValue(input, CollectionFile.class);
            if (collection.schemaVersion() != 1 || !collection.complete()
                    || collection.positions() == null || collection.positions().isEmpty()) {
                throw new IllegalArgumentException("Expected a complete, nonempty schema-version-1 collection");
            }
            Set<String> ids = new HashSet<>();
            for (var position : collection.positions()) {
                if (position == null || position.id() == null || position.id().isBlank() || !ids.add(position.id())) {
                    throw new IllegalArgumentException("Missing or duplicate position ID");
                }
                if (!Set.of("MIDDLEGAME", "ENDGAME").contains(position.phase())) {
                    throw new IllegalArgumentException("Invalid phase for " + position.id());
                }
                if (position.fen() == null || position.fen().split(" ").length != 6) {
                    throw new IllegalArgumentException("Invalid FEN for " + position.id());
                }
                Board board = new Board();
                board.loadFromFen(position.fen());
                if (board.getPieceLocation(Piece.WHITE_KING).size() != 1
                        || board.getPieceLocation(Piece.BLACK_KING).size() != 1
                        || board.isMated() || board.isDraw() || board.legalMoves().isEmpty()) {
                    throw new IllegalArgumentException("Unplayable position " + position.id());
                }
            }
            positions = List.copyOf(collection.positions());
        } catch (Exception failure) {
            throw new IOException("Cannot load starting positions from " + resource.getDescription() + ": " + failure.getMessage(), failure);
        }
    }

    public Position randomPosition() {
        return positions.get(ThreadLocalRandom.current().nextInt(positions.size()));
    }

    public List<Position> allPositions() { return positions; }
}

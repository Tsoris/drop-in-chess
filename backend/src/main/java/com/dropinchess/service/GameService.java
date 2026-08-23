package com.dropinchess.service;

import com.dropinchess.model.Game;
import com.dropinchess.DataTransferObject.MoveResponse;
import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Square;
import com.github.bhlangonijr.chesslib.move.Move;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GameService {

    private final Map<UUID, Game> activeGames = new ConcurrentHashMap<>();

    /**
     * Creates a new game from the provided FEN position.
     *
     * @param fen the FEN representing the starting position
     * @return the UUID assigned to the new game
     */
    public Game createGame(String fen) {
        Board board = new Board();
        board.loadFromFen(fen);

        UUID gameId = UUID.randomUUID();

        Game newGame = new Game(gameId, board);

        activeGames.put(gameId, newGame);

        return newGame;
    }

    public Game getGame(UUID gameId) {
        return activeGames.get(gameId);
    }

    /**
     * This function is intended for development and administrative
     * purposes and provides a view of the games currently stored in memory.
     */
    public Collection<Game> getAllGames() {
        return this.activeGames.values();
    }


    /**
     * Attempts to apply a move to an active game.
     *
     * <p>The move is validated against the backend's authoritative board.
     * If legal, the move is applied and the resulting FEN is compared with
     * the FEN produced by the frontend.</p>
     *
     * @param gameId the unique ID of the game
     * @param from the square the piece is moving from
     * @param to the square the piece is moving to
     * @param checkFen the resulting FEN produced by the frontend
     * @return the result of the move, including its validity, synchronization
     *         status, and authoritative backend FEN
     */
    public MoveResponse makeMove(
            UUID gameId,
            Square from,
            Square to,
            String checkFen) {

        Game game = getGame(gameId);
        Board board = game.getBoard();

        Move attempt = new Move(from, to);

        if (!board.isMoveLegal(attempt, true)) {
            return new MoveResponse(
                    false,
                    false,
                    board.getFen()
            );
        }

        board.doMove(attempt);

        String backendFen = board.getFen();
        boolean synchronizedBoards = backendFen.equals(checkFen);

        return new MoveResponse(
                true,
                synchronizedBoards,
                backendFen
        );
    }
}

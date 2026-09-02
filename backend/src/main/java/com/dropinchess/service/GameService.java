package com.dropinchess.service;

import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.Side;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger logger =
            LoggerFactory.getLogger(GameService.class);

    /**
     * Creates a new game from the provided FEN position.
     *
     * @param startingFen the FEN representing the starting position
     * @return the UUID assigned to the new game
     */
    public Game createGame(String startingFen) {
        Board board = new Board();
        board.loadFromFen(startingFen);

        UUID gameId = UUID.randomUUID();

        Game newGame = new Game(gameId, startingFen, board);

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

    public boolean claimDraw(UUID gameId) {
        Game game = getGame(gameId);
        return game != null && game.claimDraw();
    }


    /**
     * Attempts to apply a move to an active game.
     *
     * The move is validated and applied against the backend's authoritative
     * board. The resulting backend FEN is compared with the FEN produced by the
     * frontend after its local move attempt to determine whether the two boards
     * are synchronized. If the move is rejected, the unchanged backend FEN is
     * compared with the frontend FEN instead.
     *
     * @param gameId the unique ID of the game
     * @param from the square the piece is moving from
     * @param to the square the piece is moving to
     * @param checkFen the FEN produced by the frontend after its local move attempt
     * @return the result of the move, including whether it was applied,
     *         whether the frontend and backend boards are synchronized,
     *         and the authoritative backend FEN
     */
    public MoveResponse makeMove(
            UUID gameId,
            Square from,
            Square to,
            String promotion,
            String checkFen) {

        Game game = getGame(gameId);
        Board board = game.getBoard();

        if (game.isCompleted()) {
            String boardFen = board.getFen();
            return new MoveResponse(
                    false,
                    boardFen.equals(checkFen),
                    game.getGameStatus(),
                    game.getGameResult(),
                    game.getGameEndReason(),
                    game.getAvailableDrawClaims(),
                    boardFen
            );
        }

        Move attempt;

        if (promotion != null) {
            Piece movingPiece = board.getPiece(from);

            Piece promotionPiece = switch (promotion.toUpperCase()) {
                case "Q" -> movingPiece.getPieceSide() == Side.WHITE
                        ? Piece.WHITE_QUEEN
                        : Piece.BLACK_QUEEN;
                case "R" -> movingPiece.getPieceSide() == Side.WHITE
                        ? Piece.WHITE_ROOK
                        : Piece.BLACK_ROOK;
                case "B" -> movingPiece.getPieceSide() == Side.WHITE
                        ? Piece.WHITE_BISHOP
                        : Piece.BLACK_BISHOP;
                case "N" -> movingPiece.getPieceSide() == Side.WHITE
                        ? Piece.WHITE_KNIGHT
                        : Piece.BLACK_KNIGHT;
                default -> throw new IllegalArgumentException("Invalid promotion piece");
            };

            attempt = new Move(from, to, promotionPiece);
        } else {
            attempt = new Move(from, to);
        }

        if (!board.isMoveLegal(attempt, true)) {
            logger.warn(
                    "Rejected illegal move, gameId={}, from={}, to={}",
                    gameId, from, to
                    );

            String boardFen = board.getFen();
            boolean synchronizedBoards = boardFen.equals(checkFen);

            if(!synchronizedBoards){
                logger.error(
                        "Backend Rejected move.  Frontend/Backend mismatch, gameId={}, from={}, to={}, backendFen={}, clientFen={}",
                        gameId, from, to, boardFen, checkFen
                );
            }

            return new MoveResponse(
                    false,
                    synchronizedBoards,
                    game.getGameStatus(),
                    game.getGameResult(),
                    game.getGameEndReason(),
                    game.getAvailableDrawClaims(),
                    boardFen
            );
        }

        boolean success = board.doMove(attempt);
        if (!success) {
            logger.error(
                    "Failed to apply legal move. gameId={}, from={}, to={}, Fen={}",
                    gameId, from, to, board.getFen()
            );
        }

        String boardFenAfterMove = board.getFen();
        game.updateLastActivity();
        game.evaluatePosition();

        boolean areBoardsSynchronized = boardFenAfterMove.equals(checkFen);

        if (!areBoardsSynchronized) {
            logger.error(
                    "Frontend/backend misMatch. gameId = {}, backendFen={}, clientFen={}",
                    gameId, boardFenAfterMove, checkFen
            );
        }
        return new MoveResponse(
                success,
                areBoardsSynchronized,
                game.getGameStatus(),
                game.getGameResult(),
                game.getGameEndReason(),
                game.getAvailableDrawClaims(),
                boardFenAfterMove
        );
    }
}

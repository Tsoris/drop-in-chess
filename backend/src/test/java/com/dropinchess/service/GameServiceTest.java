package com.dropinchess.service;

import com.dropinchess.DataTransferObject.MoveResponse;
import com.dropinchess.model.Game;
import com.dropinchess.model.GameEndReason;
import com.dropinchess.model.GameResult;
import com.dropinchess.model.GameStatus;
import com.github.bhlangonijr.chesslib.Square;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameServiceTest {
    private static final String STARTING_FEN =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    private GameService gameService;

    @BeforeEach
    void setUp() {
        gameService = new GameService();
    }

    @Test
    void detectsCheckmate() {
        MoveResponse response = statusFromRejectedMove(
                "7k/6Q1/6K1/8/8/8/8/8 b - - 0 1",
                Square.H8,
                Square.H7
        );

        assertFalse(response.valid());
        assertEquals(GameStatus.COMPLETED, response.gameStatus());
        assertEquals(GameResult.WHITE_WINS, response.gameResult());
        assertEquals(GameEndReason.CHECKMATE, response.gameEndReason());
    }

    @Test
    void detectsStalemate() {
        MoveResponse response = statusFromRejectedMove(
                "7k/5Q2/6K1/8/8/8/8/8 b - - 0 1",
                Square.H8,
                Square.H7
        );

        assertFalse(response.valid());
        assertEquals(GameStatus.COMPLETED, response.gameStatus());
        assertEquals(GameResult.DRAW, response.gameResult());
        assertEquals(GameEndReason.STALEMATE, response.gameEndReason());
    }

    @Test
    void detectsDrawByInsufficientMaterial() {
        Game game = gameService.createGame("7k/8/6K1/8/8/8/8/8 w - - 0 1");

        MoveResponse response = gameService.makeMove(
                game.getId(),
                Square.G6,
                Square.F6,
                null,
                "7k/8/6K1/8/8/8/8/8 w - - 0 1"
        );

        assertFalse(response.valid());
        assertTrue(response.synchronizedBoards());
        assertEquals(GameStatus.COMPLETED, response.gameStatus());
        assertEquals(GameResult.DRAW, response.gameResult());
        assertEquals(GameEndReason.INSUFFICIENT_MATERIAL, response.gameEndReason());
    }

    @Test
    void remainsInProgressAtFiftyMoveClaimThreshold() {
        Game game = gameService.createGame("7k/8/8/8/8/8/R7/K7 w - - 99 1");

        MoveResponse response = gameService.makeMove(
                game.getId(),
                Square.A2,
                Square.B2,
                null,
                "7k/8/8/8/8/8/1R6/K7 b - - 100 1"
        );

        assertTrue(response.valid());
        assertEquals(GameStatus.IN_PROGRESS, response.gameStatus());
        assertNull(response.gameResult());
        assertNull(response.gameEndReason());
        assertTrue(response.availableDrawClaims().contains(GameEndReason.MOVE_RULE));
    }

    @Test
    void claimsDrawByFiftyMoveRuleAndRejectsLaterMoves() {
        String claimableFen = "7k/8/8/8/8/8/R7/K7 w - - 100 1";
        Game game = gameService.createGame(claimableFen);

        boolean claimed = gameService.claimDraw(game.getId());
        MoveResponse response = gameService.makeMove(
                game.getId(),
                Square.A2,
                Square.B2,
                null,
                claimableFen
        );

        assertTrue(claimed);
        assertFalse(response.valid());
        assertTrue(response.synchronizedBoards());
        assertEquals(GameStatus.COMPLETED, response.gameStatus());
        assertEquals(GameResult.DRAW, response.gameResult());
        assertEquals(GameEndReason.MOVE_RULE, response.gameEndReason());
    }

    @Test
    void remainsInProgressAtThreefoldRepetitionClaimThreshold() {
        Game game = gameService.createGame(STARTING_FEN);

        repeatKnightCycle(game.getId());
        MoveResponse response = repeatKnightCycle(game.getId());

        assertTrue(response.valid());
        assertEquals(GameStatus.IN_PROGRESS, response.gameStatus());
        assertNull(response.gameResult());
        assertNull(response.gameEndReason());
        assertTrue(response.availableDrawClaims().contains(GameEndReason.REPETITION));
    }

    @Test
    void claimsDrawByThreefoldRepetition() {
        Game game = gameService.createGame(STARTING_FEN);
        repeatKnightCycle(game.getId());
        repeatKnightCycle(game.getId());

        boolean claimed = gameService.claimDraw(game.getId());

        assertTrue(claimed);
        assertEquals(GameStatus.COMPLETED, game.getGameStatus());
        assertEquals(GameResult.DRAW, game.getGameResult());
        assertEquals(GameEndReason.REPETITION, game.getGameEndReason());
    }

    @Test
    void rejectsDrawClaimWhenNoClaimIsAvailable() {
        Game game = gameService.createGame(STARTING_FEN);

        boolean claimed = gameService.claimDraw(game.getId());

        assertFalse(claimed);
        assertEquals(GameStatus.IN_PROGRESS, game.getGameStatus());
        assertNull(game.getGameResult());
        assertNull(game.getGameEndReason());
    }

    @Test
    void detectsAutomaticDrawByFivefoldRepetition() {
        Game game = gameService.createGame(STARTING_FEN);

        repeatKnightCycle(game.getId());
        repeatKnightCycle(game.getId());
        repeatKnightCycle(game.getId());
        MoveResponse response = repeatKnightCycle(game.getId());

        assertTrue(response.valid());
        assertEquals(GameStatus.COMPLETED, response.gameStatus());
        assertEquals(GameResult.DRAW, response.gameResult());
        assertEquals(GameEndReason.REPETITION, response.gameEndReason());
    }

    @Test
    void detectsAutomaticDrawBySeventyFiveMoveRule() {
        Game game = gameService.createGame("7k/8/8/8/8/8/R7/K7 w - - 149 1");

        MoveResponse response = gameService.makeMove(
                game.getId(),
                Square.A2,
                Square.B2,
                null,
                "7k/8/8/8/8/8/1R6/K7 b - - 150 1"
        );

        assertTrue(response.valid());
        assertEquals(GameStatus.COMPLETED, response.gameStatus());
        assertEquals(GameResult.DRAW, response.gameResult());
        assertEquals(GameEndReason.MOVE_RULE, response.gameEndReason());
    }

    @Test
    void reportsInProgressForOrdinaryPosition() {
        Game game = gameService.createGame(STARTING_FEN);

        MoveResponse response = gameService.makeMove(
                game.getId(),
                Square.E2,
                Square.E4,
                null,
                "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"
        );

        assertTrue(response.valid());
        assertTrue(response.synchronizedBoards());
        assertEquals(GameStatus.IN_PROGRESS, response.gameStatus());
        assertNull(response.gameResult());
        assertNull(response.gameEndReason());
        assertTrue(response.availableDrawClaims().isEmpty());
    }

    private MoveResponse statusFromRejectedMove(String fen, Square from, Square to) {
        Game game = gameService.createGame(fen);
        return gameService.makeMove(game.getId(), from, to, null, fen);
    }

    private MoveResponse repeatKnightCycle(UUID gameId) {
        Game game = gameService.getGame(gameId);
        int fullMove = game.getBoard().getMoveCounter();
        int halfMove = game.getBoard().getHalfMoveCounter();

        gameService.makeMove(
                gameId,
                Square.G1,
                Square.F3,
                null,
                "rnbqkbnr/pppppppp/8/8/8/5N2/PPPPPPPP/RNBQKB1R b KQkq - "
                        + (halfMove + 1) + " " + fullMove
        );
        gameService.makeMove(
                gameId,
                Square.G8,
                Square.F6,
                null,
                "rnbqkb1r/pppppppp/5n2/8/8/5N2/PPPPPPPP/RNBQKB1R w KQkq - "
                        + (halfMove + 2) + " " + (fullMove + 1)
        );
        gameService.makeMove(
                gameId,
                Square.F3,
                Square.G1,
                null,
                "rnbqkb1r/pppppppp/5n2/8/8/8/PPPPPPPP/RNBQKBNR b KQkq - "
                        + (halfMove + 3) + " " + (fullMove + 1)
        );
        return gameService.makeMove(
                gameId,
                Square.F6,
                Square.G8,
                null,
                "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - "
                        + (halfMove + 4) + " " + (fullMove + 2)
        );
    }
}

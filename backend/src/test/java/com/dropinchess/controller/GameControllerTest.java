package com.dropinchess.controller;

import com.dropinchess.DataTransferObject.GameResponse;
import com.dropinchess.model.Game;
import com.dropinchess.model.GameEndReason;
import com.dropinchess.model.GameResult;
import com.dropinchess.model.GameStatus;
import com.dropinchess.service.GameService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class GameControllerTest {
    private GameService gameService;
    private GameController gameController;

    @BeforeEach
    void setUp() {
        gameService = new GameService();
        gameController = new GameController(gameService);
    }

    @Test
    void claimsAvailableDraw() {
        Game game = gameService.createGame("7k/8/8/8/8/8/R7/K7 w - - 100 1");

        ResponseEntity<GameResponse> response = gameController.claimDraw(game.getId());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        GameResponse body = response.getBody();
        assertNotNull(body);
        assertEquals(GameStatus.COMPLETED, body.status());
        assertEquals(GameResult.DRAW, body.result());
        assertEquals(GameEndReason.MOVE_RULE, body.endReason());
    }

    @Test
    void returnsConflictWhenDrawCannotBeClaimed() {
        Game game = gameService.createGame(
                "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
        );

        ResponseEntity<GameResponse> response = gameController.claimDraw(game.getId());

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(GameStatus.IN_PROGRESS, response.getBody().status());
    }

    @Test
    void returnsNotFoundForUnknownGame() {
        ResponseEntity<GameResponse> response = gameController.claimDraw(UUID.randomUUID());

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNull(response.getBody());
    }
}

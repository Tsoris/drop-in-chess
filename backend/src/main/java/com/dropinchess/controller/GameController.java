package com.dropinchess.controller;

import com.dropinchess.DataTransferObject.GameResponse;
import com.dropinchess.DataTransferObject.MoveRequest;
import com.dropinchess.model.Game;
import com.dropinchess.DataTransferObject.MoveResponse;
import com.dropinchess.service.GameService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import com.dropinchess.repository.PositionRepository;

@RestController
@CrossOrigin(origins = "http://localhost:5173")
public class GameController {

    private final GameService gameService;

    private final PositionRepository positions;

    public GameController(GameService gameService, PositionRepository positions) {
        this.gameService = gameService;
        this.positions = positions;
    }

    /**
     * Creates a new active chess game using a randomly selected starting position.
     *
     * The game is initialized on the backend, assigned a unique game ID,
     * stored as an active game session, and returned to the client with its
     * starting FEN.
     *
     * @return a response containing the created game's ID and starting FEN
     */
    @PostMapping("/games")
    public ResponseEntity<GameResponse> createGame() {

        PositionRepository.Position position = positions.randomPosition();
        Game game = gameService.createGame(position);
        GameResponse response = toGameResponse(game);

        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
    /**
     * Retrieves an active chess game by its unique game ID.
     *
     * The returned response contains the game's current authoritative FEN,
     * allowing the frontend to restore or reload the game state.
     *
     * @param gameId the unique ID of the game to retrieve
     * @return a response containing the game's ID and current FEN
     */
    @GetMapping("/games/{gameId}")
    public ResponseEntity<GameResponse> getGame(
            @PathVariable UUID gameId) {

        Game game = gameService.getGame(gameId);

        if (game == null) {
            return ResponseEntity.notFound().build();
        }

        GameResponse gameResponse = toGameResponse(game);

        return ResponseEntity.ok(gameResponse);
    }

    /**
     * Processes a move attempt for an active game.
     *
     * <p>The submitted move is passed to the game service for validation
     * and execution against the backend's authoritative game state.</p>
     *
     * @param gameId the unique ID of the game
     * @param request the requested move and resulting frontend FEN
     * @return a response describing the result of the move and the
     *         authoritative backend FEN
     */
    @PostMapping("games/{gameId}/move")
    public ResponseEntity<MoveResponse> makeMove(
            @PathVariable UUID gameId,
            @RequestBody MoveRequest request) {
        MoveResponse response = gameService.makeMove(
                gameId,
                request.from(),
                request.to(),
                request.promotion(),
                request.checkFen()
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Claims a draw when the current position has occurred three times or the
     * half-move clock has reached 100. Claims based on a proposed future move
     * are not supported by this endpoint.
     */
    @PostMapping("games/{gameId}/draw-claim")
    public ResponseEntity<GameResponse> claimDraw(
            @PathVariable UUID gameId) {
        Game game = gameService.getGame(gameId);

        if (game == null) {
            return ResponseEntity.notFound().build();
        }

        boolean claimed = gameService.claimDraw(gameId);
        GameResponse response = toGameResponse(game);

        if (!claimed) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }

        return ResponseEntity.ok(response);
    }

    private GameResponse toGameResponse(Game game) {
        return new GameResponse(
                game.getId(),
                game.getGameStatus(),
                game.getGameResult(),
                game.getGameEndReason(),
                game.getAvailableDrawClaims(),
                game.getBoard().getFen(),
                game.getPositionId(),
                game.getPhase(),
                game.getPositionContext(),
                game.getPositionSource()
        );
    }
}

package com.dropinchess.controller;

import com.dropinchess.DataTransferObject.GameResponse;
import com.dropinchess.model.Game;
import com.dropinchess.service.GameService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@RestController
@CrossOrigin(origins = "http://localhost:5173")
public class GameController {

    private final GameService gameService;

    private static final List<String> SAMPLE_FENS = List.of(
            "r1b1k2r/pp1n1ppp/4p3/3p4/1b1NnP2/2N1B3/PqP1K1PP/R2Q1B1R b kq - 1 11",
            "r4rk1/1pp2ppp/2np4/p7/2B1P1b1/1P6/PBPq1PPP/R3R1K1 w - - 0 14",
            "r4rk1/pp3ppp/2n1b3/1R6/4q3/2P2N2/P1P1BPPP/3QK2R w K - 0 15",
            "r4rk1/pb2bppp/1pn1pn2/2pp4/3P4/2PBPN2/PP1N1PPP/R1BQR1K1 w - - 4 12"
    );

    public GameController(GameService gameService) {
        this.gameService = gameService;
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

        int randomIndex = ThreadLocalRandom.current()
                .nextInt(SAMPLE_FENS.size());

        String randomFen = SAMPLE_FENS.get(randomIndex);

        Game game = gameService.createGame(randomFen);

        GameResponse response = new GameResponse(
                game.getId(),
                game.getBoard().getFen()
        );

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

        GameResponse response = new GameResponse(
                game.getId(),
                game.getBoard().getFen()
        );

        return ResponseEntity.ok(response);
    }
}

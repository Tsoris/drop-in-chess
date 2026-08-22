package com.dropinchess.service;

import com.dropinchess.model.Game;
import com.github.bhlangonijr.chesslib.Board;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
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

}

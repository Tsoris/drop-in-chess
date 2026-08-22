package com.dropinchess.model;

import com.github.bhlangonijr.chesslib.Board;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents an active chess game session.
 *
 * Maintains the current board state and tracks when the game
 * was last active.
 */
public class Game {
    private final UUID id;
    private final Board board;
    private Instant lastActivity;

    public Game(UUID id, Board board) {
        this.id = id;
        this.board = board;
        this.lastActivity = Instant.now();
    }

    public void updateLastActivity() {
        this.lastActivity = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Board getBoard() {
        return board;
    }

    public Instant getLastActivity() {
        return lastActivity;
    }
}

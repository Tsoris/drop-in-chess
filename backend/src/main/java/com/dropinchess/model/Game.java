package com.dropinchess.model;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Side;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Represents an active chess game session.
 *
 * Maintains the current board state and tracks when the game
 * was last active.
 */
public class Game {
    private final UUID id;
    private final String startingFen;
    private final Board board;
    private GameStatus gameStatus;
    private GameResult gameResult;
    private GameEndReason gameEndReason;
    private Instant lastActivity;

    public Game(UUID id, String startingFen, Board board) {
        this.id = id;
        this.startingFen = startingFen;
        this.board = board;
        this.gameStatus = GameStatus.IN_PROGRESS;
        this.lastActivity = Instant.now();
        evaluatePosition();
    }

    public void updateLastActivity() {
        this.lastActivity = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getStartingFen(){
        return startingFen;
    }

    public Board getBoard() {
        return board;
    }

    public Instant getLastActivity() {
        return lastActivity;
    }

    public GameStatus getGameStatus() {
        return this.gameStatus;
    }

    public GameResult getGameResult() {
        return gameResult;
    }

    public GameEndReason getGameEndReason() {
        return gameEndReason;
    }

    public boolean isCompleted() {
        return gameStatus == GameStatus.COMPLETED;
    }

    public Set<GameEndReason> getAvailableDrawClaims() {
        if (isCompleted()) {
            return Set.of();
        }

        EnumSet<GameEndReason> claims = EnumSet.noneOf(GameEndReason.class);
        if (board.isRepetition()) {
            claims.add(GameEndReason.REPETITION);
        }
        if (board.getHalfMoveCounter() >= 100) {
            claims.add(GameEndReason.MOVE_RULE);
        }
        return Set.copyOf(claims);
    }

    /**
     * Evaluates automatic game-ending conditions in the current position.
     * Claimable threefold-repetition and fifty-move draws are intentionally
     * excluded until the game supports an explicit draw-claim workflow.
     */
    public void evaluatePosition() {
        if (isCompleted()) {
            return;
        }

        if (board.isMated()) {
            GameResult winner = board.getSideToMove() == Side.WHITE
                    ? GameResult.BLACK_WINS
                    : GameResult.WHITE_WINS;
            complete(winner, GameEndReason.CHECKMATE);
        } else if (board.isStaleMate()) {
            complete(GameResult.DRAW, GameEndReason.STALEMATE);
        } else if (board.isInsufficientMaterial()) {
            complete(GameResult.DRAW, GameEndReason.INSUFFICIENT_MATERIAL);
        } else if (board.isRepetition(5)) {
            complete(GameResult.DRAW, GameEndReason.REPETITION);
        } else if (board.getHalfMoveCounter() >= 150) {
            complete(GameResult.DRAW, GameEndReason.MOVE_RULE);
        }
    }

    /**
     * Completes the game when the current position supports a claimable draw.
     * Threefold repetition takes precedence when both claim conditions apply.
     *
     * @return {@code true} when the draw was claimed; otherwise {@code false}
     */
    public boolean claimDraw() {
        if (isCompleted()) {
            return false;
        }

        Set<GameEndReason> availableClaims = getAvailableDrawClaims();

        if (availableClaims.contains(GameEndReason.REPETITION)) {
            complete(GameResult.DRAW, GameEndReason.REPETITION);
            return true;
        }

        if (availableClaims.contains(GameEndReason.MOVE_RULE)) {
            complete(GameResult.DRAW, GameEndReason.MOVE_RULE);
            return true;
        }

        return false;
    }

    public void complete(GameResult result, GameEndReason endReason) {
        if (isCompleted()) {
            return;
        }

        this.gameResult = Objects.requireNonNull(result);
        this.gameEndReason = Objects.requireNonNull(endReason);
        this.gameStatus = GameStatus.COMPLETED;
    }
}

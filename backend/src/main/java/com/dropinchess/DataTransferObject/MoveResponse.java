package com.dropinchess.DataTransferObject;

import com.dropinchess.model.GameStatus;
import com.dropinchess.model.GameEndReason;
import com.dropinchess.model.GameResult;

import java.util.Set;

/**
 * Represents the result of a move processed by the backend.
 *
 * @param valid whether the attempted move was legal
 * @param synchronizedBoards whether the frontend and backend produced
 *                           matching FENs after the move
 * @param fen the authoritative FEN of the backend game board
 */
public record MoveResponse(
        boolean valid,
        boolean synchronizedBoards,
        GameStatus gameStatus,
        GameResult gameResult,
        GameEndReason gameEndReason,
        Set<GameEndReason> availableDrawClaims,
        String fen
) {}

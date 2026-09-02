package com.dropinchess.DataTransferObject;

import com.dropinchess.model.GameStatus;
import com.dropinchess.model.GameEndReason;
import com.dropinchess.model.GameResult;

import java.util.Set;
import java.util.UUID;

/**
 * Response returned when a new game is created.
 */
public record GameResponse(
        UUID gameId,
        GameStatus status,
        GameResult result,
        GameEndReason endReason,
        Set<GameEndReason> availableDrawClaims,
        String fen
) {}

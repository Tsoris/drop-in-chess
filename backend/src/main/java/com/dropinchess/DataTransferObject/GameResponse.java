package com.dropinchess.DataTransferObject;

import java.util.UUID;

/**
 * Response returned when a new game is created.
 */
public record GameResponse(
        UUID gameId,
        String fen
) {}
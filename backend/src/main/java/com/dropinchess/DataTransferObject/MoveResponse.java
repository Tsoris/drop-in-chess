package com.dropinchess.DataTransferObject;

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
        String fen
) {}

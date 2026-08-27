package com.dropinchess.DataTransferObject;

import com.github.bhlangonijr.chesslib.Square;

/**
 * Represents a move attempt submitted by the client.
 *
 * @param from the square the piece is moving from
 * @param to the square the piece is moving to
 * @param checkFen the resulting FEN calculated by the frontend after the move
 */
public record MoveRequest(
        Square from,
        Square to,
        String checkFen
) {}

package com.dropinchess.model;

public enum GameEndReason {
    CHECKMATE,
    STALEMATE,
    INSUFFICIENT_MATERIAL,
    REPETITION,
    MOVE_RULE,
    RESIGNATION,
    AGREEMENT
}

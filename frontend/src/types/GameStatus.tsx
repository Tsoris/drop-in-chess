export type GameStatus =
  | "IN_PROGRESS"
  | "COMPLETED";

export type GameResult =
  | "WHITE_WINS"
  | "BLACK_WINS"
  | "DRAW";

export type GameEndReason =
  | "CHECKMATE"
  | "STALEMATE"
  | "INSUFFICIENT_MATERIAL"
  | "REPETITION"
  | "MOVE_RULE"
  | "RESIGNATION"
  | "AGREEMENT";

export type GameState = {
  status: GameStatus;
  result: GameResult | null;
  endReason: GameEndReason | null;
  availableDrawClaims: GameEndReason[];
};

export type GameResponse = GameState & {
  gameId: string;
  fen: string;
};

export type MoveResponse = {
  valid: boolean;
  synchronizedBoards: boolean;
  gameStatus: GameStatus;
  gameResult: GameResult | null;
  gameEndReason: GameEndReason | null;
  availableDrawClaims: GameEndReason[];
  fen: string;
};

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

export type PositionContext = {
  availability: "AVAILABLE" | "UNAVAILABLE";
  quality: "AI_VERIFIED" | "AI_REJECTED";
  openingContext: { summary: string } | null;
  positionGuide: { summary: string; themes: string[] } | null;
  possiblePlans: {
    white: { summary: string };
    black: { summary: string };
  } | null;
  message: string | null;
};

export type GameResponse = GameState & {
  gameId: string;
  fen: string;
  positionId: string;
  phase: "MIDDLEGAME" | "ENDGAME";
  context: PositionContext;
  source?: {
    gameUrl: string;
    eco: string;
    opening: string;
    variation: string | null;
  };
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

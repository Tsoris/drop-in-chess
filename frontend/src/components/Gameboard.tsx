import PromotionPicker, { type PromotionPiece } from "./PromotionPicker";
import { apiUrl } from "../lib/api";
import { Chess, type Square } from 'chess.js';
import { useEffect, useRef, useState } from 'react';
import { Chessboard, defaultPieces, type PieceRenderObject, type SquareHandlerArgs } from 'react-chessboard';
import type { GameState, MoveResponse } from '../types/GameStatus';

const BOARD_SQUARES = Array.from({ length: 8 }, (_, rankIndex) =>
  "abcdefgh".split("").map(file => `${file}${rankIndex + 1}` as Square)
).flat();

export function checkedKingSquare(game: Chess): Square | null {
  if (!game.isCheck()) return null;
  const checkedSide = game.turn();
  return BOARD_SQUARES.find(square => {
    const piece = game.get(square);
    return piece?.type === "k" && piece.color === checkedSide;
  }) ?? null;
}

export type BoardTheme = "wood"| "modern" | "evergreenIvory" | "knightQuest";

type BoardThemeConfig = {
  darkSquare: string;
  lightSquare: string;
  moveIndicator: "subtle" | "bold";
  pieceStyle: "classic" | "symbol";
};

const BOARD_THEMES: Record<BoardTheme, BoardThemeConfig> = {
  wood: { darkSquare: "#b58863", lightSquare: "#f0d9b5", moveIndicator: "subtle", pieceStyle: "classic" },
  modern: { darkSquare: "#111318", lightSquare: "#5c616b", moveIndicator: "bold", pieceStyle: "symbol" },
  evergreenIvory: { darkSquare: "#002E23", lightSquare: "#E8E3D5", moveIndicator: "bold", pieceStyle: "classic" },
  knightQuest: { darkSquare: "#8194ab", lightSquare: "#dee5f0", moveIndicator: "subtle", pieceStyle: "classic" }
};

const SYMBOL_GLYPHS: Record<string, string> = {
  // Use the filled glyphs for both sides so White has the same clear silhouettes.
  wP: "♟", wR: "♜", wN: "♞", wB: "♝", wQ: "♛", wK: "♚",
  bP: "♟", bR: "♜", bN: "♞", bB: "♝", bQ: "♛", bK: "♚"
};

const PIECE_STYLES: Record<BoardThemeConfig["pieceStyle"], PieceRenderObject> = {
  classic: defaultPieces,
  symbol: Object.fromEntries(Object.entries(SYMBOL_GLYPHS).map(([pieceCode, glyph]) => [
    pieceCode,
    (props?: { fill?: string; square?: string; svgStyle?: React.CSSProperties }) => {
      const isWhite = pieceCode.startsWith("w");
      return <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100" width="100%" height="100%" style={props?.svgStyle}>
        <text x="50" y="79" textAnchor="middle" fontFamily="'Segoe UI Symbol', 'Noto Sans Symbols 2', serif" fontSize="82" fill={isWhite ? "#ffffff" : "#111318"} stroke={isWhite ? "#111318" : "#ffffff"} strokeWidth={isWhite ? "2.4" : "1.8"} paintOrder="stroke">{glyph}</text>
      </svg>;
    }
  ])) as PieceRenderObject
};

type GameBoardProps = {
  gameId: string | undefined;
  boardTheme?: BoardTheme;
  chessPosition: string;
  gameState: GameState;
  onGameStateChange: (state: GameState) => void;
  onPositionChange: (fen: string) => void;
  onSessionNotFound: () => void;
};

type MoveRequest = {
  from: string;
  to: string;
  promotion?: 'q' | 'r' | 'b' | 'n',
  checkFen: string;
};

function createMoveOptionStyles(game: Chess, square: Square, theme: BoardThemeConfig): Record<string, React.CSSProperties> {
  const boldIndicator = theme.moveIndicator === "bold";
  const markerFill = "rgba(255, 255, 255, .82)";
  const markerOutline = "rgba(20, 27, 34, .62)";
  const newSquares: Record<string, React.CSSProperties> = {};

  for (const move of game.moves({ square, verbose: true })) {
    const isCapture = Boolean(game.get(move.to));
    newSquares[move.to] = {
      background: boldIndicator
        ? isCapture
          ? `radial-gradient(circle, transparent 0 59%, ${markerFill} 60% 66%, ${markerOutline} 67% 70%, transparent 71%)`
          : `radial-gradient(circle, ${markerFill} 0 14%, ${markerOutline} 15% 20%, transparent 21%)`
        : isCapture
          ? 'radial-gradient(circle, rgba(0,0,0,.1) 85%, transparent 85%)'
          : 'radial-gradient(circle, rgba(0,0,0,.1) 25%, transparent 25%)',
      borderRadius: '50%'
    };
  }

  newSquares[square] = {
    background: boldIndicator ? "rgba(255, 255, 255, .3)" : "rgba(255, 255, 0, 0.4)",
    ...(boldIndicator ? { boxShadow: `inset 0 0 0 2px ${markerOutline}` } : {})
  };
  return newSquares;
}

function GameBoard({ gameId, boardTheme = "modern", chessPosition, gameState, onGameStateChange, onPositionChange, onSessionNotFound }: GameBoardProps) {

  const [currChessPosition, setCurrChessPosition] = useState(chessPosition);
  const boardElement = useRef<HTMLDivElement>(null);
  const feedbackAnimation = useRef<Animation | null>(null);
  useEffect(() => () => feedbackAnimation.current?.cancel(), []);

  function shakePiece(square: string) {
    feedbackAnimation.current?.cancel();
    if (window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) return;
    const element = boardElement.current?.querySelector(`[data-square="${square}"] [data-piece] svg`);
    if (!element?.animate) return;
    feedbackAnimation.current = element.animate(
      [{ transform: 'translateX(0)' }, { transform: 'translateX(-4px)' }, { transform: 'translateX(4px)' }, { transform: 'translateX(-3px)' }, { transform: 'translateX(0)' }],
      { duration: 260, easing: 'ease-in-out' },
    );
  }

  const [moveFrom, setMoveFrom] = useState('');
  const [promotion, setPromotion] = useState<{ from: string; to: string; color: 'w' | 'b' } | null>(null);
  const requestPending = useRef(false);
  const [optionSquares, setOptionSquares] = useState({});
  const chessGameRef = useRef(new Chess(chessPosition));
  const chessGame = chessGameRef.current;

  // Keep the mounted board in sync with loaded games and server corrections.
  // Acknowledgments matching the optimistic position must not restart animation.
  useEffect(() => {
    const board = chessGameRef.current;
    if (board.fen() === chessPosition) return;
    board.load(chessPosition);
    setPromotion(null);
    setCurrChessPosition(chessPosition);
    setMoveFrom('');
    setOptionSquares({});
  }, [chessPosition]);

  useEffect(() => {
    if (!moveFrom) return;
    setOptionSquares(createMoveOptionStyles(chessGame, moveFrom as Square, BOARD_THEMES[boardTheme]));
  }, [boardTheme, chessGame, moveFrom]);

  function getMoveOptions(square: Square) {
    const moves = chessGame.moves({
      square,
      verbose: true
    });

    if (moves.length === 0) {
      if (chessGame.get(square)) shakePiece(square);
      setOptionSquares({});
      return false;
    }

    // set the option squares
    setOptionSquares(createMoveOptionStyles(chessGame, square, BOARD_THEMES[boardTheme]));

    // return true to indicate that there are move options
    return true;

  }

  async function onSquareClick({
    square,
    piece
  }: SquareHandlerArgs) {

    if (gameState.status !== "IN_PROGRESS" || promotion || requestPending.current) {
      return;
    }
    const clickedPiece = chessGame.get(square as Square);
    if (clickedPiece && clickedPiece.color !== chessGame.turn()) {
      const isCapture = moveFrom && chessGame.moves({ square: moveFrom as Square, verbose: true })
        .some(move => move.to === square);
      if (!isCapture) {
        setMoveFrom('');
        setOptionSquares({});
        shakePiece(square);
        return;
      }
    }
    // piece clicked to move
    if (!moveFrom && piece) {
      // get the move options for the square
      const hasMoveOptions = getMoveOptions(square as Square);

      // if move options, set the moveFrom to the square
      if (hasMoveOptions) {
        setMoveFrom(square);
      }

      // return early
      return;
    }

    // square clicked to move to, check if valid move
    const moves = chessGame.moves({
      square: moveFrom as Square,
      verbose: true
    });
    const foundMove = moves.find(m => m.from === moveFrom && m.to === square);

    // not a valid move
    if (!foundMove) {
      // check if clicked on new piece
      const hasMoveOptions = getMoveOptions(square as Square);

      // if new piece, setMoveFrom, otherwise clear moveFrom
      setMoveFrom(hasMoveOptions ? square : '');

      // return early
      return;
    }

    if (foundMove.promotion) {
      setPromotion({ from: moveFrom, to: square, color: chessGame.turn() });
      return;
    }
    await submitMove(moveFrom, square);
  }

  async function submitMove(fromSquare: string, destinationSquare: string, selectedPromotion?: PromotionPiece) {
    if (requestPending.current || gameState.status !== 'IN_PROGRESS') return;
    const beforeMove = chessGame.fen();
    try {
      chessGame.move({ from: fromSquare, to: destinationSquare, ...(selectedPromotion ? { promotion: selectedPromotion } : {}) });
    } catch {
      setPromotion(null);
      return;
    }
    requestPending.current = true;
    setPromotion(null);
    const gameBoardFen = chessGame.fen();

    const moveRequest: MoveRequest = {
      from: fromSquare.toUpperCase(),
      to: destinationSquare.toUpperCase(),
      checkFen: gameBoardFen
    };

    if (selectedPromotion) moveRequest.promotion = selectedPromotion;

    feedbackAnimation.current?.cancel();
    setCurrChessPosition(gameBoardFen);
    // clear moveFrom and optionSquares
    setMoveFrom('');
    setOptionSquares({});

    try {
      const response = await fetch(apiUrl(`/games/${gameId}/move`), {
        method: "POST",
        headers: {
          "Content-Type": "application/json"
        },
        body: JSON.stringify(moveRequest)
      });

      if (response.status === 404) {
        onSessionNotFound();
        return;
      }

      const data: MoveResponse = await response.json();

      if (!response.ok) {
        console.error("Fail to apply move:");
        chessGame.load(data.fen);
        setCurrChessPosition(data.fen);
      }

      if (!data.synchronizedBoards) {
        console.error("FrontEnd/BackEnd board mismatch");
        chessGame.load(data.fen);
        setCurrChessPosition(data.fen);
      }
      onPositionChange(data.fen);
      onGameStateChange({
        status: data.gameStatus,
        result: data.gameResult,
        endReason: data.gameEndReason,
        availableDrawClaims: data.availableDrawClaims ?? []
      });
    } catch (error) {
      console.error("Failed to connect to server: ", error);
      chessGame.load(beforeMove);
      setCurrChessPosition(beforeMove);
    } finally {
      requestPending.current = false;
    }
  }

  const kingInCheck = checkedKingSquare(chessGame);
  const checkSquareStyles: Record<string, React.CSSProperties> = kingInCheck
    ? {
        [kingInCheck]: {
          background: 'radial-gradient(circle, rgba(239, 68, 68, 0.92) 0%, rgba(153, 27, 27, 0.82) 100%)',
          boxShadow: 'inset 0 0 0 4px rgba(127, 29, 29, 0.9)'
        }
      }
    : {};

  const chessboardOptions = {
    allowDragging: false,
    animationDurationInMs: 300,
    onSquareClick,
    position: currChessPosition,
    pieces: PIECE_STYLES[BOARD_THEMES[boardTheme].pieceStyle],
    darkSquareStyle: { backgroundColor: BOARD_THEMES[boardTheme].darkSquare },
    lightSquareStyle: { backgroundColor: BOARD_THEMES[boardTheme].lightSquare },
    squareStyles: { ...optionSquares, ...checkSquareStyles },
    id: 'click-to-move'
  };

  return <div ref={boardElement}>
    <Chessboard options={chessboardOptions} />
    {promotion && gameState.status === 'IN_PROGRESS' && <PromotionPicker color={promotion.color} pieces={PIECE_STYLES[BOARD_THEMES[boardTheme].pieceStyle]}
      onChoose={piece => { void submitMove(promotion.from, promotion.to, piece); }}
      onCancel={() => { setPromotion(null); setMoveFrom(''); setOptionSquares({}); }} />}
  </div>;
}

export default GameBoard;

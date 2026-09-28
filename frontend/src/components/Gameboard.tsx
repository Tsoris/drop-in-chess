import PromotionPicker, { type PromotionPiece } from "./PromotionPicker";
import { apiUrl } from "../lib/api";
import { Chess, type Square } from 'chess.js';
import { useEffect, useRef, useState } from 'react';
import { Chessboard, type SquareHandlerArgs } from 'react-chessboard';
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

type GameBoardProps = {
  gameId: string | undefined;
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

function GameBoard({ gameId, chessPosition, gameState, onGameStateChange, onPositionChange, onSessionNotFound }: GameBoardProps) {

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

    const newSquares: Record<string, React.CSSProperties> = {};
    for (const move of moves) {
      newSquares[move.to] = {
        background: chessGame.get(move.to) && chessGame.get(move.to)?.color !== chessGame.get(square)?.color ? 'radial-gradient(circle, rgba(0,0,0,.1) 85%, transparent 85%)' // larger circle for capturing
          : 'radial-gradient(circle, rgba(0,0,0,.1) 25%, transparent 25%)',
        // smaller circle for moving
        borderRadius: '50%'
      };
    }

    // set the square clicked to move from to yellow
    newSquares[square] = {
      background: 'rgba(255, 255, 0, 0.4)'
    };


    // set the option squares
    setOptionSquares(newSquares);

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
    squareStyles: { ...optionSquares, ...checkSquareStyles },
    id: 'click-to-move'
  };

  return <div ref={boardElement}>
    <Chessboard options={chessboardOptions} />
    {promotion && gameState.status === 'IN_PROGRESS' && <PromotionPicker color={promotion.color}
      onChoose={piece => { void submitMove(promotion.from, promotion.to, piece); }}
      onCancel={() => { setPromotion(null); setMoveFrom(''); setOptionSquares({}); }} />}
  </div>;
}

export default GameBoard;


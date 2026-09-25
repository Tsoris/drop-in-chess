import { apiUrl } from "../lib/api";
import { Chess, type Square } from 'chess.js';
import { useRef, useState } from 'react';
import { Chessboard, type SquareHandlerArgs } from 'react-chessboard';
import type { GameState, MoveResponse } from '../types/GameStatus';

type GameBoardProps = {
  gameId: string | undefined;
  chessPosition: string;
  gameState: GameState;
  onGameStateChange: (state: GameState) => void;
};

type MoveRequest = {
  from: string;
  to: string;
  promotion?: 'q' | 'r' | 'b' | 'n',
  checkFen: string;
};

function GameBoard({ gameId, chessPosition, gameState, onGameStateChange }: GameBoardProps) {

  const [currChessPosition, setCurrChessPosition] = useState(chessPosition);
  const [moveFrom, setMoveFrom] = useState('');
  const [optionSquares, setOptionSquares] = useState({});

  const chessGameRef = useRef(new Chess(chessPosition));
  const chessGame = chessGameRef.current;

  function getMoveOptions(square: Square) {
    const moves = chessGame.moves({
      square,
      verbose: true
    });

    if (moves.length === 0) {
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

    if (gameState.status !== "IN_PROGRESS") {
      return;
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

    // is normal move
    try {
      chessGame.move({
        from: moveFrom,
        to: square,
        promotion: 'q'
      });
    } catch {
      // if invalid, setMoveFrom and getMoveOptions
      const hasMoveOptions = getMoveOptions(square as Square);

      // if new piece, setMoveFrom, otherwise clear moveFrom
      if (hasMoveOptions) {
        setMoveFrom(square);
      }

      // return early
      return;
    }

    // update the position state
    const fromSquare = moveFrom;
    const destinationSquare = square;
    const gameBoardFen = chessGame.fen();

    const moveRequest: MoveRequest = {
      from: fromSquare.toUpperCase(),
      to: destinationSquare.toUpperCase(),
      checkFen: gameBoardFen
    };

    if (foundMove.promotion) {
      moveRequest.promotion = 'q';
    };

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
      onGameStateChange({
        status: data.gameStatus,
        result: data.gameResult,
        endReason: data.gameEndReason,
        availableDrawClaims: data.availableDrawClaims ?? []
      });
    } catch (error) {
      console.error("Failed to connect to server: ", error);
    }
  }

  const chessboardOptions = {
    allowDragging: false,
    onSquareClick,
    position: currChessPosition,
    squareStyles: optionSquares,
    id: 'click-to-move'
  };

  return <Chessboard options={chessboardOptions} />;
}

export default GameBoard;


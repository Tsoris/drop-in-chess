import { Chessboard } from 'react-chessboard';

type GameBoardProps = {
    startPosition: string;
};

function GameBoard({startPosition}: GameBoardProps) {


  const chessboardOptions = {
    position: startPosition
  };

  return <Chessboard options={chessboardOptions} />;
}

export default GameBoard;
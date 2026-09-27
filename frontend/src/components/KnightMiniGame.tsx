import { useState } from 'react';
import { coinPoints, knightMoves, newTarget, newKnightGame, squareName } from '../lib/knightGame';
import './KnightMiniGame.css';

export default function KnightMiniGame() {
  const [game, setGame] = useState(newKnightGame);
  const legalMoves = knightMoves(game.knight);

  function move(square: number) {
    setGame(current => {
      if (!knightMoves(current.knight).includes(square)) return current;
      const moves = current.moves + 1;
      if (square === current.coin) {
        return {
          knight: square,
          ...newTarget(square),
          score: current.score + coinPoints(current.targetMoves, moves),
        };
      }
      return { ...current, knight: square, moves };
    });
  }
  return (
    <section className="knight-game" aria-labelledby="knight-title">
      <div className="knight-toolbar">
        <h2 id="knight-title">Knight quest</h2>
        <span role="status" aria-label="Score">Score: {game.score}</span>
      </div>
      <p id="knight-help">Move the knight to the coin. Choose a highlighted square to hop in an L shape.</p>
      <p role="status" aria-label="Current coin value">Current coin: +{coinPoints(game.targetMoves, game.moves)} points</p>
      <div className="knight-board" role="group" aria-label="Knight mini-game board" aria-describedby="knight-help">
        {Array.from({ length: 64 }, (_, square) => {
          const knight = square === game.knight;
          const coin = square === game.coin;
          const legal = legalMoves.includes(square);
          return (
            <button
              type="button"
              key={square}
              className={`knight-square ${(Math.floor(square / 8) + square % 8) % 2 ? 'dark' : 'light'}${legal ? ' legal' : ''}`}
              aria-label={`${squareName(square)}${knight ? ', knight' : ''}${coin ? ', coin' : ''}${legal ? ', legal destination' : ''}`}
              aria-disabled={!legal}
              tabIndex={legal ? 0 : -1}
              onClick={() => move(square)}
            >
              {knight && <span className="knight-piece" aria-hidden="true">&#9822;</span>}
              {coin && <span className="knight-coin" aria-hidden="true">&#9679;</span>}
              {legal && !coin && <span className="knight-dot" aria-hidden="true" />}
              <span className="knight-coordinate" aria-hidden="true">{squareName(square)}</span>
            </button>
          );
        })}
      </div>
      <button className="knight-reset" type="button" onClick={() => setGame(newKnightGame())}>Reset</button>
    </section>
  );
}

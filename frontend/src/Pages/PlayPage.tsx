import { apiUrl } from "../lib/api";
import { useNavigate, useParams } from "react-router-dom";
import Gameboard from "../components/Gameboard";
import { useEffect, useState } from "react";
import { AnimatePresence, motion } from "motion/react";
import type { GameResponse, GameState } from "../types/GameStatus";

const INITIAL_GAME_STATE: GameState = {
  status: "IN_PROGRESS",
  result: null,
  endReason: null,
  availableDrawClaims: []
};

function gameResultMessage(gameState: GameState) {
  if (gameState.status !== "COMPLETED") {
    return null;
  }

  if (gameState.result === "WHITE_WINS") {
    return gameState.endReason === "RESIGNATION"
      ? "White wins by resignation."
      : "White wins by checkmate.";
  }

  if (gameState.result === "BLACK_WINS") {
    return gameState.endReason === "RESIGNATION"
      ? "Black wins by resignation."
      : "Black wins by checkmate.";
  }

  const drawMessages = {
    STALEMATE: "Draw by stalemate.",
    INSUFFICIENT_MATERIAL: "Draw by insufficient material.",
    REPETITION: "Draw by repetition.",
    MOVE_RULE: "Draw by move rule.",
    AGREEMENT: "Draw by agreement."
  } as const;

  return gameState.endReason && gameState.endReason in drawMessages
    ? drawMessages[gameState.endReason as keyof typeof drawMessages]
    : "Game completed.";
}
/*
 * Renders an active chess game session.
 *
 * Retrieves the game ID from the URL and loads the game's current FEN
 * from the backend. Also provides controls for copying the session ID
 * and starting a new game.
 */
export const PlayPage = () => {
  const navigate = useNavigate();

  const [chessPosition, setChessPosition] = useState("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
  const { gameId } = useParams();
  const [gameState, setGameState] = useState<GameState>(INITIAL_GAME_STATE);
  const [claimMessage, setClaimMessage] = useState("");
  const [isClaimingDraw, setIsClaimingDraw] = useState(false);

  const [copyMessage, setCopyMessage] = useState("");

  function copyGameId() {
    if (gameId) {
      navigator.clipboard.writeText(gameId);

      setCopyMessage(
        "Copied! Sessions expire after 3 days of inactivity."
      );

      setTimeout(() => {
        setCopyMessage("");
      }, 4000);
    }
  }

  async function handleNewGame() {
    try {
      const response = await fetch(apiUrl("/games"), {
        method: "POST"
      });

      if (!response.ok) {
        console.error("Failed to create game:", response.status);
        return;
      }

      const data = await response.json();

      sessionStorage.setItem("gameId", data.gameId);
      navigate(`/game/${data.gameId}`);

    } catch (error) {
      console.error("Unable to connect to server:", error);
    }
  }

  async function handleDrawClaim() {
    if (!gameId || isClaimingDraw) {
      return;
    }

    setIsClaimingDraw(true);
    setClaimMessage("");

    try {
      const response = await fetch(
        apiUrl(`/games/${gameId}/draw-claim`),
        { method: "POST" }
      );
      const data: GameResponse = await response.json();

      setChessPosition(data.fen);
      setGameState({
        status: data.status,
        result: data.result,
        endReason: data.endReason,
        availableDrawClaims: data.availableDrawClaims ?? []
      });

      if (!response.ok) {
        setClaimMessage(
          response.status === 409
            ? "A draw can no longer be claimed in this position."
            : "Unable to claim a draw."
        );
      }
    } catch (error) {
      console.error("Unable to claim draw:", error);
      setClaimMessage("Unable to connect to the server.");
    } finally {
      setIsClaimingDraw(false);
    }
  }

  // Load the authoritative game state whenever the game ID in the URL changes.
  useEffect(() => {
    fetch(apiUrl(`/games/${gameId}`))
      .then(response => {
        if (!response.ok) {
          throw new Error(
            `Failed to load game: ${response.status}`
          );
        }
        return response.json() as Promise<GameResponse>;
      })
      .then(data => {
        setChessPosition(data.fen);
        setGameState({
          status: data.status,
          result: data.result,
          endReason: data.endReason,
          availableDrawClaims: data.availableDrawClaims ?? []
        });
        setClaimMessage("");
      })
      .catch(error => {
        console.error("Unable to fetch game:", error);
      });
  }, [gameId]);

  return (
    <main className="web-page">

      <h2></h2>
      <p>Good luck and have fun!</p>

      <div className="session-controls">
        <button onClick={copyGameId}>
          Copy Session ID
        </button>

        <div className="session-message">
          <AnimatePresence>
            {copyMessage && (
              <motion.span
                className="session-info"
                initial={{ opacity: 0, y: -5 }}
                animate={{
                  opacity: 1,
                  y: 0,
                  transition: { duration: 0.2 }
                }}
                exit={{
                  opacity: 0,
                  y: -5,
                  transition: { duration: 0.6 }
                }}
              >
                {copyMessage}
              </motion.span>
            )}
          </AnimatePresence>
        </div>
      </div>

      <Gameboard
        key={`${gameId}:${chessPosition}`}
        gameId={gameId}
        chessPosition={chessPosition}
        gameState={gameState}
        onGameStateChange={setGameState}
      />

      {gameState.status === "IN_PROGRESS" &&
        gameState.availableDrawClaims.length > 0 && (
        <div className="draw-claim">
          <p>
            {gameState.availableDrawClaims.includes("REPETITION")
              ? "A draw by repetition is available."
              : "A draw under the 50-move rule is available."}
          </p>
          <button onClick={handleDrawClaim} disabled={isClaimingDraw}>
            {isClaimingDraw ? "Claiming..." : "Claim Draw"}
          </button>
        </div>
      )}

      {claimMessage && <p className="game-message">{claimMessage}</p>}

      {gameState.status === "COMPLETED" && (
        <div className="game-result">
          <p>{gameResultMessage(gameState)}</p>
        </div>
      )}


      <button
        onClick={handleNewGame}>New Game
      </button>

    </main>
  )
};

export default PlayPage;


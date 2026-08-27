import { useNavigate, useParams } from "react-router-dom";
import Gameboard from "../components/Gameboard";
import { useEffect, useState } from "react";
import { AnimatePresence, motion } from "motion/react";

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
      const response = await fetch("http://localhost:8080/games", {
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

  // Load the authoritative game state whenever the game ID in the URL changes.
  useEffect(() => {
    fetch(`http://localhost:8080/games/${gameId}`)
      .then(response => {
        if (!response.ok) {
          throw new Error(
            `Failed to load game: ${response.status}`
          );
        }
        return response.json();
      })
      .then(data => {
        setChessPosition(data.fen);
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
        gameId={gameId}
        chessPosition={chessPosition}
      />

      <button
        onClick={handleNewGame}>New Game
      </button>

    </main>
  )
};

export default PlayPage;
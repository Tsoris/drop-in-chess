import { useParams } from "react-router-dom";
import Gameboard from "../components/Gameboard";
import { useEffect, useState } from "react";

/**
 * Renders an active chess game session.
 *
 * Retrieves the game ID from the URL and loads the game's current FEN
 * from the backend. Also provides controls for copying the session ID
 * and clearing the locally stored session.
 */
export const PlayPage = () => {
  const [startPosition, setStartPosition] = useState("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
  const { gameId } = useParams();
  console.log(gameId);

  function copyGameId() {
    if (gameId) {
      navigator.clipboard.writeText(gameId);
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
        setStartPosition(data.fen);
      })
      .catch(error => {
        console.error("Unable to fetch game:", error);
      });
  }, [gameId]);

  console.log(startPosition);

  return (
    <main className="play-page">
      <p>Good luck and have fun</p>
      <button onClick={copyGameId}>
        Copy Session ID
      </button>
      <Gameboard startPosition={startPosition} />
      <button
        onClick={() => sessionStorage.removeItem("gameId")}>Clear Game Session
      </button>


    </main>
  )
};

export default PlayPage;
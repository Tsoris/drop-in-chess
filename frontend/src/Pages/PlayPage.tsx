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

type PositionDetails = Pick<GameResponse, "positionId" | "phase" | "context" | "source">;

function gameResultMessage(gameState: GameState) {
  if (gameState.status !== "COMPLETED") return null;
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

function stateFromResponse(data: GameResponse): GameState {
  return {
    status: data.status,
    result: data.result,
    endReason: data.endReason,
    availableDrawClaims: data.availableDrawClaims ?? []
  };
}

export const PlayPage = () => {
  const navigate = useNavigate();
  const { gameId } = useParams();
  const [chessPosition, setChessPosition] = useState(
    "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
  );
  const [gameState, setGameState] = useState<GameState>(INITIAL_GAME_STATE);
  const [positionDetails, setPositionDetails] = useState<PositionDetails | null>(null);
  const [claimMessage, setClaimMessage] = useState("");
  const [isClaimingDraw, setIsClaimingDraw] = useState(false);
  const [copyMessage, setCopyMessage] = useState("");
  const [fenCopyMessage, setFenCopyMessage] = useState("");

  const sideToMove = chessPosition.split(" ")[1] === "b" ? "Black" : "White";

  function showTemporaryMessage(setter: (message: string) => void, message: string) {
    setter(message);
    window.setTimeout(() => setter(""), 3000);
  }

  function copyGameId() {
    if (!gameId) return;
    void navigator.clipboard.writeText(gameId);
    showTemporaryMessage(setCopyMessage, "Session ID copied");
  }

  function copyFen() {
    void navigator.clipboard.writeText(chessPosition);
    showTemporaryMessage(setFenCopyMessage, "FEN copied");
  }

  async function handleNewGame() {
    try {
      const response = await fetch(apiUrl("/games"), { method: "POST" });
      if (!response.ok) throw new Error(`Failed to create game: ${response.status}`);
      const data: GameResponse = await response.json();
      sessionStorage.setItem("gameId", data.gameId);
      navigate(`/game/${data.gameId}`);
    } catch (error) {
      console.error("Unable to create game:", error);
    }
  }

  async function handleDrawClaim() {
    if (!gameId || isClaimingDraw) return;
    setIsClaimingDraw(true);
    setClaimMessage("");
    try {
      const response = await fetch(apiUrl(`/games/${gameId}/draw-claim`), { method: "POST" });
      const data: GameResponse = await response.json();
      setChessPosition(data.fen);
      setGameState(stateFromResponse(data));
      if (!response.ok) {
        setClaimMessage(response.status === 409
          ? "A draw can no longer be claimed in this position."
          : "Unable to claim a draw.");
      }
    } catch (error) {
      console.error("Unable to claim draw:", error);
      setClaimMessage("Unable to connect to the server.");
    } finally {
      setIsClaimingDraw(false);
    }
  }

  useEffect(() => {
    if (!gameId) return;
    fetch(apiUrl(`/games/${gameId}`))
      .then(response => {
        if (!response.ok) throw new Error(`Failed to load game: ${response.status}`);
        return response.json() as Promise<GameResponse>;
      })
      .then(data => {
        setChessPosition(data.fen);
        setGameState(stateFromResponse(data));
        setPositionDetails({
          positionId: data.positionId,
          phase: data.phase,
          context: data.context,
          source: data.source
        });
        setClaimMessage("");
      })
      .catch(error => console.error("Unable to fetch game:", error));
  }, [gameId]);

  const context = positionDetails?.context;
  const descriptionsAvailable = context?.availability === "AVAILABLE";
  const positionTitle = positionDetails?.source?.opening
    ?? (positionDetails
      ? `${positionDetails.phase === "MIDDLEGAME" ? "Middlegame" : "Endgame"} position`
      : "Loading position details…");

  return (
    <main className="play-page">
      <section className="play-workspace" aria-label="Chess position workspace">
        <div className="board-section">
          <div className="turn-indicator">
            <span className="turn-dot" aria-hidden="true" />
            <strong>{sideToMove} to move</strong>
            {positionDetails && <span>{positionDetails.phase === "MIDDLEGAME" ? "Middlegame" : "Endgame"}</span>}
          </div>
          <div className="board-stage">
            <Gameboard
              key={`${gameId}:${chessPosition}`}
              gameId={gameId}
              chessPosition={chessPosition}
              gameState={gameState}
              onGameStateChange={setGameState}
              onPositionChange={setChessPosition}
            />
          </div>
        </div>

        <div className="position-console">
          <div className="position-heading">
            <div>
              <p className="position-kicker">
                {positionDetails?.phase === "ENDGAME" ? "Curated endgame position" : "Curated middlegame position"}
              </p>
              <h2>{positionTitle}</h2>
            </div>
            {descriptionsAvailable && <span className="verified-badge">✓ Feedback Verified</span>}
          </div>

          {positionDetails?.source && (
            <div className="provenance-card">
              <div>
                <span className="provenance-label">Position provenance</span>
                <strong>ECO {positionDetails.source.eco}</strong>
                <span>{positionDetails.source.variation || positionDetails.source.opening}</span>
              </div>
              <a href={positionDetails.source.gameUrl} target="_blank" rel="noreferrer">
                View source game ↗
              </a>
            </div>
          )}

          {descriptionsAvailable ? (
            <div className="position-help" aria-label="Optional position guidance">
              <details className="help-card">
                <summary><span>01</span> How this opening got here <b>+</b></summary>
                <p>{context.openingContext?.summary}</p>
              </details>
              <details className="help-card">
                <summary><span>02</span> Current position briefing <b>+</b></summary>
                <p>{context.positionGuide?.summary}</p>
                <div className="theme-list">
                  {context.positionGuide?.themes.map(theme => <span key={theme}>{theme}</span>)}
                </div>
              </details>
              <details className="help-card">
                <summary><span>03</span> Possible plans <b>+</b></summary>
                <div className="plan-grid">
                  <div><strong>White</strong><p>{context.possiblePlans?.white.summary}</p></div>
                  <div><strong>Black</strong><p>{context.possiblePlans?.black.summary}</p></div>
                </div>
              </details>
            </div>
          ) : context ? (
            <div className="guidance-unavailable">
              <span>Position guidance unavailable</span>
              <p>{context.message}</p>
            </div>
          ) : null}

          <div className="fen-bar">
            <code>{chessPosition}</code>
            <button type="button" onClick={copyFen}>{fenCopyMessage || "Copy FEN"}</button>
          </div>

          {gameState.status === "IN_PROGRESS" && gameState.availableDrawClaims.length > 0 && (
            <div className="draw-claim">
              <p>{gameState.availableDrawClaims.includes("REPETITION")
                ? "A draw by repetition is available."
                : "A draw under the 50-move rule is available."}</p>
              <button onClick={handleDrawClaim} disabled={isClaimingDraw}>
                {isClaimingDraw ? "Claiming…" : "Claim Draw"}
              </button>
            </div>
          )}

          {claimMessage && <p className="game-message">{claimMessage}</p>}
          {gameState.status === "COMPLETED" && (
            <div className="game-result"><p>{gameResultMessage(gameState)}</p></div>
          )}

          <div className="game-actions">
            <button type="button" className="secondary-action" onClick={copyGameId}>Copy session ID</button>
            <button type="button" className="primary-action" onClick={handleNewGame}>New position</button>
          </div>
          <div className="session-message" aria-live="polite">
            <AnimatePresence>
              {copyMessage && (
                <motion.span
                  className="session-info"
                  initial={{ opacity: 0, y: -4 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, y: -4 }}
                >{copyMessage}</motion.span>
              )}
            </AnimatePresence>
          </div>
        </div>
      </section>
    </main>
  );
};

export default PlayPage;

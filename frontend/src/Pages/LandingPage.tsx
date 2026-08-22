import { useState } from "react";
import { useNavigate } from "react-router-dom";

/**
 * Renders the landing page and manages entry into a game session.
 *
 * Allows the user to resume the current browser session, create a new game,
 * or restore an existing game using a session ID.
 */
export const LandingPage = () => {
    const navigate = useNavigate();


    /*
    * Resumes the current game session if one exists in sessionStorage.
    * Otherwise, requests a new game from the backend, stores the returned
    * game ID, and navigates to the game page.
    */
    async function handlePlayNow() {
        const existingGameId = sessionStorage.getItem("gameId");
        if (existingGameId) {
            navigate(`/game/${existingGameId}`);
            return;
        }

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

    const [restoreGameId, setRestoreGameId] = useState("");
    const [restoreError, setRestoreError] = useState("");

    /*
     * Attempts to restore an existing game using the entered session ID.
     *
     * Verifies that the game exists on the backend before storing the session ID
     * and navigating to the game page. Displays an error if the session is invalid,
     * expired, or the backend cannot be reached.
     */
    async function restoreGame() {
        const gameId = restoreGameId.trim();
        if (!gameId) {
            setRestoreError("Enter a Session ID.");
            return;
        }

        try {
            const response = await fetch(
                `http://localhost:8080/games/${gameId}`
            );
            if (!response.ok) {
                setRestoreError("Invalid or expired Session ID.");
                return;
            }

            sessionStorage.setItem("gameId", gameId);
            setRestoreError("");

            navigate(`/game/${gameId}`);
        } catch (error) {
            console.error("Unable to restore game:", error);
            setRestoreError("Unable to connect to the server.");
        }
    }

    return (
        <main className="landing-page">
            <h2>Welcome to Drop in Chess</h2>

            <p>Let's jump into an interesting position</p>

            <button onClick={handlePlayNow}>Play Now</button>

            <div className="restore-section">
                <p>Have an existing game?</p>

                <input
                    type="text"
                    placeholder="Enter Session ID"
                    value={restoreGameId}
                    onChange={(event) => setRestoreGameId(event.target.value)}
                />

                <button onClick={restoreGame}>Restore Game</button>

                {restoreError && (
                    <p className="restore-error">
                        {restoreError}
                    </p>
                )}

            </div>
        </main>
    );
};

export default LandingPage;
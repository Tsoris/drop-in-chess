import { apiUrl } from "../lib/api";
import { AnimatePresence, motion } from "motion/react";
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
            const response = await fetch(
                apiUrl(`/games/${existingGameId}`)
            );

            if (response.ok) {
                navigate(`/game/${existingGameId}`);
                return;
            }

            sessionStorage.removeItem("gameId");
        }

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
            showRestoreError("Enter a Session ID.");
            return;
        }

        try {
            const response = await fetch(
                apiUrl(`/games/${gameId}`)
            );
            if (!response.ok) {
                showRestoreError("Invalid or expired Session ID.");
                return;
            }

            sessionStorage.setItem("gameId", gameId);
            setRestoreError("");

            navigate(`/game/${gameId}`);
        } catch (error) {
            console.error("Unable to restore game:", error);
            showRestoreError("Unable to connect to the server.");
        }
    }

    function showRestoreError(message: string) {
        setRestoreError(message);

        setTimeout(() => {
            setRestoreError("");
        }, 3000);
    }

    return (
        <main className="web-page">
            <h2>Welcome to Drop in Chess</h2>

            <p>Let's jump into an interesting position</p>

            <button onClick={handlePlayNow}>Play Now</button>

            <div className="restore-section">
                <p>Have an existing game?</p>

                <input
                    type="text"
                    placeholder="Enter session ID"
                    value={restoreGameId}
                    onChange={(event) => setRestoreGameId(event.target.value)}
                />

                <button onClick={restoreGame}>Restore Game</button>

                <AnimatePresence>
                    {restoreError && (
                        <motion.p
                            className="restore-error"
                            initial={{ opacity: 0, y: -5 }}
                            animate={{
                                opacity: 1,
                                y: 0,
                                transition: { duration: 0.5 }
                            }}
                            exit={{
                                opacity: 0,
                                y: -5,
                                transition: { duration: 0.8 }
                            }}
                        >
                            {restoreError}
                        </motion.p>
                    )}
                </AnimatePresence>

            </div>
        </main>
    );
};

export default LandingPage;

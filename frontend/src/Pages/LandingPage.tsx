import { apiUrl } from "../lib/api";
import "./LandingPage.css";
import { useState } from "react";
import { useNavigate } from "react-router-dom";

/**
 * Renders the landing page and manages entry into a game session.
 *
 * Allows the user to resume the current browser session, create a new game,
 * or restore an existing game using a session ID.
 */
export const LandingPage = ({ onPlayKnightQuest }: { onPlayKnightQuest?: () => void }) => {
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
        <section className="landing-page" aria-label="Welcome to Drop in Chess">
            <div className="landing-hero">
                <div className="landing-copy">
                    <p className="landing-eyebrow"><span /> STRAIGHT TO THE POSITION </p>
                    <h2>Skip the opening.<br /><em>Find your next move.</em></h2>
                    <p className="landing-lead">Drop into a position from a real game. Explore the possibilities, follow your instincts, and play it through.</p>
                    <button className="landing-primary" onClick={handlePlayNow}>Play Locally <span aria-hidden="true">&#8599;</span></button>
                    <p className="landing-note">Curated positions. Both sides in your hands.</p>
                </div>
                <div className="landing-art" aria-hidden="true">
                    <div className="landing-board-caption"><span>BEYOND THE OPENING</span><span>EXPLORE</span></div>
                    <div className="landing-mini-board">
                        {Array.from({ length: 64 }, (_, square) => {
                            const pieces: Record<number, string> = { 6: '\u265A', 13: '\u265F', 14: '\u265F', 15: '\u265F', 19: '\u265E', 26: '\u265F', 28: '\u2659', 35: '\u2659', 42: '\u2658', 45: '\u2659', 53: '\u2659', 54: '\u2659', 62: '\u2654' };
                            return <span key={square} className={`${(Math.floor(square / 8) + square % 8) % 2 ? 'shade' : ''} ${square === 42 ? 'featured' : ''}`}>{pieces[square]}</span>;
                        })}
                    </div>
                    <div className="landing-art-footer"><span>Less theory.</span><strong>More discovery.</strong></div>
                </div>
            </div>

            <div className="landing-secondary">
                <section className="landing-card" aria-labelledby="restore-heading">
                    <span className="landing-card-number">PICK UP WHERE YOU LEFT OFF</span>
                    <h3 id="restore-heading">Back to your game.</h3>
                    <p>Have a session ID? Bring your position back to the board.</p>
                    <form className="landing-restore" onSubmit={event => { event.preventDefault(); void restoreGame(); }}>
                        <label className="landing-sr-only" htmlFor="restore-session">Session ID</label>
                        <input id="restore-session" type="text" placeholder="Enter session ID" value={restoreGameId}
                            onChange={event => setRestoreGameId(event.target.value)} aria-describedby={restoreError ? 'restore-error' : undefined} />
                        <button type="submit">Restore Game <span aria-hidden="true">&#8594;</span></button>
                    </form>
                    {restoreError && <p id="restore-error" className="restore-error" role="alert">{restoreError}</p>}
                </section>
                <section className="landing-card landing-quest" aria-labelledby="quest-heading">
                    <span className="landing-card-number">TAKE A QUICK DETOUR</span>
                    <h3 id="quest-heading">One knight. Endless routes.</h3>
                    <p>Like Knight Quest? Chase the next coin and make every move count.</p>
                    <button className="landing-quest-button" onClick={onPlayKnightQuest}>Play Here <span aria-hidden="true">&#8594;</span></button>
                </section>
            </div>
        </section>
    );
};

export default LandingPage;
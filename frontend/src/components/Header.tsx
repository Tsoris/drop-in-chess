import { useLayoutEffect, useState } from "react";
import { Link } from "react-router-dom";

type Theme = "light" | "dark";

function initialTheme(): Theme {
    try {
        const saved = localStorage.getItem("drop-in-chess-theme");
        if (saved === "light" || saved === "dark") return saved;
    } catch { /* Theme remains usable when storage is unavailable. */ }
    return window.matchMedia?.("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}

function Header() {
    const [theme, setTheme] = useState<Theme>(initialTheme);
    useLayoutEffect(() => {
        document.documentElement.dataset.theme = theme;
    }, [theme]);

    function toggleTheme() {
        const next = theme === "dark" ? "light" : "dark";
        setTheme(next);
        try { localStorage.setItem("drop-in-chess-theme", next); } catch { /* Optional persistence. */ }
    }

    return (
        <header className="site-header">
            <h1 className="site-title"><Link to="/" aria-label="Drop in Chess home"><span className="header-mark" aria-hidden="true">&#9822;</span><span>Drop in Chess</span></Link></h1>
            <div className="theme-controls">
                <button className="theme-toggle" type="button" onClick={toggleTheme}
                    aria-label={`Switch to ${theme === "dark" ? "light" : "dark"} mode`}>
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.5} aria-hidden="true">
                        {/* Heroicons outline icons, matching the portfolio. MIT license: licenses/heroicons.txt */}
                        <path strokeLinecap="round" strokeLinejoin="round" d={theme === "dark"
                            ? "M12 3v2.25m6.364.386-1.591 1.591M21 12h-2.25m-.386 6.364-1.591-1.591M12 18.75V21m-4.773-4.227-1.591 1.591M5.25 12H3m4.227-4.773L5.636 5.636M15.75 12a3.75 3.75 0 1 1-7.5 0 3.75 3.75 0 0 1 7.5 0Z"
                            : "M21.752 15.002A9.72 9.72 0 0 1 18 15.75c-5.385 0-9.75-4.365-9.75-9.75 0-1.33.266-2.597.748-3.752A9.753 9.753 0 0 0 3 11.25C3 16.635 7.365 21 12.75 21a9.753 9.753 0 0 0 9.002-5.998Z"} />
                    </svg>
                </button>
            </div>

        </header>
    );
}

export default Header;

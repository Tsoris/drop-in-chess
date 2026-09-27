import BackendHealthStatus from "./components/BackendHealthStatus";
import './App.css';
import { BrowserRouter, Navigate, Route, Routes, useNavigate } from "react-router-dom";
import LandingPage from "./Pages/LandingPage";
import PlayPage from "./Pages/PlayPage";
import Footer from "./components/Footer";
import { useState } from "react";
import Header from './components/Header';

export function GameRoutes() {
    const navigate = useNavigate();
    const [showKnightQuest, setShowKnightQuest] = useState(false);

    return (
        <BackendHealthStatus onContinue={() => { setShowKnightQuest(false); navigate("/", { replace: true }); }} showKnightQuest={showKnightQuest}>
            <Routes>
                <Route path="/" element={<LandingPage onPlayKnightQuest={() => setShowKnightQuest(true)}/>}/>
                <Route path="/knight-game" element={<Navigate to="/" replace />}/>
                <Route path="/game/:gameId" element={<PlayPage/>}/>
            </Routes>
        </BackendHealthStatus>
    );
}

function App() {
    return (
        <BrowserRouter>
            <div className='app'>
                <Header/>
                <main className='main-content'>
                    <GameRoutes />
                </main>
            </div>
            <Footer />
        </BrowserRouter>
    );
}

export default App;

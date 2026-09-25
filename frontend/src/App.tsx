import BackendHealthStatus from "./components/BackendHealthStatus";
import './App.css';
import { BrowserRouter, Route, Routes, useLocation } from "react-router-dom";
import LandingPage from "./Pages/LandingPage";
import PlayPage from "./Pages/PlayPage";
import Footer from "./components/Footer";
import Header from './components/Header';

function GameRoutes() {
    const location = useLocation();
    return (
        <BackendHealthStatus key={location.pathname}>
            <Routes>
                <Route path="/" element={<LandingPage/>}/>
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


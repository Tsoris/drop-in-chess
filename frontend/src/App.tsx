import './App.css';
import { BrowserRouter, Route, Routes } from "react-router-dom";
import LandingPage from "./Pages/LandingPage";
import PlayPage from "./Pages/PlayPage";
import Footer from "./components/Footer";
import Header from './components/Header';

function App() {
    return (
        <BrowserRouter>
            <div className='app'>
                <Header/>
                <main className='main-content'>
                    <Routes>
                        <Route path="/" element={<LandingPage/>}/>
                        <Route path="/game/:gameId" element={<PlayPage/>}/>
                    </Routes>
                </main>
            </div>
            <Footer />
        </BrowserRouter>
    );
}

export default App;

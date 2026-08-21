import { useEffect, useState } from "react";
import Gameboard from "../components/Gameboard";

export const PlayPage = () => {
    const [startPosition, setStartPosition] = useState("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
    
      useEffect(() => {
        fetch("http://localhost:8080/startingFEN")
          .then(response => response.json())
          .then(data => {
            setStartPosition(data.fen);
          })
          .catch(error => {
            console.error("Unable to fetch a starting position:", error);
          });
    
      }, []);

      console.log(startPosition)

    return (
        <main className="play-page">
            <p>Good luck and have fun</p>
            <Gameboard startPosition={startPosition}/>
        </main>
    )
};

export default PlayPage;
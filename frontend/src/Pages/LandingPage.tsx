import { Link } from "react-router-dom";

export const LandingPage = () => {
    return (
        <>
            <h2 style={{ padding: "2rem" }}>Welcome to Drop in Chess</h2>

            <p>Let's jump into an interesting position</p>

            <Link to="/play">
                <button>Play Now</button>
            </Link>
        </>
    );
};

export default LandingPage;
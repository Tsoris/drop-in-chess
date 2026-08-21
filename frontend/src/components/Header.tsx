import { Link } from "react-router-dom";

function Header () {
    return (
        <h1 className="site-title">
            <Link to='/'>Drop in Chess</Link>
        </h1>
    )
}

export default Header;
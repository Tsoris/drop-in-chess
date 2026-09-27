function Footer() {
    return (
        <footer className="site-footer">
            <div className="footer-brand">
                <span className="footer-mark" aria-hidden="true">&#9822;</span>
                <div>
                    <strong>Drop in Chess</strong>
                    <p>More discovery.</p>
                </div>
            </div>
            <div className="footer-note">
                <small>&copy; {new Date().getFullYear()} Drop in Chess</small>
            </div>
        </footer>
    );
}

export default Footer;

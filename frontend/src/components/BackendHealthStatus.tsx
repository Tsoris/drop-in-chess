import { useEffect, useState } from "react";

function BackendHealthStatus() {
    const [status, setStatus] = useState("");

    useEffect(() => {
        fetch("http://localhost:8080/health")
            .then(response => response.json())
            .then(data => {
                setStatus(data.status);
            })
            .catch(error => {
                console.error("Backend request failed:", error);
                setStatus("NOT CONNECTED");
            });
    }, []);

    return (
        <div>
            Backend status: {status}
        </div>
    );
}

export default BackendHealthStatus;
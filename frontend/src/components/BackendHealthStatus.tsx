import { useEffect, useState, type ReactNode } from "react";
import { apiUrl } from "../lib/api";

export default function BackendHealthStatus({ children }: { children: ReactNode }) {
  const [attempt, setAttempt] = useState(0);
  const [status, setStatus] = useState<"connecting" | "waiting" | "ready" | "error">("connecting");
  useEffect(() => {
    let disposed = false;
    let request: AbortController | undefined;
    let retryTimer: ReturnType<typeof setTimeout> | undefined;
    let requestTimer: ReturnType<typeof setTimeout> | undefined;
    const waitTimer = setTimeout(() => setStatus("waiting"), 3000);
    const deadlineTimer = setTimeout(() => {
      disposed = true;
      request?.abort();
      clearTimeout(retryTimer);
      clearTimeout(requestTimer);
      clearTimeout(waitTimer);
      setStatus("error");
    }, 90000);
    async function check() {
      request = new AbortController();
      requestTimer = setTimeout(() => request?.abort(), 10000);
      try {
        const response = await fetch(apiUrl("/health"), { signal: request.signal, cache: "no-store" });
        if (!response.ok) throw new Error("Health check failed");
        const data = await response.json();
        if (data.status !== "CONNECTED") throw new Error("Backend not ready");
        if (disposed) return;
        clearTimeout(waitTimer);
        clearTimeout(deadlineTimer);
        setStatus("ready");
      } catch {
        if (!disposed) retryTimer = setTimeout(check, 2000);
      } finally { clearTimeout(requestTimer); }
    }
    void check();
    return () => {
      disposed = true;
      request?.abort();
      clearTimeout(waitTimer);
      clearTimeout(deadlineTimer);
      clearTimeout(retryTimer);
      clearTimeout(requestTimer);
    };
  }, [attempt]);
  if (status === "ready") return children;
  return (
    <section className="backend-startup" aria-busy={status !== "error"}>
      <h2>{status === "error" ? "Unable to connect" : "Getting your game ready"}</h2>
      <p role={status === "error" ? "alert" : "status"}>
        {status === "connecting" && "Connecting to the chess server..."}
        {status === "waiting" && "The server may be waking up. This can take about a minute. We'll continue automatically when it's ready."}
        {status === "error" && "The server hasn't responded yet. Please check your connection and try again."}
      </p>
      {status === "error" && <button onClick={() => { setStatus("connecting"); setAttempt(value => value + 1); }}>Try again</button>}
    </section>
  );
}

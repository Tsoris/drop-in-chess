import { useEffect, useState, type ReactNode } from "react";
import KnightMiniGame from "./KnightMiniGame";
import { apiUrl } from "../lib/api";

export default function BackendHealthStatus({ children, onContinue, showKnightQuest = false }: { children: ReactNode; onContinue?: () => void; showKnightQuest?: boolean }) {
  const [continued, setContinued] = useState(false);
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
  const visible = showKnightQuest || status !== "ready" || !continued;
  return (
    <>
    <section className="backend-startup" hidden={!visible}>
      <h2>{status === "error" ? "Unable to connect" : "A little chess while you wait"}</h2>
      <p role={status === "error" ? "alert" : "status"}>
        {status === "connecting" && "Starting Drop in Chess server..."}
        {status === "waiting" && "Waiting for the Drop in Chess server..."}
        {status === "error" && "The server hasn't responded yet. Please check your connection and try again."}
        {status === "ready" && "Server ready! You can return to Knight Quest from the main page."}
      </p>
      {status === "ready" && <button className="startup-continue" onClick={() => { onContinue?.(); setContinued(true); }}>Continue to Drop in Chess</button>}
      <KnightMiniGame />
      {status === "error" && <button onClick={() => { setStatus("connecting"); setAttempt(value => value + 1); }}>Try again</button>}
    </section>
    {!visible && children}
    </>
  );
}

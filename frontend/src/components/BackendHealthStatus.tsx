import { useEffect, useState, type ReactNode } from "react";
import KnightMiniGame from "./KnightMiniGame";
import { apiUrl } from "../lib/api";

type HealthStatus = "connecting" | "waiting" | "ready" | "error";

const HEALTH_POLL_MS = 10_000;
const RETRY_MS = 2_000;
const WAITING_MESSAGE_MS = 3_000;
const REQUEST_TIMEOUT_MS = 10_000;
const OUTAGE_TIMEOUT_MS = 90_000;

export default function BackendHealthStatus({
  children,
  onContinue,
  showKnightQuest = false
}: {
  children: ReactNode;
  onContinue?: () => void;
  showKnightQuest?: boolean;
}) {
  const [continued, setContinued] = useState(() => {
    try {
      return sessionStorage.getItem("drop-in-chess-startup-dismissed") === "true";
    } catch {
      return false;
    }
  });
  const [attempt, setAttempt] = useState(0);
  const [status, setStatus] = useState<HealthStatus>("connecting");
  const [recoveryPending, setRecoveryPending] = useState(false);

  useEffect(() => {
    let disposed = false;
    let outageActive = false;
    let outageTimedOut = false;
    let request: AbortController | undefined;
    let nextCheckTimer: ReturnType<typeof setTimeout> | undefined;
    let requestTimer: ReturnType<typeof setTimeout> | undefined;
    let waitingTimer: ReturnType<typeof setTimeout> | undefined;
    let outageTimer: ReturnType<typeof setTimeout> | undefined;

    function clearOutageTimers() {
      clearTimeout(waitingTimer);
      clearTimeout(outageTimer);
    }

    function beginOutage(immediate: boolean) {
      if (outageActive) return;
      outageActive = true;
      outageTimedOut = false;
      if (immediate) {
        setRecoveryPending(true);
        setStatus("waiting");
      } else {
        waitingTimer = setTimeout(() => {
          if (!disposed && !outageTimedOut) {
            setRecoveryPending(true);
            setStatus("waiting");
          }
        }, WAITING_MESSAGE_MS);
      }
      outageTimer = setTimeout(() => {
        if (disposed) return;
        outageTimedOut = true;
        request?.abort();
        clearTimeout(nextCheckTimer);
        setRecoveryPending(true);
        setStatus("error");
      }, OUTAGE_TIMEOUT_MS);
    }

    function scheduleCheck(delay: number) {
      clearTimeout(nextCheckTimer);
      nextCheckTimer = setTimeout(() => void check(), delay);
    }

    async function check() {
      request = new AbortController();
      requestTimer = setTimeout(() => request?.abort(), REQUEST_TIMEOUT_MS);
      try {
        const response = await fetch(apiUrl("/health"), {
          signal: request.signal,
          cache: "no-store"
        });
        if (!response.ok) throw new Error("Health check failed");
        const data = await response.json();
        if (data.status !== "CONNECTED") throw new Error("Backend not ready");
        if (disposed) return;

        outageActive = false;
        outageTimedOut = false;
        clearOutageTimers();
        setStatus("ready");
        scheduleCheck(HEALTH_POLL_MS);
      } catch {
        if (disposed || outageTimedOut) return;
        beginOutage(!outageActive);
        scheduleCheck(RETRY_MS);
      } finally {
        clearTimeout(requestTimer);
      }
    }

    beginOutage(false);
    void check();

    return () => {
      disposed = true;
      request?.abort();
      clearTimeout(nextCheckTimer);
      clearTimeout(requestTimer);
      clearOutageTimers();
    };
  }, [attempt]);

  const showStartup = showKnightQuest || recoveryPending || !continued || status === "waiting" || status === "error";

  function continueToApp() {
    try {
      sessionStorage.setItem("drop-in-chess-startup-dismissed", "true");
    } catch {
      // Storage is optional.
    }
    onContinue?.();
    setRecoveryPending(false);
    setContinued(true);
  }

  function retry() {
    setStatus("waiting");
    setAttempt(value => value + 1);
  }

  return (
    <>
      <section className="backend-startup" hidden={!showStartup}>
        <h2>{status === "error" ? "Unable to connect" : "A little chess while you wait"}</h2>
        <p role={status === "error" ? "alert" : "status"}>
          {status === "connecting" && "Starting Drop in Chess server..."}
          {status === "waiting" && "Waiting for the Drop in Chess server..."}
          {status === "error" && "The server hasn't responded yet. Please check your connection and try again."}
          {status === "ready" && "Server ready. Continue when you're ready."}
        </p>
        {status === "ready" && (
          <button className="startup-continue" onClick={continueToApp}>
            Continue to Drop in Chess
          </button>
        )}
        <KnightMiniGame />
        {status === "error" && <button onClick={retry}>Try again</button>}
      </section>

      {!showStartup && (
        status === "ready" ? children : (
          <section className="backend-startup">
            <p role="status">Connecting to Drop in Chess...</p>
          </section>
        )
      )}
    </>
  );
}

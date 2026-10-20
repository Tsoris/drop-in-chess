import { useEffect, useState, type ReactNode } from "react";
import KnightMiniGame from "./KnightMiniGame";
import { apiUrl } from "../lib/api";
import "./BackendHealthStatus.css";

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
  const [statusHidden, setStatusHidden] = useState(false);
  const [status, setStatus] = useState<HealthStatus>("connecting");
  const [recoveryPending, setRecoveryPending] = useState(false);
  const [remainingSeconds, setRemainingSeconds] = useState(OUTAGE_TIMEOUT_MS / 1000);

  useEffect(() => {
    let disposed = false;
    let outageActive = false;
    let outageTimedOut = false;
    let request: AbortController | undefined;
    let nextCheckTimer: ReturnType<typeof setTimeout> | undefined;
    let requestTimer: ReturnType<typeof setTimeout> | undefined;
    let waitingTimer: ReturnType<typeof setTimeout> | undefined;
    let outageTimer: ReturnType<typeof setTimeout> | undefined;
    let countdownTimer: ReturnType<typeof setInterval> | undefined;

    function clearOutageTimers() {
      clearTimeout(waitingTimer);
      clearTimeout(outageTimer);
      clearInterval(countdownTimer);
    }

    function beginOutage(immediate: boolean) {
      if (outageActive) return;
      outageActive = true;
      outageTimedOut = false;
      const deadline = Date.now() + OUTAGE_TIMEOUT_MS;
      setRemainingSeconds(OUTAGE_TIMEOUT_MS / 1000);
      countdownTimer = setInterval(() => {
        if (!disposed) setRemainingSeconds(Math.max(0, Math.ceil((deadline - Date.now()) / 1000)));
      }, 1000);
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
        clearInterval(countdownTimer);
        setRemainingSeconds(0);
        request?.abort();
        clearTimeout(nextCheckTimer);
        setRecoveryPending(true);
        setStatus("error");
        setStatusHidden(false);
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
  const isConnecting = status === "connecting" || status === "waiting";

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
        <div className="chess-connection-window">
          <div className="chess-connection-titlebar">
            <button type="button" className="chess-connection-toggle" aria-label={statusHidden ? "Show connection status" : "Hide connection status"} title={statusHidden ? "Show connection status" : "Hide connection status"} aria-expanded={!statusHidden} aria-controls="chess-connection-status" onClick={() => setStatusHidden(value => !value)}>
              <span aria-hidden="true">{statusHidden ? "+" : "−"}</span>
            </button>
            <span className="chess-connection-window-title">Connection status</span>
            <span className="chess-connection-dot" data-status={status} role="img" aria-label={isConnecting ? "Connecting" : status === "ready" ? "Connected" : "Disconnected"} title={isConnecting ? "Connecting" : status === "ready" ? "Connected" : "Disconnected"} />
            {statusHidden && status === "ready" && (
              <button type="button" className="chess-connection-quick-continue" onClick={continueToApp} aria-label="Continue to Drop in Chess" title="Continue to Drop in Chess">
                <svg aria-hidden="true" viewBox="0 0 24 24"><path d="M5 12h14m-6-6 6 6-6 6" /></svg>
              </button>
            )}
          </div>
        <div id="chess-connection-status" className="chess-connection-panel" data-status={status} hidden={statusHidden}>
          <div className="chess-connection-heading">
            <h2>
              <svg aria-hidden="true" viewBox="0 0 24 24"><path d="M7 7h10c2 0 3 2 4 9 .3 2-2 3-3 1l-2-2H8l-2 2c-1 2-3.3 1-3-1 1-7 2-9 4-9Z"/><path d="M7 10v4m-2-2h4m6-1h.01m3 2h.01"/></svg>
              {status === "error" ? "Unable to connect" : status === "ready" ? "Ready to play" : "While you wait..."}
            </h2>
            <span className="chess-connection-badge">{isConnecting ? "Connecting" : status === "ready" ? "Connected" : "Disconnected"}</span>
          </div>
          <div className="chess-connection-card">
            <div className="chess-connection-label">
              <svg aria-hidden="true" viewBox="0 0 24 24"><rect x="3" y="3" width="18" height="7" rx="2"/><rect x="3" y="14" width="18" height="7" rx="2"/><path d="M6 6.5h.01M6 17.5h.01"/></svg>
              <strong role="status">{isConnecting ? "Waking up Drop in Chess server" : status === "ready" ? "Server ready" : "Connection timed out"}</strong>
              {isConnecting && remainingSeconds <= 10 && remainingSeconds > 0 && (
                <span className="chess-connection-time" role="timer" aria-label="Time remaining before connection timeout">0:{String(remainingSeconds).padStart(2, "0")}</span>
              )}
            </div>
            {isConnecting && <progress className="chess-connection-progress" max={OUTAGE_TIMEOUT_MS / 1000} value={remainingSeconds} aria-label="Time remaining before connection timeout" />}
            <p role={status === "error" ? "alert" : undefined}>
              {isConnecting && "We'll keep trying to connect while you play."}
              {status === "error" && <>Taking longer than expected. The servers may still be spinning up, or there could be an issue with your ad blocker.<br />Try again, or disable your ad blocker for this site and try again.</>}
              {status === "ready" && <><span>Server ready. Continue when you're ready.</span><br />You can return to Knight Quest anytime from the home page.</>}
            </p>
          </div>
          {status === "error" && <button className="chess-connection-retry" onClick={retry}>Try again</button>}
          {status === "ready" && (
            <button className="startup-continue" onClick={continueToApp}>
              Continue to Drop in Chess
            </button>
          )}
        </div>
        </div>
        <KnightMiniGame />
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

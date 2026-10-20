import { act, cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, expect, test, vi } from "vitest";
import BackendHealthStatus from "./BackendHealthStatus";

afterEach(() => { cleanup(); sessionStorage.clear(); vi.useRealTimers(); vi.restoreAllMocks(); });
const ready = () => Promise.resolve({ ok: true, json: async () => ({ status: "CONNECTED" }) } as Response);

test("minimized status keeps checking and offers a Continue arrow on recovery", async () => {
  vi.useFakeTimers();
  const mock = vi.spyOn(globalThis, "fetch").mockRejectedValue(new Error("offline"));
  render(<BackendHealthStatus>Game ready</BackendHealthStatus>);
  const board = screen.getByRole("group", { name: "Knight mini-game board" });
  fireEvent.click(screen.getByRole("button", { name: "Hide connection status" }));
  expect(screen.queryByRole("progressbar")).not.toBeInTheDocument();
  expect(screen.getByRole("img", { name: "Connecting" })).toBeVisible();
  expect(screen.queryByRole("button", { name: "Continue to Drop in Chess" })).not.toBeInTheDocument();
  expect(screen.getByRole("button", { name: "Show connection status" })).toHaveAttribute("aria-expanded", "false");
  await act(async () => { await vi.advanceTimersByTimeAsync(2000); });
  expect(mock.mock.calls.length).toBeGreaterThan(1);
  fireEvent.click(screen.getByRole("button", { name: "Show connection status" }));
  expect(screen.getByRole("progressbar")).toHaveAttribute("value", "88");
  fireEvent.click(screen.getByRole("button", { name: "Hide connection status" }));
  mock.mockImplementation(ready);
  await act(async () => { await vi.advanceTimersByTimeAsync(2000); });
  expect(screen.getByRole("button", { name: "Continue to Drop in Chess" })).toBeVisible();
  expect(screen.getByRole("img", { name: "Connected" })).toBeVisible();
  expect(screen.getByRole("button", { name: "Show connection status" })).toHaveAttribute("aria-expanded", "false");
  expect(screen.getByRole("button", { name: "Continue to Drop in Chess" })).toHaveAttribute("title", "Continue to Drop in Chess");
  expect(screen.getByRole("group", { name: "Knight mini-game board" })).toBe(board);
  fireEvent.click(screen.getByRole("button", { name: "Continue to Drop in Chess" }));
  expect(screen.getByText("Game ready")).toBeVisible();
});

test("progress bar follows the outage deadline and resets on retry", async () => {
  vi.useFakeTimers();
  const mock = vi.spyOn(globalThis, "fetch").mockRejectedValue(new Error("offline"));
  const view = render(<BackendHealthStatus>Game ready</BackendHealthStatus>);
  expect(screen.getByRole("progressbar")).toHaveAttribute("value", "90");
  await act(async () => { await vi.advanceTimersByTimeAsync(30000); });
  expect(screen.queryByRole("timer")).not.toBeInTheDocument();
  expect(screen.getByRole("progressbar")).toHaveAttribute("value", "60");
  await act(async () => { await vi.advanceTimersByTimeAsync(49000); });
  expect(screen.queryByRole("timer")).not.toBeInTheDocument();
  await act(async () => { await vi.advanceTimersByTimeAsync(1000); });
  expect(screen.getByRole("timer")).toHaveTextContent("0:10");
  await act(async () => { await vi.advanceTimersByTimeAsync(9000); });
  expect(screen.getByRole("timer")).toHaveTextContent("0:01");
  await act(async () => { await vi.advanceTimersByTimeAsync(1000); });
  expect(screen.queryByRole("timer")).not.toBeInTheDocument();
  expect(screen.getByRole("alert")).toHaveTextContent("Taking longer than expected. The servers may still be spinning up, or there could be an issue with your ad blocker.");
  fireEvent.click(screen.getByRole("button", { name: "Hide connection status" }));
  expect(screen.getByRole("img", { name: "Disconnected" })).toBeVisible();
  fireEvent.click(screen.getByRole("button", { name: "Show connection status" }));
  await act(async () => { fireEvent.click(screen.getByRole("button", { name: "Try again" })); });
  expect(screen.getByRole("progressbar")).toHaveAttribute("value", "90");
  mock.mockImplementation(ready);
  await act(async () => { await vi.advanceTimersByTimeAsync(2000); });
  expect(screen.queryByRole("timer")).not.toBeInTheDocument();
  expect(screen.queryByRole("progressbar")).not.toBeInTheDocument();
  expect(screen.getByText("Connected")).toBeInTheDocument();
  view.unmount();
  expect(vi.getTimerCount()).toBe(0);
});

test("holds game UI until health succeeds", async () => {
  vi.spyOn(globalThis, "fetch").mockImplementation(ready);
  render(<BackendHealthStatus><button>Play Now</button></BackendHealthStatus>);
  expect(screen.queryByText("Play Now")).not.toBeInTheDocument();
  const board = screen.getByRole("group", { name: "Knight mini-game board" });
  const proceed = await screen.findByRole("button", { name: "Continue to Drop in Chess" });
  expect(screen.getByRole("group", { name: "Knight mini-game board" })).toBe(board);
  expect(screen.queryByText("Play Now")).not.toBeInTheDocument();
  fireEvent.click(proceed);
  expect(screen.getByText("Play Now")).toBeInTheDocument();
});

test("shows wake-up feedback then continues after a retry", async () => {
  vi.useFakeTimers();
  vi.spyOn(globalThis, "fetch").mockRejectedValueOnce(new Error("sleeping")).mockRejectedValueOnce(new Error("sleeping")).mockImplementation(ready);
  render(<BackendHealthStatus>Game ready</BackendHealthStatus>);
  await act(async () => { await vi.advanceTimersByTimeAsync(3000); });
  expect(screen.getByText("Waking up Drop in Chess server")).toBeInTheDocument();
  await act(async () => { await vi.advanceTimersByTimeAsync(1000); });
  expect(screen.queryByText("Game ready")).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "Continue to Drop in Chess" }));
  expect(screen.getByText("Game ready")).toBeInTheDocument();
});

test("times out even a stuck request and supports retry", async () => {
  vi.useFakeTimers();
  const mock = vi.spyOn(globalThis, "fetch").mockImplementation(() => new Promise(() => {}));
  render(<BackendHealthStatus>Game ready</BackendHealthStatus>);
  await act(async () => { await vi.advanceTimersByTimeAsync(90000); });
  expect(screen.getByRole("alert")).toBeInTheDocument();
  mock.mockImplementation(ready);
  await act(async () => { fireEvent.click(screen.getByRole("button", { name: "Try again" })); });
  expect(screen.queryByText("Game ready")).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "Continue to Drop in Chess" }));
  expect(screen.getByText("Game ready")).toBeInTheDocument();
});

test("unmount cancels requests and retry timers", async () => {
  vi.useFakeTimers();
  const mock = vi.spyOn(globalThis, "fetch").mockRejectedValue(new Error("offline"));
  const view = render(<BackendHealthStatus>Game ready</BackendHealthStatus>);
  await act(async () => { await vi.advanceTimersByTimeAsync(1); });
  const signal = mock.mock.calls[0][1]?.signal;
  view.unmount();
  await vi.advanceTimersByTimeAsync(100000);
  expect(signal?.aborted).toBe(true);
  expect(mock).toHaveBeenCalledTimes(1);
});

test("shows Knight Quest when a dismissed startup check keeps failing", async () => {
  vi.useFakeTimers();
  sessionStorage.setItem("drop-in-chess-startup-dismissed", "true");
  vi.spyOn(globalThis, "fetch").mockImplementation(() => new Promise(() => {}));

  render(<BackendHealthStatus>Game ready</BackendHealthStatus>);

  expect(screen.getByText("Connecting to Drop in Chess...")).toBeInTheDocument();
  expect(screen.queryByRole("group", { name: "Knight mini-game board" })).not.toBeInTheDocument();

  await act(async () => { await vi.advanceTimersByTimeAsync(3000); });

  expect(screen.getByText("Waking up Drop in Chess server")).toBeInTheDocument();
  expect(screen.getByRole("group", { name: "Knight mini-game board" })).toBeInTheDocument();
});

test("detects a later disconnect and waits for Continue after recovery", async () => {
  vi.useFakeTimers();
  sessionStorage.setItem("drop-in-chess-startup-dismissed", "true");
  vi.spyOn(globalThis, "fetch")
    .mockImplementationOnce(ready)
    .mockRejectedValueOnce(new Error("offline"))
    .mockImplementation(ready);

  render(<BackendHealthStatus>Game ready</BackendHealthStatus>);
  await act(async () => {});
  expect(screen.getByText("Game ready")).toBeInTheDocument();

  await act(async () => { await vi.advanceTimersByTimeAsync(10000); });
  expect(screen.getByText("Waking up Drop in Chess server")).toBeInTheDocument();
  expect(screen.getByRole("group", { name: "Knight mini-game board" })).toBeInTheDocument();

  await act(async () => { await vi.advanceTimersByTimeAsync(2000); });
  expect(screen.queryByText("Game ready")).not.toBeInTheDocument();
  expect(screen.getByText("Server ready. Continue when you're ready.")).toBeInTheDocument();
  expect(screen.getByRole("group", { name: "Knight mini-game board" })).toBeInTheDocument();

  fireEvent.click(screen.getByRole("button", { name: "Continue to Drop in Chess" }));
  expect(screen.getByText("Game ready")).toBeInTheDocument();
  expect(screen.queryByRole("group", { name: "Knight mini-game board" })).not.toBeInTheDocument();
});

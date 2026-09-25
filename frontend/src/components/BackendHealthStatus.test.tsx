import { act, cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, expect, test, vi } from "vitest";
import BackendHealthStatus from "./BackendHealthStatus";

afterEach(() => { cleanup(); vi.useRealTimers(); vi.restoreAllMocks(); });
const ready = () => Promise.resolve({ ok: true, json: async () => ({ status: "CONNECTED" }) } as Response);

test("holds game UI until health succeeds", async () => {
  vi.spyOn(globalThis, "fetch").mockImplementation(ready);
  render(<BackendHealthStatus><button>Play Now</button></BackendHealthStatus>);
  expect(screen.queryByText("Play Now")).not.toBeInTheDocument();
  expect(await screen.findByText("Play Now")).toBeInTheDocument();
});

test("shows wake-up feedback then continues after a retry", async () => {
  vi.useFakeTimers();
  vi.spyOn(globalThis, "fetch").mockRejectedValueOnce(new Error("sleeping")).mockRejectedValueOnce(new Error("sleeping")).mockImplementation(ready);
  render(<BackendHealthStatus>Game ready</BackendHealthStatus>);
  await act(async () => { await vi.advanceTimersByTimeAsync(3000); });
  expect(screen.getByRole("status")).toHaveTextContent("may be waking up");
  await act(async () => { await vi.advanceTimersByTimeAsync(1000); });
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

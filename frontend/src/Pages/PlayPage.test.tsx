import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, expect, test, vi } from "vitest";
import PlayPage from "./PlayPage";

vi.mock("../components/Gameboard", () => ({
  default: () => <div data-testid="gameboard" />
}));

afterEach(() => {
  vi.restoreAllMocks();
});

test("claims a draw advertised by the backend", async () => {
  const user = userEvent.setup();
  const fen = "7k/8/8/8/8/8/R7/K7 w - - 100 1";
  const fetchMock = vi
    .spyOn(globalThis, "fetch")
    .mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: async () => ({
        gameId: "game-123",
        status: "IN_PROGRESS",
        result: null,
        endReason: null,
        availableDrawClaims: ["MOVE_RULE"],
        fen
      })
    } as Response)
    .mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: async () => ({
        gameId: "game-123",
        status: "COMPLETED",
        result: "DRAW",
        endReason: "MOVE_RULE",
        availableDrawClaims: [],
        fen
      })
    } as Response);

  render(
    <MemoryRouter initialEntries={["/game/game-123"]}>
      <Routes>
        <Route path="/game/:gameId" element={<PlayPage />} />
      </Routes>
    </MemoryRouter>
  );

  await user.click(
    await screen.findByRole("button", { name: "Claim Draw" })
  );

  await waitFor(() => {
    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8080/games/game-123/draw-claim",
      { method: "POST" }
    );
  });
  expect(await screen.findByText("Draw by move rule.")).toBeInTheDocument();
});

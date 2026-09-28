import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, expect, test, vi } from "vitest";
import PlayPage from "./PlayPage";

vi.mock("../components/Gameboard", () => ({
  default: ({
    onPositionChange,
    onSessionNotFound
  }: {
    onPositionChange: (fen: string) => void;
    onSessionNotFound: () => void;
  }) => (
    <div>
      <div data-testid="gameboard" />
      <button onClick={() => onPositionChange("8/8/8/8/8/4k3/8/4K3 b - - 1 1")}>Make mock move</button>
      <button onClick={onSessionNotFound}>Report missing session</button>
    </div>
  )
}));

afterEach(() => {
  cleanup();
  sessionStorage.clear();
  vi.restoreAllMocks();
});

const positionDetails = {
  positionId: "position-123",
  phase: "MIDDLEGAME",
  source: {
    gameUrl: "https://lichess.org/example",
    ply: 33,
    eco: "B76",
    opening: "Sicilian Defense: Dragon Variation",
    variation: null
  },
  context: {
    availability: "AVAILABLE",
    quality: "AI_VERIFIED",
    openingContext: { summary: "The game began as a Sicilian Dragon." },
    positionGuide: {
      summary: "Both sides are fighting around the center.",
      themes: ["King safety", "Central tension"]
    },
    possiblePlans: {
      white: { summary: "White can build pressure on the kingside." },
      black: { summary: "Black can seek queenside counterplay." }
    },
    message: null
  }
};

test("keeps the three guidance levels collapsed until requested", async () => {
  const user = userEvent.setup();
  vi.spyOn(globalThis, "fetch").mockResolvedValue({
    ok: true,
    status: 200,
    json: async () => ({
      gameId: "game-123",
      status: "IN_PROGRESS",
      result: null,
      endReason: null,
      availableDrawClaims: [],
      fen: "8/8/8/8/8/4k3/8/4K3 w - - 0 1",
      ...positionDetails
    })
  } as Response);

  render(
    <MemoryRouter initialEntries={["/game/game-123"]}>
      <Routes><Route path="/game/:gameId" element={<PlayPage />} /></Routes>
    </MemoryRouter>
  );

  expect(await screen.findByText("ECO B76")).toBeInTheDocument();
  const briefing = screen.getByText("Both sides are fighting around the center.");
  expect(briefing).not.toBeVisible();
  await user.click(screen.getByText("Current position briefing"));
  expect(briefing).toBeVisible();
  expect(screen.getByText("King safety")).toBeVisible();
});

test("renders the game when an already-running backend omits source metadata", async () => {
  vi.spyOn(globalThis, "fetch").mockResolvedValue({
    ok: true,
    status: 200,
    json: async () => ({
      gameId: "game-123",
      status: "IN_PROGRESS",
      result: null,
      endReason: null,
      availableDrawClaims: [],
      fen: "8/8/8/8/8/4k3/8/4K3 w - - 0 1",
      positionId: positionDetails.positionId,
      phase: positionDetails.phase,
      context: positionDetails.context
    })
  } as Response);

  render(
    <MemoryRouter initialEntries={["/game/game-123"]}>
      <Routes><Route path="/game/:gameId" element={<PlayPage />} /></Routes>
    </MemoryRouter>
  );

  expect(await screen.findByText("Middlegame position")).toBeInTheDocument();
  expect(screen.getByTestId("gameboard")).toBeInTheDocument();
  expect(screen.getByText("Current position briefing")).toBeInTheDocument();
});

test("updates the player indicator when the board side to move changes", async () => {
  const user = userEvent.setup();
  vi.spyOn(globalThis, "fetch").mockResolvedValue({
    ok: true,
    status: 200,
    json: async () => ({
      gameId: "game-123",
      status: "IN_PROGRESS",
      result: null,
      endReason: null,
      availableDrawClaims: [],
      fen: "8/8/8/8/8/4k3/8/4K3 w - - 0 1",
      ...positionDetails
    })
  } as Response);

  render(
    <MemoryRouter initialEntries={["/game/game-123"]}>
      <Routes><Route path="/game/:gameId" element={<PlayPage />} /></Routes>
    </MemoryRouter>
  );

  expect(await screen.findByText("White to move")).toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: "Make mock move" }));
  expect(await screen.findByText("Black to move")).toBeInTheDocument();
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
        fen,
        ...positionDetails
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
        fen,
        ...positionDetails
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

test("keeps the board mounted when a move updates the position", async () => {
  const user = userEvent.setup();
  vi.spyOn(globalThis, "fetch").mockResolvedValue({
    ok: true,
    json: async () => ({ gameId: "game-123", status: "IN_PROGRESS", result: null,
      endReason: null, availableDrawClaims: [],
      fen: "8/8/8/8/8/4k3/8/4K3 w - - 0 1", ...positionDetails })
  } as Response);
  render(<MemoryRouter initialEntries={["/game/game-123"]}>
    <Routes><Route path="/game/:gameId" element={<PlayPage />} /></Routes>
  </MemoryRouter>);
  await screen.findByText("ECO B76");
  const board = screen.getByTestId("gameboard");
  await user.click(screen.getByRole("button", { name: "Make mock move" }));
  expect(screen.getByTestId("gameboard")).toBe(board);
  expect(screen.getByText("Black to move")).toBeInTheDocument();
  expect(screen.getByText("Starting Position: White's move 17")).toBeInTheDocument();
});
test("keeps the play page visible for a missing game and starts a replacement position", async () => {
  const user = userEvent.setup();
  sessionStorage.setItem("gameId", "missing-game");
  const fetchMock = vi.spyOn(globalThis, "fetch")
    .mockResolvedValueOnce({ ok: false, status: 404 } as Response)
    .mockResolvedValueOnce({
      ok: true,
      status: 201,
      json: async () => ({ gameId: "replacement-game" })
    } as Response)
    .mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: async () => ({
        gameId: "replacement-game",
        status: "IN_PROGRESS",
        result: null,
        endReason: null,
        availableDrawClaims: [],
        fen: "8/8/8/8/8/4k3/8/4K3 w - - 0 1",
        ...positionDetails
      })
    } as Response);

  render(
    <MemoryRouter initialEntries={["/game/missing-game"]}>
      <Routes>
        <Route path="/" element={<div>Home page</div>} />
        <Route path="/game/:gameId" element={<PlayPage />} />
      </Routes>
    </MemoryRouter>
  );

  const missingSessionAlerts = await screen.findAllByRole("alert");
  expect(missingSessionAlerts).toHaveLength(2);
  for (const alert of missingSessionAlerts) {
    expect(alert).toHaveTextContent("Game not found");
    expect(alert).toHaveTextContent("Click New position to start another game.");
  }
  expect(screen.queryByText("Curated middlegame position")).not.toBeInTheDocument();
  expect(screen.queryByRole("button", { name: /position info/i })).not.toBeInTheDocument();
  expect(screen.getByTestId("gameboard")).toBeInTheDocument();
  expect(sessionStorage.getItem("gameId")).toBeNull();

  await user.click(screen.getByRole("button", { name: "New position" }));
  await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(
    "http://localhost:8080/games",
    { method: "POST" }
  ));
  expect(sessionStorage.getItem("gameId")).toBe("replacement-game");
  expect(await screen.findByTestId("gameboard")).toBeInTheDocument();
  expect(screen.queryByRole("alert")).not.toBeInTheDocument();
});

test("shows the same missing-session message if an active board reports that its session disappeared", async () => {
  const user = userEvent.setup();
  sessionStorage.setItem("gameId", "game-123");
  vi.spyOn(globalThis, "fetch").mockResolvedValue({
    ok: true,
    status: 200,
    json: async () => ({
      gameId: "game-123",
      status: "IN_PROGRESS",
      result: null,
      endReason: null,
      availableDrawClaims: [],
      fen: "8/8/8/8/8/4k3/8/4K3 w - - 0 1",
      ...positionDetails
    })
  } as Response);

  render(
    <MemoryRouter initialEntries={["/game/game-123"]}>
      <Routes><Route path="/game/:gameId" element={<PlayPage />} /></Routes>
    </MemoryRouter>
  );

  await screen.findByText("ECO B76");
  await user.click(screen.getByRole("button", { name: "Report missing session" }));
  expect(screen.getAllByRole("alert")).toHaveLength(2);
  expect(screen.getAllByRole("alert")[0]).toHaveTextContent("Click New position to start another game.");
  expect(sessionStorage.getItem("gameId")).toBeNull();
});

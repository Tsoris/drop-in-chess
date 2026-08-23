import { beforeEach, expect, test, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import LandingPage from "./LandingPage";
import userEvent from "@testing-library/user-event";

const mockNavigate = vi.fn();

vi.mock("react-router-dom", async () => {
    const actual = await vi.importActual("react-router-dom");

    return {
        ...actual,
        useNavigate: () => mockNavigate,
    };
});

beforeEach(() => {
    sessionStorage.clear();
    mockNavigate.mockClear();
    vi.restoreAllMocks();
});

test("creates a new game when stored session is stale", async () => {
    const user = userEvent.setup();

    // Pretend the browser remembers an old game.
    sessionStorage.setItem("gameId", "stale-game-id");

    // Mock the backend.
    const fetchMock = vi
        .spyOn(globalThis, "fetch")

        // First request: checking the stored game fails.
        .mockResolvedValueOnce({
            ok: false,
            status: 404,
        } as Response)

        // Second request: creating a new game succeeds.
        .mockResolvedValueOnce({
            ok: true,
            status: 201,
            json: async () => ({
                gameId: "new-game-id",
                fen: "test-fen",
            }),
        } as Response);

    render(
        <MemoryRouter>
            <LandingPage />
        </MemoryRouter>
    );

    await user.click(
        screen.getByRole("button", { name: /play now/i })
    );

    await waitFor(() => {
        expect(fetchMock).toHaveBeenCalledTimes(2);
    });

    expect(fetchMock).toHaveBeenNthCalledWith(
        1,
        "http://localhost:8080/games/stale-game-id"
    );

    expect(fetchMock).toHaveBeenNthCalledWith(
        2,
        "http://localhost:8080/games",
        { method: "POST" }
    );

    expect(sessionStorage.getItem("gameId")).toBe("new-game-id");

    expect(mockNavigate).toHaveBeenCalledWith(
        "/game/new-game-id"
    );
});
import { Chess } from "chess.js";
import { describe, expect, test } from "vitest";
import { checkedKingSquare } from "./Gameboard";

describe("checkedKingSquare", () => {
  test("returns the checked side's king square", () => {
    const game = new Chess("4k3/8/8/8/8/8/4R3/4K3 b - - 0 1");

    expect(game.isCheck()).toBe(true);
    expect(checkedKingSquare(game)).toBe("e8");
  });

  test("returns no square when neither king is in check", () => {
    const game = new Chess("4k3/8/8/8/8/8/8/4K3 w - - 0 1");

    expect(game.isCheck()).toBe(false);
    expect(checkedKingSquare(game)).toBeNull();
  });
});

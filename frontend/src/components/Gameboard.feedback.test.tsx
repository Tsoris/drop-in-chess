import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, expect, test, vi } from 'vitest';
import { Chess } from 'chess.js';
import GameBoard from './Gameboard';

vi.mock('react-chessboard', () => ({
  Chessboard: ({ options }: { options: { position: string; squareStyles: Record<string, object>; onSquareClick: (args: unknown) => void } }) => {
    const game = new Chess(options.position);
    return <>{['e2', 'e4', 'd5', 'e7', 'a1', 'e1', 'd2'].map(square => {
      const piece = game.get(square as 'e2');
      return <button key={square} data-square={square} data-highlighted={Boolean(options.squareStyles[square])} aria-label={square}
        onClick={() => options.onSquareClick({ square, piece: piece ? { pieceType: piece.color + piece.type.toUpperCase() } : null })}>
        {piece && <span data-piece={piece.type}><svg /></span>}
      </button>;
    })}</>;
  },
}));

afterEach(() => { cleanup(); vi.restoreAllMocks(); vi.unstubAllGlobals(); });

function setup(reduced = false, fen = new Chess().fen()) {
  const animate = vi.fn(() => ({ cancel: vi.fn() }));
  vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: reduced })));
  vi.stubGlobal('Animation', class {});
  const original = Element.prototype.animate;
  Object.defineProperty(Element.prototype, 'animate', { configurable: true, value: animate });
  render(<GameBoard gameId="test" chessPosition={fen}
    gameState={{ status: 'IN_PROGRESS', result: null, endReason: null, availableDrawClaims: [] }}
    onPositionChange={() => {}} onGameStateChange={() => {}} />);
  return { animate, restore: () => Object.defineProperty(Element.prototype, 'animate', { configurable: true, value: original }) };
}

test('selects own pieces without animation and shakes wrong-color pieces without sending a move', () => {
  const fetchMock = vi.spyOn(globalThis, 'fetch');
  const { animate, restore } = setup();
  try {
    fireEvent.click(screen.getByRole('button', { name: 'e2' }));
    expect(animate).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: 'e7' }));
    expect(animate.mock.calls[0]).toEqual(expect.arrayContaining([expect.objectContaining({ duration: 260 })]));
    expect(screen.getByRole('button', { name: 'e4' })).toHaveAttribute('data-highlighted', 'false');
    fireEvent.click(screen.getByRole('button', { name: 'e4' }));
    expect(fetchMock).not.toHaveBeenCalled();
  } finally { restore(); }
});

test('respects reduced-motion preference', () => {
  const { animate, restore } = setup(true);
  try {
    fireEvent.click(screen.getByRole('button', { name: 'e2' }));
    fireEvent.click(screen.getByRole('button', { name: 'e7' }));
    expect(animate).not.toHaveBeenCalled();
  } finally { restore(); }
});

test('clicking an opponent piece still allows a legal capture', () => {
  const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(() => new Promise(() => {}));
  const { animate, restore } = setup(false, '4k3/8/8/3p4/4P3/8/8/4K3 w - - 0 1');
  try {
    fireEvent.click(screen.getByRole('button', { name: 'e4' }));
    fireEvent.click(screen.getByRole('button', { name: 'd5' }));
    expect(fetchMock).toHaveBeenCalledOnce();
    expect(animate).not.toHaveBeenCalled();
  } finally { restore(); }
});

test('shakes an own piece that cannot resolve check without sending a move', () => {
  const fetchMock = vi.spyOn(globalThis, 'fetch');
  const { animate, restore } = setup(false, 'k3r3/8/8/8/8/8/8/R3K3 w - - 0 1');
  try {
    fireEvent.click(screen.getByRole('button', { name: 'a1' }));
    expect(animate).toHaveBeenCalledOnce();
    expect(screen.getByRole('button', { name: 'a1' })).toHaveAttribute('data-highlighted', 'false');
    expect(fetchMock).not.toHaveBeenCalled();
  } finally { restore(); }
});

test('allows selecting another piece if it can legally block check', () => {
  const { animate, restore } = setup(false, 'k3r3/8/8/8/8/8/3B4/4K3 w - - 0 1');
  try {
    fireEvent.click(screen.getByRole('button', { name: 'd2' }));
    expect(animate).not.toHaveBeenCalled();
    expect(screen.getByRole('button', { name: 'd2' })).toHaveAttribute('data-highlighted', 'true');
  } finally { restore(); }
});

test('shakes a blocked own piece even when the king is not in check', () => {
  const { animate, restore } = setup();
  try {
    fireEvent.click(screen.getByRole('button', { name: 'a1' }));
    expect(animate).toHaveBeenCalledOnce();
    expect(screen.getByRole('button', { name: 'a1' })).toHaveAttribute('data-highlighted', 'false');
  } finally { restore(); }
});
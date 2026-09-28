import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, expect, test, vi } from 'vitest';
import { Chess } from 'chess.js';
import GameBoard from './Gameboard';

vi.mock('react-chessboard', () => ({
  Chessboard: ({ options }: { options: { position: string; squareStyles: Record<string, object>; onSquareClick: (args: unknown) => void } }) => {
    const game = new Chess(options.position);
    return <>{['e2', 'e4', 'd5', 'e7', 'a1', 'e1', 'd2', 'a7', 'a8', 'b8', 'b2', 'b1'].map(square => {
      const piece = game.get(square as 'e2');
      return <button key={square} data-square={square} data-highlighted={Boolean(options.squareStyles[square])} aria-label={square}
        onClick={() => options.onSquareClick({ square, piece: piece ? { pieceType: piece.color + piece.type.toUpperCase() } : null })}>
        {piece && <span data-piece={piece.type}><svg /></span>}
      </button>;
    })}</>;
  },
}));

afterEach(() => { cleanup(); vi.restoreAllMocks(); vi.unstubAllGlobals(); });

function setup(reduced = false, fen = new Chess().fen(), onSessionNotFound = () => {}) {
  const animate = vi.fn(() => ({ cancel: vi.fn() }));
  vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: reduced })));
  vi.stubGlobal('Animation', class {});
  const original = Element.prototype.animate;
  Object.defineProperty(Element.prototype, 'animate', { configurable: true, value: animate });
  render(<GameBoard gameId="test" chessPosition={fen}
    gameState={{ status: 'IN_PROGRESS', result: null, endReason: null, availableDrawClaims: [] }}
    onPositionChange={() => {}} onGameStateChange={() => {}} onSessionNotFound={onSessionNotFound} />);
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
function mockPromotionDialog() {
  Object.defineProperty(HTMLDialogElement.prototype, 'showModal', { configurable: true, value: function (this: HTMLDialogElement) { this.setAttribute('open', ''); } });
  Object.defineProperty(HTMLDialogElement.prototype, 'close', { configurable: true, value: function (this: HTMLDialogElement) { this.removeAttribute('open'); } });
}

for (const scenario of [
  { label: 'white', fen: '7k/P7/8/8/8/8/8/7K w - - 0 1', from: 'a7', to: 'a8' },
  { label: 'black', fen: '7k/8/8/8/8/8/1p6/7K b - - 0 1', from: 'b2', to: 'b1' },
  { label: 'capture', fen: '1r5k/P7/8/8/8/8/8/7K w - - 0 1', from: 'a7', to: 'b8' },
]) {
  test.each([['Queen', 'q'], ['Rook', 'r'], ['Bishop', 'b'], ['Knight', 'n']])(`${scenario.label} promotion to %s submits the selected piece and matching FEN`, (name, piece) => {
    mockPromotionDialog();
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(() => new Promise(() => {}));
    const { restore } = setup(false, scenario.fen);
    try {
      fireEvent.click(screen.getByRole('button', { name: scenario.from }));
      fireEvent.click(screen.getByRole('button', { name: scenario.to }));
      expect(screen.getByRole('dialog', { name: 'Promote your pawn' })).toBeInTheDocument();
      expect(fetchMock).not.toHaveBeenCalled();
      fireEvent.click(screen.getByRole('button', { name }));
      const expected = new Chess(scenario.fen);
      expected.move({ from: scenario.from, to: scenario.to, promotion: piece });
      expect(fetchMock).toHaveBeenCalledOnce();
      expect(JSON.parse(fetchMock.mock.calls[0][1]!.body as string)).toEqual({
        from: scenario.from.toUpperCase(), to: scenario.to.toUpperCase(), promotion: piece, checkFen: expected.fen(),
      });
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    } finally { restore(); }
  });
}

test('cancelling promotion leaves the pawn unmoved and sends no request', () => {
  mockPromotionDialog();
  const fetchMock = vi.spyOn(globalThis, 'fetch');
  const { restore } = setup(false, '7k/P7/8/8/8/8/8/7K w - - 0 1');
  try {
    fireEvent.click(screen.getByRole('button', { name: 'a7' }));
    fireEvent.click(screen.getByRole('button', { name: 'a8' }));
    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'a7' }).querySelector('[data-piece="p"]')).not.toBeNull();
    expect(fetchMock).not.toHaveBeenCalled();
  } finally { restore(); }
});
test('clicking outside the promotion panel cancels without submitting', () => {
  mockPromotionDialog();
  const fetchMock = vi.spyOn(globalThis, 'fetch');
  const { restore } = setup(false, '7k/P7/8/8/8/8/8/7K w - - 0 1');
  try {
    fireEvent.click(screen.getByRole('button', { name: 'a7' }));
    fireEvent.click(screen.getByRole('button', { name: 'a8' }));
    fireEvent.click(screen.getByRole('dialog'), { clientX: -10, clientY: -10 });
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  } finally { restore(); }
});
test('reports a missing session when a move returns 404', async () => {
  const onSessionNotFound = vi.fn();
  vi.spyOn(globalThis, 'fetch').mockResolvedValue({ status: 404, ok: false } as Response);
  const { restore } = setup(false, new Chess().fen(), onSessionNotFound);
  try {
    fireEvent.click(screen.getByRole('button', { name: 'e2' }));
    fireEvent.click(screen.getByRole('button', { name: 'e4' }));
    await vi.waitFor(() => expect(onSessionNotFound).toHaveBeenCalledOnce());
  } finally { restore(); }
});

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, expect, test, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { GameRoutes } from './App';
import Header from './components/Header';


vi.mock('./Pages/PlayPage', () => ({ default: () => <div>Play page</div> }));
afterEach(() => { cleanup(); sessionStorage.clear(); vi.restoreAllMocks(); });

test('Continue appears above the board and preserves a refreshed game URL', async () => {
  vi.spyOn(globalThis, 'fetch').mockResolvedValue({ ok: true, json: async () => ({ status: 'CONNECTED' }) } as Response);
  render(<MemoryRouter initialEntries={['/game/example']}><Header /><GameRoutes /></MemoryRouter>);
  const proceed = await screen.findByRole('button', { name: 'Continue to Drop in Chess' });
  const board = screen.getByRole('group', { name: 'Knight mini-game board' });
  expect(proceed.compareDocumentPosition(board) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  fireEvent.click(proceed);
  expect(screen.getByText('Play page')).toBeInTheDocument();
  expect(screen.queryByRole('region', { name: 'Welcome to Drop in Chess' })).not.toBeInTheDocument();
});

test('Knight Quest opened from the landing page returns to the landing page', async () => {
  sessionStorage.setItem('drop-in-chess-startup-dismissed', 'true');
  vi.spyOn(globalThis, 'fetch').mockResolvedValue({ ok: true, json: async () => ({ status: 'CONNECTED' }) } as Response);
  render(<MemoryRouter initialEntries={['/']}><GameRoutes /></MemoryRouter>);
  expect(await screen.findByRole('region', { name: 'Welcome to Drop in Chess' })).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'Play Here' }));
  expect(screen.getByRole('group', { name: 'Knight mini-game board' })).toBeInTheDocument();
  expect(screen.getByText("Server ready. Continue when you're ready.")).toBeVisible();
  fireEvent.click(screen.getByRole('button', { name: 'Continue to Drop in Chess' }));
  expect(screen.getByRole('region', { name: 'Welcome to Drop in Chess' })).toBeInTheDocument();
});

test('shared Knight Quest is playable while the backend is still starting', () => {
  vi.spyOn(globalThis, 'fetch').mockImplementation(() => new Promise(() => {}));
  render(<MemoryRouter initialEntries={['/']}><GameRoutes /></MemoryRouter>);
  expect(screen.getByRole('group', { name: 'Knight mini-game board' })).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Reset' })).toBeInTheDocument();
  expect(screen.getByText(/Starting Drop in Chess/)).toBeVisible();
});

test('refresh after Continue restores the current route without reopening Knight Quest', async () => {
  vi.spyOn(globalThis, 'fetch').mockResolvedValue({ ok: true, json: async () => ({ status: 'CONNECTED' }) } as Response);
  const first = render(<MemoryRouter><GameRoutes /></MemoryRouter>);
  fireEvent.click(await screen.findByRole('button', { name: 'Continue to Drop in Chess' }));
  expect(sessionStorage.getItem('drop-in-chess-startup-dismissed')).toBe('true');
  first.unmount();
  render(<MemoryRouter initialEntries={['/game/example']}><GameRoutes /></MemoryRouter>);
  expect(screen.queryByRole('group', { name: 'Knight mini-game board' })).not.toBeInTheDocument();
  expect(await screen.findByText('Play page')).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'Continue to Drop in Chess' })).not.toBeInTheDocument();
});

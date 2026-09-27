import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, expect, test, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { GameRoutes } from './App';
import Header from './components/Header';


vi.mock('./Pages/PlayPage', () => ({ default: () => <div>Play page</div> }));
afterEach(() => { cleanup(); vi.restoreAllMocks(); });

test('Continue appears above the board and returns a game URL to the main page; replay returns home', async () => {
  vi.spyOn(globalThis, 'fetch').mockResolvedValue({ ok: true, json: async () => ({ status: 'CONNECTED' }) } as Response);
  render(<MemoryRouter initialEntries={['/game/example']}><Header /><GameRoutes /></MemoryRouter>);
  const proceed = await screen.findByRole('button', { name: 'Continue to Drop in Chess' });
  const board = screen.getByRole('group', { name: 'Knight mini-game board' });
  expect(proceed.compareDocumentPosition(board) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  fireEvent.click(proceed);
  expect(screen.getByText('Welcome to Drop in Chess')).toBeInTheDocument();
  expect(screen.queryByText('Play page')).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'Play Here' }));
  expect(screen.getByRole('group', { name: 'Knight mini-game board' })).toBeInTheDocument();
  expect(screen.getByText('Server ready! Continue whenever you are ready.')).toBeVisible();
  expect(screen.getByRole('group', { name: 'Knight mini-game board' })).toBe(board);
  fireEvent.click(screen.getByRole('button', { name: 'Continue to Drop in Chess' }));
  expect(screen.getByText('Welcome to Drop in Chess')).toBeInTheDocument();
});

test('shared Knight Quest is playable while the backend is still starting', () => {
  vi.spyOn(globalThis, 'fetch').mockImplementation(() => new Promise(() => {}));
  render(<MemoryRouter initialEntries={['/']}><GameRoutes /></MemoryRouter>);
  expect(screen.getByRole('group', { name: 'Knight mini-game board' })).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Reset' })).toBeInTheDocument();
  expect(screen.getByText(/Starting Drop in Chess/)).toBeVisible();
});

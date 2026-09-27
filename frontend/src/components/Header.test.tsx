import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, expect, test, vi } from 'vitest';
import Header from './Header';

afterEach(() => {
  cleanup();
  localStorage.clear();
  delete document.documentElement.dataset.theme;
  vi.unstubAllGlobals();
});

test('uses the system theme, toggles both ways, and remembers the choice', () => {
  vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: true })));
  const view = render(<MemoryRouter><Header /></MemoryRouter>);
  expect(document.documentElement.dataset.theme).toBe('dark');
  fireEvent.click(screen.getByRole('button', { name: 'Switch to light mode' }));
  expect(document.documentElement.dataset.theme).toBe('light');
  expect(localStorage.getItem('drop-in-chess-theme')).toBe('light');
  view.unmount();
  render(<MemoryRouter><Header /></MemoryRouter>);
  expect(document.documentElement.dataset.theme).toBe('light');
  fireEvent.click(screen.getByRole('button', { name: 'Switch to dark mode' }));
  expect(document.documentElement.dataset.theme).toBe('dark');
  expect(localStorage.getItem('drop-in-chess-theme')).toBe('dark');
});

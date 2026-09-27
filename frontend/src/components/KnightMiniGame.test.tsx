import { afterEach, expect, test, vi } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { coinPoints, knightDistances, knightMoves, newCoin, squareName } from '../lib/knightGame';
import KnightMiniGame from './KnightMiniGame';

afterEach(() => { cleanup(); vi.restoreAllMocks(); });

test('every knight edge is legal and targets are 2â€“3 shortest-path moves from all 64 squares', () => {
  for (let start = 0; start < 64; start++) {
    const distances = knightDistances(start);
    expect(distances.every(distance => distance >= 0)).toBe(true);
    for (const end of knightMoves(start)) {
      const dr = Math.abs(Math.floor(start / 8) - Math.floor(end / 8));
      const dc = Math.abs(start % 8 - end % 8);
      expect([dr, dc].sort()).toEqual([1, 2]);
    }
    for (const random of [0, .2, .4, .6, .8, .999]) {
      const spy = vi.spyOn(Math, 'random').mockReturnValue(random);
      expect([2, 3]).toContain(distances[newCoin(start)]);
      spy.mockRestore();
    }
  }
  expect(knightMoves(0).sort()).toEqual([10, 17]);
});

function followShortestPath(start: number, target: number) {
  const distances = knightDistances(target);
  let square = start;
  while (square !== target) {
    square = knightMoves(square).find(next => distances[next] === distances[square] - 1)!;
    fireEvent.click(screen.getByRole('button', { name: new RegExp(`^${squareName(square)},`) }));
  }
}

test.each([0, .8])('awards full shortest-path points and resets the next target (random %s)', random => {
  vi.spyOn(Math, 'random').mockReturnValue(random);
  render(<KnightMiniGame />);
  const start = Math.floor(random * 64);
  const coin = newCoin(start);
  const distance = knightDistances(start)[coin];
  fireEvent.click(screen.getByRole('button', { name: `${squareName(start)}, knight` }));
  expect(screen.getByLabelText('Current coin value')).toHaveTextContent(`Current coin: +${distance} points`);
  followShortestPath(start, coin);
  expect(screen.getByLabelText('Score')).toHaveTextContent(`Score: ${distance}`);
  expect(screen.getByRole('button', { name: `${squareName(coin)}, knight` })).toBeInTheDocument();
  expect(screen.getByLabelText('Current coin value')).toHaveTextContent(/^Current coin: \+[23] points$/);
  fireEvent.click(screen.getByRole('button', { name: 'Reset' }));
  expect(screen.getByLabelText('Score')).toHaveTextContent('Score: 0');
  expect(screen.getByLabelText('Current coin value')).toHaveTextContent(/^Current coin: \+[23] points$/);
});

test('extra moves lower the displayed reward and collection never awards less than one', () => {
  vi.spyOn(Math, 'random').mockReturnValue(0);
  render(<KnightMiniGame />);
  const coin = newCoin(0);
  expect(screen.getByLabelText('Current coin value')).toHaveTextContent('Current coin: +2 points');
  // Two out-and-back detours avoid the two-move-away coin.
  for (let i = 0; i < 2; i++) {
    fireEvent.click(screen.getByRole('button', { name: 'c7, legal destination' }));
    fireEvent.click(screen.getByRole('button', { name: 'a8, legal destination' }));
  }
  expect(screen.getByLabelText('Current coin value')).toHaveTextContent('Current coin: +1 points');
  followShortestPath(0, coin);
  expect(screen.getByLabelText('Score')).toHaveTextContent('Score: 1');
});

test('reward counts the collecting move and floors penalties at one', () => {
  expect(coinPoints(3, 3)).toBe(3);
  expect(coinPoints(3, 4)).toBe(2);
  expect(coinPoints(3, 5)).toBe(1);
  expect(coinPoints(3, 100)).toBe(1);
  expect(coinPoints(2, 2)).toBe(2);
  expect(coinPoints(2, 3)).toBe(1);
});

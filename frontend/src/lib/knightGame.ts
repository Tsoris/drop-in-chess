const offsets = [[-2, -1], [-2, 1], [-1, -2], [-1, 2], [1, -2], [1, 2], [2, -1], [2, 1]];

export function knightMoves(square: number): number[] {
  const row = Math.floor(square / 8);
  const col = square % 8;
  return offsets.flatMap(([dr, dc]) => {
    const r = row + dr;
    const c = col + dc;
    return r >= 0 && r < 8 && c >= 0 && c < 8 ? [r * 8 + c] : [];
  });
}

export function knightDistances(start: number): number[] {
  const distances = Array<number>(64).fill(-1);
  distances[start] = 0;
  const queue = [start];
  for (let i = 0; i < queue.length; i++) {
    for (const next of knightMoves(queue[i])) {
      if (distances[next] !== -1) continue;
      distances[next] = distances[queue[i]] + 1;
      queue.push(next);
    }
  }
  return distances;
}

export function newCoin(knight: number): number {
  const weights = [2, 2, 3, 3, 3];
  const distance = weights[Math.floor(Math.random() * weights.length)];
  const distances = knightDistances(knight);
  const candidates = Array.from({ length: 64 }, (_, square) => square)
    .filter(square => distances[square] === distance);
  return candidates[Math.floor(Math.random() * candidates.length)];
}

export function newKnightGame() {
  const knight = Math.floor(Math.random() * 64);
  return { knight, ...newTarget(knight), score: 0 };
}

export function squareName(square: number): string {
  return `${'abcdefgh'[square % 8]}${8 - Math.floor(square / 8)}`;
}

export function newTarget(knight: number) {
  const coin = newCoin(knight);
  return { coin, targetMoves: knightDistances(knight)[coin], moves: 0 };
}

export function coinPoints(targetMoves: number, moves: number): number {
  return Math.max(1, targetMoves - Math.max(0, moves - targetMoves));
}

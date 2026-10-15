import type { MangolaStep } from './MangolaState';
import { PITS, nextPit, pitFromName, pitName } from './geometry';

// Test support: a TypeScript port of the backend's `Sowing.sow` (the rules of the owner's game), so that the
// animation tests run on real traces. It is checked against the hand-made examples E1..E10 of the spec in
// `replay.test.ts`. Not used by the application.

/** A board from `a2:3 c2:1` (pits not listed are empty). */
export function boardOf(text: string): number[] {
  const board = Array<number>(PITS).fill(0);
  for (const part of text.split(/\s+/).filter(Boolean)) {
    const [name, count] = part.split(':');
    const pit = pitFromName(name);
    if (pit === null) throw new Error(`Not a pit: ${name}`);
    board[pit] = Number(count);
  }
  return board;
}

/** The non-empty pits of a board, `a2:3 c2:1`, in pit order: for readable comparisons. */
export function boardText(board: readonly number[]): string {
  return board.map((n, pit) => (n > 0 ? `${pitName(pit)}:${n}` : '')).filter(Boolean).join(' ');
}

const facingOf = (pit: number): number | null => {
  const row = Math.floor(pit / 8);
  return row === 1 ? pit + 8 : row === 2 ? pit - 8 : null;
};
const behindOf = (pit: number): number | null => {
  const row = Math.floor(pit / 8);
  return row === 1 ? pit + 16 : row === 2 ? pit - 16 : null;
};

export interface Sown {
  board: number[];
  trace: MangolaStep[];
  drops: number;
}

/** Plays `pit` on `previous` and returns the new board with the trace the server would send. */
export function sow(previous: readonly number[], pitText: string, maxDrops = 5000): Sown {
  const pit = pitFromName(pitText);
  if (pit === null) throw new Error(`Not a pit: ${pitText}`);
  const seeds = [...previous];
  const trace: MangolaStep[] = [];
  let hand = seeds[pit];
  seeds[pit] = 0;
  trace.push({ type: 'PICK', pit, count: hand });
  let lapStart = pit;
  let position = pit;
  let drops = 0;

  for (;;) {
    while (hand > 1) {
      position = nextPit(position);
      seeds[position]++;
      hand--;
      drops++;
      trace.push({ type: 'DROP', pit: position });
    }
    if (drops > maxDrops) throw new Error('endless sowing');

    const landing = nextPit(position);
    if (seeds[landing] === 0) {
      const after = nextPit(landing);
      seeds[after]++;
      drops++;
      trace.push({ type: 'SKIP_EMPTY', pit: landing }, { type: 'DROP', pit: after });
      return { board: seeds, trace, drops };
    }

    seeds[landing]++;
    drops++;
    trace.push({ type: 'DROP', pit: landing });
    position = landing;
    const facing = facingOf(landing);
    const behind = behindOf(landing);
    if (facing !== null && behind !== null && seeds[facing] > 0 && seeds[behind] > 0) {
      hand = seeds[facing] + seeds[behind];
      trace.push({ type: 'CAPTURE', pit: landing, count: hand, facing, behind });
      seeds[facing] = 0;
      seeds[behind] = 0;
      trace.push({ type: 'RESUME_AFTER', pit: lapStart });
      position = lapStart;
    } else {
      hand = seeds[landing];
      seeds[landing] = 0;
      trace.push({ type: 'RELAY', pit: landing, count: hand });
      lapStart = landing;
    }
  }
}

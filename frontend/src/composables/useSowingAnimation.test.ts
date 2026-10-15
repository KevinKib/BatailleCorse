import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { effectScope } from 'vue';
import { useSowingAnimation, MAX_QUEUED_STATES } from './useSowingAnimation';
import { mangolaState } from '../model/mangola/mangolaFixtures';
import { boardOf, sow } from '../model/mangola/sowingFixtures';
import { MAX_TOTAL_MS, planSowing } from '../model/mangola/trace';
import type { MangolaState } from '../model/mangola/MangolaState';
import { pitFromName } from '../model/mangola/geometry';

const POSITIONS: { name: string; before: string; pit: string }[] = [
  { name: 'PICK/DROP/SKIP_EMPTY (E1)', before: 'a2:3', pit: 'a2' },
  { name: 'RELAY (E2)', before: 'a2:2 c2:3', pit: 'a2' },
  { name: 'CAPTURE/RESUME_AFTER (E3)', before: 'a2:2 c2:1 c3:2 c4:3', pit: 'a2' },
  { name: 'two captures and a relay (E9)', before: 'a2:2 c2:1 d2:1 c3:1 c4:2 d3:1 d4:1', pit: 'a2' },
  { name: 'laps (E8)', before: 'a2:17', pit: 'a2' },
];

/** The state the server sends after `pit` is played from `before` (the board is already final). */
function played(before: string, pit: string, version: number, over: Partial<MangolaState> = {}): MangolaState {
  const { board, trace } = sow(boardOf(before), pit);
  return mangolaState({
    version,
    board,
    lastMove: { seat: 0, pit: pitFromName(pit)!, trace },
    ...over,
  });
}

function start(before: string, version = 1, over: Partial<MangolaState> = {}): MangolaState {
  return mangolaState({ version, board: boardOf(before), ...over });
}

describe('useSowingAnimation', () => {
  let reduced = false;
  const make = () => useSowingAnimation({ reducedMotion: () => reduced });

  beforeEach(() => {
    vi.useFakeTimers();
    reduced = false;
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it('shows the first state at once, without a move to announce', () => {
    const anim = make();
    const first = played('a2:3', 'a2', 1);
    anim.push(first);
    expect(anim.animating.value).toBe(false);
    expect(anim.displayBoard.value).toEqual(first.board);
    expect(anim.announced.value).toBeNull();
  });

  it('shows nothing without a state', () => {
    const anim = make();
    expect(anim.displayBoard.value).toBeNull();
    anim.push(start('a2:3'));
    anim.push(null);
    expect(anim.displayBoard.value).toBeNull();
    expect(anim.animating.value).toBe(false);
  });

  it('plays the sowing of a move that arrives: the hand picks up, then the counts change step by step', () => {
    const anim = make();
    const before = start('a2:2 c2:1 c3:2 c4:3');
    anim.push(before);
    const next = played('a2:2 c2:1 c3:2 c4:3', 'a2', 2);
    anim.push(next);

    expect(anim.animating.value).toBe(true);
    // The picked pit is already empty and the hand is over it, holding its seeds.
    expect(anim.displayBoard.value![pitFromName('a2')!]).toBe(0);
    expect(anim.hand.value).toEqual({ pit: pitFromName('a2')!, seeds: 2 });
    expect(anim.activePit.value).toBe(pitFromName('a2')!);

    vi.advanceTimersByTime(1);
    const seen: string[] = [];
    for (let i = 0; i < 400 && anim.animating.value; i++) {
      seen.push(String(anim.displayBoard.value));
      vi.advanceTimersByTime(20);
    }
    expect(new Set(seen).size).toBeGreaterThan(5);
    expect(anim.animating.value).toBe(false);
    expect(anim.hand.value).toBeNull();
    expect(anim.activePit.value).toBeNull();
    expect(anim.displayBoard.value).toEqual(next.board);
  });

  for (const position of POSITIONS) {
    it(`ends exactly on the server board: ${position.name}`, () => {
      const anim = make();
      anim.push(start(position.before));
      const next = played(position.before, position.pit, 2);
      anim.push(next);
      vi.advanceTimersByTime(MAX_TOTAL_MS + 100);
      expect(anim.animating.value).toBe(false);
      expect(anim.displayBoard.value).toEqual(next.board);
      expect(anim.shownState.value).toBe(next);
    });
  }

  it('flags the pits a capture empties while the hand holds their seeds', () => {
    const anim = make();
    anim.push(start('a2:2 c2:1 c3:2 c4:3'));
    anim.push(played('a2:2 c2:1 c3:2 c4:3', 'a2', 2));
    let flagged: number[] = [];
    for (let i = 0; i < 400 && anim.animating.value; i++) {
      if (anim.capturing.value.length > 0) flagged = [...anim.capturing.value];
      vi.advanceTimersByTime(10);
    }
    expect(flagged).toEqual([pitFromName('c3')!, pitFromName('c4')!]);
  });

  it('announces the move once, at the start, not at each step', () => {
    const anim = make();
    anim.push(start('a2:3'));
    const next = played('a2:3', 'a2', 2);
    anim.push(next);
    const first = anim.announced.value;
    expect(first).toEqual({ version: 2, move: next.lastMove });
    const seen = new Set<unknown>([first]);
    for (let i = 0; i < 100; i++) {
      vi.advanceTimersByTime(20);
      seen.add(anim.announced.value);
    }
    expect(seen.size).toBe(1);
  });

  it('Skip jumps to the final board at once', () => {
    const anim = make();
    anim.push(start('a2:17'));
    const next = played('a2:17', 'a2', 2);
    anim.push(next);
    vi.advanceTimersByTime(300);
    expect(anim.animating.value).toBe(true);
    anim.skip();
    expect(anim.animating.value).toBe(false);
    expect(anim.hand.value).toBeNull();
    expect(anim.displayBoard.value).toEqual(next.board);
    // No timer is left to change the board afterwards.
    vi.advanceTimersByTime(MAX_TOTAL_MS);
    expect(anim.displayBoard.value).toEqual(next.board);
  });

  it('Skip does nothing when nothing is playing', () => {
    const anim = make();
    const first = start('a2:3');
    anim.push(first);
    anim.skip();
    expect(anim.displayBoard.value).toEqual(first.board);
  });

  /** The states the server sends for these moves in a row, versions from 2. */
  function sequence(before: string, pits: string[]): MangolaState[] {
    let board = boardOf(before);
    return pits.map((pit, i) => {
      const result = sow(board, pit);
      board = result.board;
      return mangolaState({ version: 2 + i, board, lastMove: { seat: i % 2, pit: pitFromName(pit)!, trace: result.trace } });
    });
  }

  it('queues a state that arrives mid-animation and plays it after', () => {
    const anim = make();
    const before = 'a2:3 h4:2';
    anim.push(start(before));
    const [mine, reply] = sequence(before, ['a2', 'h4']);
    anim.push(mine);
    vi.advanceTimersByTime(50);
    anim.push(reply);

    // Still showing my move's animation: the board is not the reply's.
    expect(anim.animating.value).toBe(true);
    expect(anim.shownState.value?.version).toBe(1);
    expect(anim.displayBoard.value).not.toEqual(reply.board);

    vi.advanceTimersByTime(planSowing(mine.lastMove!.trace).totalMs - 50 + 1);
    expect(anim.shownState.value?.version).toBe(2);
    // The reply started right after: its hand is on screen.
    expect(anim.animating.value).toBe(true);
    expect(anim.announced.value?.version).toBe(3);
    vi.advanceTimersByTime(MAX_TOTAL_MS + 50);
    expect(anim.animating.value).toBe(false);
    expect(anim.displayBoard.value).toEqual(reply.board);
    expect(anim.shownState.value).toBe(reply);
  });

  it('a newer version wins at the end: states queued past the limit jump to the latest board', () => {
    const anim = make();
    const before = 'a2:3 h4:3 a4:3 a1:3';
    anim.push(start(before));
    const states = sequence(before, ['a2', 'h4', 'a4', 'a1']);
    expect(states.length).toBeGreaterThan(MAX_QUEUED_STATES + 1);
    states.forEach(state => anim.push(state));
    vi.advanceTimersByTime(10 * MAX_TOTAL_MS);
    const last = states[states.length - 1];
    expect(anim.animating.value).toBe(false);
    expect(anim.displayBoard.value).toEqual(last.board);
    expect(anim.shownState.value).toBe(last);
    expect(anim.announced.value?.version).toBe(last.version);
  });

  it('ignores a state that is older than, or the same as, the newest one', () => {
    const anim = make();
    anim.push(start('a2:3 h4:2', 5));
    const next = played('a2:3 h4:2', 'a2', 6);
    anim.push(next);
    vi.advanceTimersByTime(MAX_TOTAL_MS + 50);
    anim.push(start('a2:3 h4:2', 5));
    anim.push(next);
    expect(anim.animating.value).toBe(false);
    expect(anim.displayBoard.value).toEqual(next.board);
  });

  it('shows the final board at once under reduced motion, with the move announced', () => {
    reduced = true;
    const anim = make();
    anim.push(start('a2:17'));
    const next = played('a2:17', 'a2', 2);
    anim.push(next);
    expect(anim.animating.value).toBe(false);
    expect(anim.hand.value).toBeNull();
    expect(anim.displayBoard.value).toEqual(next.board);
    expect(anim.announced.value).toEqual({ version: 2, move: next.lastMove });
  });

  it('snaps to the server board when the trace does not fit the board on screen', () => {
    const anim = make();
    anim.push(start('a2:3 h4:2'));
    // The previous board here is not the one the trace was computed from (a missed state).
    const next = played('a2:5 h4:2', 'a2', 4);
    anim.push(next);
    expect(anim.animating.value).toBe(false);
    expect(anim.displayBoard.value).toEqual(next.board);
    expect(anim.announced.value?.version).toBe(4);
  });

  it('does not play a state without a new move', () => {
    const anim = make();
    anim.push(start('a2:3'));
    const same = start('a2:3', 2, { repetitions: 2 });
    anim.push(same);
    expect(anim.animating.value).toBe(false);
    expect(anim.shownState.value).toBe(same);
    expect(anim.announced.value).toBeNull();
  });

  it('a state of another game replaces everything at once', () => {
    const anim = make();
    anim.push(start('a2:17'));
    anim.push(played('a2:17', 'a2', 2));
    expect(anim.animating.value).toBe(true);
    const other = start('a2:3 h4:2', 0, { id: 'g2' });
    anim.push(other);
    expect(anim.animating.value).toBe(false);
    expect(anim.displayBoard.value).toEqual(other.board);
    vi.advanceTimersByTime(MAX_TOTAL_MS);
    expect(anim.displayBoard.value).toEqual(other.board);
  });

  it('a 600-seed sowing ends within the cap', () => {
    const anim = make();
    const before = 'a2:20 b2:20 c2:20 d2:20 e2:20 f2:20 g2:20 h2:20 h1:20 g1:20';
    const { drops } = sow(boardOf(before), 'a2');
    expect(drops).toBeGreaterThan(100);
    anim.push(start(before));
    const next = played(before, 'a2', 2);
    anim.push(next);
    vi.advanceTimersByTime(MAX_TOTAL_MS + 10);
    expect(anim.animating.value).toBe(false);
    expect(anim.displayBoard.value).toEqual(next.board);
  });

  it('keeps the board before the move so the captured pits show what they lost, once it is over', () => {
    const anim = make();
    anim.push(start('a2:2 c2:1 c3:2 c4:3'));
    const next = played('a2:2 c2:1 c3:2 c4:3', 'a2', 2);
    anim.push(next);
    expect(anim.marks.value.captured).toEqual([]);
    vi.advanceTimersByTime(MAX_TOTAL_MS + 50);
    expect(anim.marks.value.start).toBe(pitFromName('a2'));
    expect(anim.marks.value.captured).toEqual([
      { pit: pitFromName('c3'), seeds: 2 },
      { pit: pitFromName('c4'), seeds: 3 },
    ]);
  });

  it('marks nothing before any move', () => {
    const anim = make();
    anim.push(start('a2:3'));
    expect(anim.marks.value).toEqual({ start: null, end: null, captured: [] });
  });

  it('stops its timers when its scope ends', () => {
    const scope = effectScope();
    const anim = scope.run(() => make())!;
    anim.push(start('a2:17'));
    anim.push(played('a2:17', 'a2', 2));
    expect(vi.getTimerCount()).toBeGreaterThan(0);
    scope.stop();
    expect(vi.getTimerCount()).toBe(0);
  });
});

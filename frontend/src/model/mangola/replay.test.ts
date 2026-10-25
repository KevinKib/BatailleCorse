import { describe, it, expect } from 'vitest';
import { replayTrace, sowingFrames } from './replay';
import { boardOf, sow, boardText } from './sowingFixtures';
import { planSowing } from './trace';
import type { MangolaStep, MangolaStepType } from './MangolaState';

// The hand-checked examples of the spec ("A move", E1..E10): the boards before and after, pit by pit.
const EXAMPLES: { name: string; before: string; pit: string; after: string; kinds: MangolaStepType[] }[] = [
  { name: 'E1 skip empty', before: 'a2:3', pit: 'a2', after: 'b2:1 c2:1 e2:1', kinds: ['SKIP_EMPTY'] },
  { name: 'E2 relay', before: 'a2:2 c2:3', pit: 'a2', after: 'b2:1 d2:1 e2:1 f2:1 h2:1', kinds: ['RELAY', 'SKIP_EMPTY'] },
  { name: 'E3 capture', before: 'a2:2 c2:1 c3:2 c4:3', pit: 'a2', after: 'b2:2 c2:3 d2:1 e2:1 g2:1', kinds: ['CAPTURE', 'RESUME_AFTER'] },
  { name: 'E5 outer relay', before: 'g1:2 e1:1 e3:2 e4:2', pit: 'g1', after: 'b1:1 d1:1 f1:1 e3:2 e4:2', kinds: ['RELAY'] },
  { name: 'E6 wrap rows', before: 'g2:3 a1:4', pit: 'g2', after: 'a1:4 f1:1 h1:1 h2:1', kinds: ['SKIP_EMPTY'] },
  { name: 'E8 laps', before: 'a2:17', pit: 'a2', after: 'b1:2 d1:2 f1:2 h1:2 a2:2 c2:3 e2:2 g2:2', kinds: ['RELAY'] },
  { name: 'E9 two captures', before: 'a2:2 c2:1 d2:1 c3:1 c4:2 d3:1 d4:1', pit: 'a2', after: 'b2:3 d2:3 e2:1 f2:1 h2:1', kinds: ['CAPTURE', 'RESUME_AFTER', 'RELAY'] },
  { name: 'E10 no relay', before: 'a2:2 d2:5', pit: 'a2', after: 'b2:1 d2:6', kinds: ['SKIP_EMPTY'] },
];

describe('the reference sowing used for fixtures', () => {
  for (const ex of EXAMPLES) {
    it(`reproduces ${ex.name} of the spec`, () => {
      const { board, trace } = sow(boardOf(ex.before), ex.pit);
      expect(boardText(board)).toBe(boardText(boardOf(ex.after)));
      const kinds = new Set(trace.map(s => s.type));
      for (const kind of ex.kinds) expect(kinds.has(kind)).toBe(true);
    });
  }

  it('reproduces E7, played by the second side', () => {
    const { board } = sow(boardOf('h4:2 g3:1 g2:1 g1:1'), 'h4');
    expect(boardText(board)).toBe(boardText(boardOf('c3:1 e3:1 f3:1 h3:2')));
  });
});

describe('replayTrace', () => {
  for (const ex of EXAMPLES) {
    it(`ends exactly on the server board for ${ex.name}`, () => {
      const before = boardOf(ex.before);
      const { board, trace } = sow(before, ex.pit);
      expect(replayTrace(before, trace)).toEqual(board);
    });
  }

  it('refuses a board with a hidden pit', () => {
    const before: (number | null)[] = boardOf('a2:3');
    before[20] = null;
    expect(replayTrace(before, [{ type: 'PICK', pit: 8, count: 3 }])).toBeNull();
  });

  it('does not modify the board it is given', () => {
    const before = boardOf('a2:3');
    const copy = [...before];
    replayTrace(before, sow(before, 'a2').trace);
    expect(before).toEqual(copy);
  });

  it('refuses a trace that takes seeds a pit does not hold', () => {
    expect(replayTrace(boardOf('a2:3'), [{ type: 'PICK', pit: 9, count: 3 }])).toBeNull();
  });
});

describe('sowingFrames', () => {
  const E3 = 'a2:2 c2:1 c3:2 c4:3';

  it('has one frame per step, each carrying the board after the step', () => {
    const before = boardOf(E3);
    const { trace } = sow(before, 'a2');
    const { frames } = sowingFrames(before, trace);
    expect(frames).toHaveLength(trace.length);
    // PICK: the pit is empty, the hand holds its seeds.
    expect(frames[0].board[8]).toBe(0);
    expect(frames[0].hand).toEqual({ pit: 8, seeds: 2 });
    // First drop: b2 holds one seed, one is left in hand.
    expect(frames[1].board[9]).toBe(1);
    expect(frames[1].hand).toEqual({ pit: 9, seeds: 1 });
  });

  it('shows the intermediate counts of E3 in order, capture included', () => {
    const before = boardOf(E3);
    const { trace } = sow(before, 'a2');
    const { frames } = sowingFrames(before, trace);
    const afterCapture = frames[trace.findIndex(s => s.type === 'CAPTURE')];
    expect(afterCapture.board[18]).toBe(0);
    expect(afterCapture.board[26]).toBe(0);
    expect(afterCapture.board[10]).toBe(2);
    expect(afterCapture.hand).toEqual({ pit: 10, seeds: 5 });
    expect(afterCapture.captured).toEqual([18, 26]);
    // The hand goes back to the start of the lap to resume.
    const resume = frames[trace.findIndex(s => s.type === 'RESUME_AFTER')];
    expect(resume.hand).toEqual({ pit: 8, seeds: 5 });
  });

  it('the hand stays where it is on a skipped empty pit, then lands after it', () => {
    const before = boardOf('a2:3');
    const { trace } = sow(before, 'a2');
    const { frames } = sowingFrames(before, trace);
    const skip = trace.findIndex(s => s.type === 'SKIP_EMPTY');
    expect(frames[skip].hand).toEqual({ pit: 11, seeds: 1 });
    expect(frames[skip].board[11]).toBe(0);
    expect(frames[skip + 1].hand).toEqual({ pit: 12, seeds: 0 });
  });

  for (const ex of EXAMPLES) {
    it(`the last frame equals the server board for ${ex.name}, with an empty hand`, () => {
      const before = boardOf(ex.before);
      const { board, trace } = sow(before, ex.pit);
      const { frames } = sowingFrames(before, trace);
      expect(frames[frames.length - 1].board).toEqual(board);
      expect(frames[frames.length - 1].hand?.seeds).toBe(0);
    });
  }

  it('every intermediate frame keeps the 32 seeds of the move on the board or in the hand', () => {
    const before = boardOf('a2:2 c2:1 d2:1 c3:1 c4:2 d3:1 d4:1');
    const { trace } = sow(before, 'a2');
    const total = before.reduce((a, b) => a + b, 0);
    for (const frame of sowingFrames(before, trace).frames) {
      expect(frame.board.reduce((a, b) => a + b, 0) + (frame.hand?.seeds ?? 0)).toBe(total);
    }
  });

  it('times the frames like planSowing', () => {
    const before = boardOf('a2:17');
    const { trace } = sow(before, 'a2');
    const plan = planSowing(trace);
    const { frames, totalMs } = sowingFrames(before, trace);
    expect(totalMs).toBeCloseTo(plan.totalMs, 6);
    frames.forEach((f, i) => {
      expect(f.atMs).toBeCloseTo(plan.steps[i].atMs, 6);
      expect(f.durationMs).toBeCloseTo(plan.steps[i].durationMs, 6);
    });
  });

  it('a long sowing lasts what its steps last, with no cap', () => {
    const before = boardOf('a2:40 b2:40');
    const { trace } = sow(before, 'a2');
    expect(trace.filter(s => s.type === 'DROP').length).toBeGreaterThan(100);
    expect(sowingFrames(before, trace).totalMs).toBeCloseTo(planSowing(trace).totalMs, 6);
  });

  it('returns nothing when the trace cannot be replayed on the board', () => {
    const trace: MangolaStep[] = [{ type: 'PICK', pit: 9, count: 3 }];
    expect(sowingFrames(boardOf('a2:3'), trace)).toBeNull();
  });
});

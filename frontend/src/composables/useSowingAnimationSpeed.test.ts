import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ref } from 'vue';
import { useSowingAnimation } from './useSowingAnimation';
import type { AnimationSpeed } from '../animation';
import { mangolaState } from '../model/mangola/mangolaFixtures';
import { boardOf, sow } from '../model/mangola/sowingFixtures';
import { planSowing, SPEED_PROFILES } from '../model/mangola/trace';
import type { MangolaState } from '../model/mangola/MangolaState';
import { pitFromName } from '../model/mangola/geometry';

const BEFORE = 'a2:2 c2:1 c3:2 c4:3 h4:3';

/** The state after `seat` sowed `pit` from `before`. */
function played(before: string, pit: string, version: number, seat: number): MangolaState {
  const { board, trace } = sow(boardOf(before), pit);
  return mangolaState({ version, board, lastMove: { seat, pit: pitFromName(pit)!, trace } });
}
const start = (before: string, version = 1): MangolaState => mangolaState({ version, board: boardOf(before) });

describe('useSowingAnimation: speed, and the opponent move shown before the seeds leave', () => {
  const speed = ref<AnimationSpeed>('normal');
  const MY_SEAT = 1; // the moves below are played by seat 0: the opponent's
  const make = () => useSowingAnimation({ speed: () => speed.value, mySeat: () => MY_SEAT });

  beforeEach(() => {
    vi.useFakeTimers();
    speed.value = 'normal';
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it('Normal: the chosen pit is shown and the hand rests on it for 500 ms before any seed leaves', () => {
    const anim = make();
    anim.push(start(BEFORE));
    const next = played(BEFORE, 'a2', 2, 0);
    anim.push(next);

    const a2 = pitFromName('a2')!;
    expect(anim.animating.value).toBe(true);
    expect(anim.chosenPit.value).toBe(a2);
    // The board is still the one before the move and the hand holds the pit's seeds.
    expect(anim.displayBoard.value).toEqual(boardOf(BEFORE));
    expect(anim.hand.value).toEqual({ pit: a2, seeds: 2 });
    expect(anim.activePit.value).toBeNull();
    expect(anim.announced.value?.version).toBe(2);

    vi.advanceTimersByTime(499);
    expect(anim.chosenPit.value).toBe(a2);
    expect(anim.displayBoard.value).toEqual(boardOf(BEFORE));

    vi.advanceTimersByTime(1);
    expect(anim.chosenPit.value).toBeNull();
    expect(anim.displayBoard.value![a2]).toBe(0); // the pick: the seeds have left
    expect(anim.activePit.value).toBe(a2);
  });

  it('Normal: the whole move lasts the pause plus the sowing', () => {
    const anim = make();
    anim.push(start(BEFORE));
    const next = played(BEFORE, 'a2', 2, 0);
    anim.push(next);
    const total = SPEED_PROFILES.normal.leadInMs + planSowing(next.lastMove!.trace, 'normal').totalMs;
    // Each step's timer is rounded to a millisecond: allow a few ms either way.
    vi.advanceTimersByTime(total - 20);
    expect(anim.animating.value).toBe(true);
    vi.advanceTimersByTime(40);
    expect(anim.animating.value).toBe(false);
    expect(anim.displayBoard.value).toEqual(next.board);
    expect(anim.marks.value.start).toBe(pitFromName('a2'));
  });

  it('Fast: the pause is 250 ms and the sowing runs at the old rhythm', () => {
    speed.value = 'fast';
    const anim = make();
    anim.push(start(BEFORE));
    const next = played(BEFORE, 'a2', 2, 0);
    anim.push(next);
    expect(anim.chosenPit.value).toBe(pitFromName('a2'));
    vi.advanceTimersByTime(250);
    expect(anim.chosenPit.value).toBeNull();
    vi.advanceTimersByTime(planSowing(next.lastMove!.trace).totalMs);
    expect(anim.animating.value).toBe(false);
    expect(anim.displayBoard.value).toEqual(next.board);
  });

  it('Instant: the final board, the marks and the announcement come at once, with no pause', () => {
    speed.value = 'instant';
    const anim = make();
    anim.push(start(BEFORE));
    const next = played(BEFORE, 'a2', 2, 0);
    anim.push(next);
    expect(anim.animating.value).toBe(false);
    expect(anim.chosenPit.value).toBeNull();
    expect(anim.hand.value).toBeNull();
    expect(anim.displayBoard.value).toEqual(next.board);
    expect(anim.marks.value.start).toBe(pitFromName('a2'));
    expect(anim.announced.value?.version).toBe(2);
    expect(vi.getTimerCount()).toBe(0);
  });

  it('my own move has no pause: the hand picks up at once (I just chose the pit)', () => {
    const anim = make();
    anim.push(start(BEFORE));
    anim.push(played(BEFORE, 'a2', 2, MY_SEAT));
    expect(anim.chosenPit.value).toBeNull();
    expect(anim.activePit.value).toBe(pitFromName('a2'));
    expect(anim.displayBoard.value![pitFromName('a2')!]).toBe(0);
  });

  it('without a seat to compare, no move gets a pause', () => {
    const anim = useSowingAnimation({ speed: () => 'normal' });
    anim.push(start(BEFORE));
    anim.push(played(BEFORE, 'a2', 2, 0));
    expect(anim.chosenPit.value).toBeNull();
    expect(anim.activePit.value).toBe(pitFromName('a2'));
  });

  it('a state that fits no sowing is shown at once, with no pause', () => {
    const anim = make();
    anim.push(start('a2:3 h4:2'));
    anim.push(played('a2:5 h4:2', 'a2', 4, 0));
    expect(anim.animating.value).toBe(false);
    expect(anim.chosenPit.value).toBeNull();
  });

  it('Skip during the pause ends on the final board', () => {
    const anim = make();
    anim.push(start(BEFORE));
    const next = played(BEFORE, 'a2', 2, 0);
    anim.push(next);
    vi.advanceTimersByTime(100);
    anim.skip();
    expect(anim.animating.value).toBe(false);
    expect(anim.chosenPit.value).toBeNull();
    expect(anim.hand.value).toBeNull();
    expect(anim.displayBoard.value).toEqual(next.board);
    vi.advanceTimersByTime(10000);
    expect(anim.displayBoard.value).toEqual(next.board);
  });

  describe('changing the speed while a sowing is playing', () => {
    it('to Instant, the sowing in progress ends on its final board at once', () => {
      const anim = make();
      anim.push(start(BEFORE));
      const next = played(BEFORE, 'a2', 2, 0);
      anim.push(next);
      vi.advanceTimersByTime(800);
      expect(anim.animating.value).toBe(true);
      speed.value = 'instant';
      expect(anim.animating.value).toBe(false);
      expect(anim.displayBoard.value).toEqual(next.board);
      expect(anim.shownState.value).toBe(next);
      expect(anim.marks.value.start).toBe(pitFromName('a2'));
      expect(vi.getTimerCount()).toBe(0);
    });

    it('to Instant during the pause, too', () => {
      const anim = make();
      anim.push(start(BEFORE));
      const next = played(BEFORE, 'a2', 2, 0);
      anim.push(next);
      vi.advanceTimersByTime(100);
      speed.value = 'instant';
      expect(anim.animating.value).toBe(false);
      expect(anim.chosenPit.value).toBeNull();
      expect(anim.displayBoard.value).toEqual(next.board);
    });

    it('to Instant, a state waiting behind is shown at once too', () => {
      const anim = make();
      anim.push(start('a2:3 h4:3'));
      const first = played('a2:3 h4:3', 'a2', 2, 0);
      anim.push(first);
      // A reply that really follows `first` on the board.
      const { board, trace } = sow(first.board as number[], 'h4');
      const second = mangolaState({ version: 3, board, lastMove: { seat: 1, pit: pitFromName('h4')!, trace } });
      anim.push(second);
      vi.advanceTimersByTime(700);
      speed.value = 'instant';
      expect(anim.animating.value).toBe(false);
      expect(anim.shownState.value).toBe(second);
      expect(anim.displayBoard.value).toEqual(second.board);
    });

    it('from Normal to Fast, the rest of the sowing runs at the faster rhythm', () => {
      const anim = make();
      anim.push(start(BEFORE));
      const next = played(BEFORE, 'a2', 2, 0);
      anim.push(next);
      vi.advanceTimersByTime(500 + 300); // inside the sowing
      speed.value = 'fast';
      // Whatever is left cannot take longer than the Fast plan's whole duration.
      vi.advanceTimersByTime(planSowing(next.lastMove!.trace, 'fast').totalMs + 1);
      expect(anim.animating.value).toBe(false);
      expect(anim.displayBoard.value).toEqual(next.board);
    });

    it('from Fast to Normal during the pause, the pause becomes 500 ms again', () => {
      speed.value = 'fast';
      const anim = make();
      anim.push(start(BEFORE));
      anim.push(played(BEFORE, 'a2', 2, 0));
      speed.value = 'normal';
      vi.advanceTimersByTime(499);
      expect(anim.chosenPit.value).toBe(pitFromName('a2'));
      vi.advanceTimersByTime(1);
      expect(anim.chosenPit.value).toBeNull();
    });

    it('from Normal to Fast during the pause, the pause is cut to 250 ms', () => {
      const anim = make();
      anim.push(start(BEFORE));
      anim.push(played(BEFORE, 'a2', 2, 0));
      speed.value = 'fast';
      vi.advanceTimersByTime(250);
      expect(anim.chosenPit.value).toBeNull();
    });

    it('does nothing when nothing is playing', () => {
      const anim = make();
      const first = start(BEFORE);
      anim.push(first);
      speed.value = 'instant';
      speed.value = 'fast';
      expect(anim.animating.value).toBe(false);
      expect(anim.displayBoard.value).toEqual(first.board);
    });
  });
});

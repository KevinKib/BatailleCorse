import type { PresidentState, PresidentTrickPlay } from './PresidentState';
import type { PresidentLogEntry, PresidentTrickClosure } from './eventLog';

/** What the pile shows: the play on top, the closure it carries (a trick just closed) and the trick indicator's count. */
export interface PileView {
  play: PresidentTrickPlay | null;
  /** Set while the trick that the latest move closed is still the last thing on the table (until the next play). */
  closure: PresidentTrickClosure | null;
  /** How many cards the trick holds (the closed trick's, while it is shown). */
  count: number;
}

/**
 * The pile, from the state and the latest move. A trick in progress shows its top play. A trick the latest move
 * closed keeps its closing play on the pile, with the closure, until the next play: the state already shows the trick
 * empty, and the picture says why. Pure.
 */
export function pileView(game: PresidentState, latest: PresidentLogEntry | null): PileView {
  const plays = game.trick.plays;
  if (plays.length > 0) {
    return { play: plays[plays.length - 1], closure: null, count: plays.reduce((n, p) => n + p.cards.length, 0) };
  }
  const closure = latest?.closure ?? null;
  if (!closure || game.phase !== 'PLAYING' || latest!.round !== game.round) return { play: null, closure: null, count: 0 };
  return {
    play: closure.cards.length ? { seat: closure.closer, cards: closure.cards } : null,
    closure,
    count: closure.size,
  };
}

import type Card from '../Card';
import { scaleMovements } from '../../animation';
import type { PresidentAnchor, PresidentMovement } from './motion';
import type { PresidentExchangePair, PresidentState } from './PresidentState';
import { exchangeLegKey } from './exchangeBoard';
import { PRESIDENT_TEMPO } from './tempo';

/**
 * The cards of the exchange that fly between two seats, from two successive states (the server's exchange events
 * carry a count, never cards, so the states say which and when): at the deal of an exchange round the low title's best
 * cards fly to the high one; when a high title gives back, the returned cards fly the other way. The faces are shown
 * only inside my own pair (my `given` and `received`); the other pairs fly card backs. Built in chunk 1, played by the
 * exchange screen in chunk 2. Pure.
 */
export function planExchangeFlights(
  before: PresidentState | null, after: PresidentState, mySeat: number, factor = 1,
): PresidentMovement[] {
  // The pairs stay listed all round (the server leaves the exchange as soon as the last one is done), so the phase
  // says nothing: a new round says "dealt", a pair newly done says "given back".
  if (!before || after.exchange.length === 0) return [];
  const dealt = before.round !== after.round;
  // each pair is planned from its own start (0), then the pairs are laid one after the other
  const groups: PresidentMovement[][] = [];
  for (const pair of after.exchange) {
    const group: PresidentMovement[] = [];
    if (dealt) {
      group.push(...flights(pair.low, pair.high, pair, faces(after, mySeat, pair, 'taken'), mySeat, group.length));
      // giveBack=lowest: the returns are made at the deal, right behind the first cards
      if (pair.done) {
        const wait = PRESIDENT_TEMPO.opponent.flightMs;
        group.push(...flights(pair.high, pair.low, pair, faces(after, mySeat, pair, 'returned'), mySeat, group.length, wait));
      }
    } else if (pair.done && !before.exchange.some(p => p.high === pair.high && p.low === pair.low && p.done)) {
      group.push(...flights(pair.high, pair.low, pair, faces(after, mySeat, pair, 'returned'), mySeat, 0));
    }
    if (group.length > 0) groups.push(group);
  }
  return scaleMovements(sequence(groups), factor);
}

/** The moment the last card of a group lands, from the group's own start. */
const groupEnd = (group: PresidentMovement[]) => Math.max(...group.map(m => m.delayMs + m.durationMs));

/**
 * The groups one after the other: each starts once the one before it has landed, plus the gap. When the whole would
 * last longer than `exchange.maxSpanMs`, the starts are scaled down by one same ratio so that it ends at the cap (the
 * pairs then overlap); a single pair longer than the cap cannot be shortened that way and is left as it is.
 */
function sequence(groups: PresidentMovement[][]): PresidentMovement[] {
  const { pairGapMs, maxSpanMs } = PRESIDENT_TEMPO.exchange;
  const starts: number[] = [];
  let next = 0;
  for (const group of groups) {
    starts.push(next);
    next += groupEnd(group) + pairGapMs;
  }
  // the largest ratio of the starts that keeps every group inside the cap (1 when none exceeds it)
  const ratio = Math.min(1, ...groups.map((group, k) =>
    starts[k] === 0 ? 1 : Math.max(0, maxSpanMs - groupEnd(group)) / starts[k]));
  return groups.flatMap((group, k) => {
    const start = Math.round(starts[k] * ratio);
    return group.map(m => ({ ...m, delayMs: m.delayMs + start }));
  });
}

/**
 * When each leg of the exchange lands (its last card, before the hold after), in ms from the start of its plan, by
 * `exchangeLegKey`: the low title's best cards go up, the high title's cards come back. The moves are the plan's own
 * (already scaled by the speed), so the landings are too. Pure.
 */
export function exchangeLandings(after: PresidentState, moves: PresidentMovement[]): Record<string, number> {
  const landings: Record<string, number> = {};
  for (const pair of after.exchange) {
    for (const [leg, from, to] of [['best', pair.low, pair.high], ['back', pair.high, pair.low]] as const) {
      const legMoves = moves.filter(m => m.from.kind === 'seat' && m.from.seat === from && m.to.kind === 'seat' && m.to.seat === to);
      if (legMoves.length > 0) landings[exchangeLegKey(pair, leg)] = Math.max(...legMoves.map(m => m.delayMs + m.durationMs));
    }
  }
  return landings;
}

/** The faces I may see:the low title's best cards are the low seat's `given` and the high seat's `received`. */
function faces(state: PresidentState, mySeat: number, pair: PresidentExchangePair, leg: 'taken' | 'returned'): Card[] {
  if (mySeat === pair.low) return leg === 'taken' ? state.given : state.received;
  if (mySeat === pair.high) return leg === 'taken' ? state.received : state.given;
  return [];
}

function flights(
  from: number, to: number, pair: PresidentExchangePair, seen: Card[], mySeat: number, offset: number, waitMs = 0,
): PresidentMovement[] {
  const byOpponent = from !== mySeat;
  const leg = byOpponent ? PRESIDENT_TEMPO.opponent : PRESIDENT_TEMPO.own;
  const source: PresidentAnchor = { kind: 'seat', seat: from };
  const target: PresidentAnchor = { kind: 'seat', seat: to };
  return Array.from({ length: pair.count }, (_, i) => ({
    kind: 'travel' as const, from: source, to: target, card: seen[i] ?? null, flip: false, turnDown: false,
    actor: from, byOpponent, delayMs: waitMs + leg.holdBeforeMs + (offset + i) * PRESIDENT_TEMPO.cardStaggerMs,
    durationMs: leg.flightMs, holdAfterMs: leg.holdAfterMs, marks: [source, target],
  }));
}

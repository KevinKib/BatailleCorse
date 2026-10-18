import type Card from '../Card';
import { scaleMovements } from '../table/motion';
import type { PresidentAnchor, PresidentMovement } from './motion';
import type { PresidentExchangePair, PresidentState } from './PresidentState';
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
  if (!before || after.phase !== 'EXCHANGE') return [];
  const moves: PresidentMovement[] = [];
  const dealt = before.phase !== 'EXCHANGE' || before.round !== after.round;
  for (const pair of after.exchange) {
    if (dealt) {
      moves.push(...flights(pair.low, pair.high, pair, faces(after, mySeat, pair, 'taken'), mySeat, moves.length));
    } else if (pair.done && !before.exchange.some(p => p.high === pair.high && p.low === pair.low && p.done)) {
      moves.push(...flights(pair.high, pair.low, pair, faces(after, mySeat, pair, 'returned'), mySeat, moves.length));
    }
  }
  return scaleMovements(moves, factor);
}

/** The faces I may see: the low title's best cards are the low seat's `given` and the high seat's `received`. */
function faces(state: PresidentState, mySeat: number, pair: PresidentExchangePair, leg: 'taken' | 'returned'): Card[] {
  if (mySeat === pair.low) return leg === 'taken' ? state.given : state.received;
  if (mySeat === pair.high) return leg === 'taken' ? state.received : state.given;
  return [];
}

function flights(
  from: number, to: number, pair: PresidentExchangePair, seen: Card[], mySeat: number, offset: number,
): PresidentMovement[] {
  const byOpponent = from !== mySeat;
  const leg = byOpponent ? PRESIDENT_TEMPO.opponent : PRESIDENT_TEMPO.own;
  const source: PresidentAnchor = { kind: 'seat', seat: from };
  const target: PresidentAnchor = { kind: 'seat', seat: to };
  return Array.from({ length: pair.count }, (_, i) => ({
    kind: 'travel' as const, from: source, to: target, card: seen[i] ?? null, flip: false, turnDown: false,
    actor: from, byOpponent, delayMs: leg.holdBeforeMs + (offset + i) * PRESIDENT_TEMPO.cardStaggerMs,
    durationMs: leg.flightMs, holdAfterMs: leg.holdAfterMs, marks: [source, target],
  }));
}

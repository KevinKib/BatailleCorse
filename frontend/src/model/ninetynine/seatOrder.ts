import { opponentsInPlayOrder } from '../table/opponentOrder';
import type { NinetyNineDirection, NinetyNinePlayer } from './NinetyNineState';

/**
 * The opponents in the order they play after me (seat order, wrapping), eliminated ones included: they stay at the
 * table with their place. The ring of the table follows this order.
 */
export function opponentsAfterMe(players: NinetyNinePlayer[], mySeat: number): NinetyNinePlayer[] {
  const ordered = opponentsInPlayOrder(players.map(p => ({ id: String(p.seat), player: p })), mySeat);
  return ordered.map(o => o.player);
}

/** Every seat of the table, me first, then the others in seat order: the order of the plates of the ring. */
export function seatsAtTable(players: NinetyNinePlayer[], mySeat: number): NinetyNinePlayer[] {
  const me = players.find(p => p.seat === mySeat);
  const others = opponentsAfterMe(players, mySeat).filter(p => p.seat !== mySeat);
  return me ? [me, ...others] : others;
}

/**
 * The seats in the order play reaches them, me first: the seat order, or its reverse after a 4. The rail of seats reads
 * this way; the ring keeps the seat order and shows the direction with arrows.
 */
export function seatsInTurnOrder(
  players: NinetyNinePlayer[], mySeat: number, direction: NinetyNineDirection,
): NinetyNinePlayer[] {
  const [first, ...rest] = seatsAtTable(players, mySeat);
  if (!first) return [];
  return direction === 'COUNTER_CLOCKWISE' ? [first, ...rest.reverse()] : [first, ...rest];
}

import { opponentsInPlayOrder } from '../table/opponentOrder';
import type { NinetyNinePlayer } from './NinetyNineState';

/**
 * The opponents in the order they play after me (seat order, wrapping), eliminated ones included: they stay at the
 * table with their place. The ring of the table follows this order.
 */
export function opponentsAfterMe(players: NinetyNinePlayer[], mySeat: number): NinetyNinePlayer[] {
  const ordered = opponentsInPlayOrder(players.map(p => ({ id: String(p.seat), player: p })), mySeat);
  return ordered.map(o => o.player);
}

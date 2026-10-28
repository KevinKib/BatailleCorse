import { opponentsInPlayOrder } from '../table/opponentOrder';
import type { KiloDeMerdePlayer } from './KiloDeMerdeState';

const activeOf = (players: KiloDeMerdePlayer[]) => players.filter(p => p.active);

/**
 * Every seat still at the table in the order of the ring: me first, then my left neighbour (who receives my card),
 * on to my right neighbour (who gives me one). A seat that forfeited has left the table. When I am not seated the
 * seat order is kept.
 */
export function seatsAtTable(players: KiloDeMerdePlayer[], mySeat: number): KiloDeMerdePlayer[] {
  const active = activeOf(players);
  const me = active.find(p => p.seat === mySeat);
  const after = opponentsInPlayOrder(active.map(p => ({ id: String(p.seat), player: p })), mySeat).map(o => o.player);
  return me ? [me, ...after.filter(p => p.seat !== mySeat)] : after;
}

/** The seat that receives the card of `seat` at each step: the next active seat, wrapping. Null when `seat` is out. */
export function leftNeighbour(players: KiloDeMerdePlayer[], seat: number): number | null {
  const ring = seatsAtTable(players, seat);
  return ring[0]?.seat === seat ? ring[1 % ring.length].seat : null;
}

/** The seat that gives `seat` its card at each step: the previous active seat, wrapping. Null when `seat` is out. */
export function rightNeighbour(players: KiloDeMerdePlayer[], seat: number): number | null {
  const ring = seatsAtTable(players, seat);
  return ring[0]?.seat === seat ? ring[ring.length - 1].seat : null;
}

import { opponentsInPlayOrder } from '../table/opponentOrder';
import type { BlackjackSeat } from './BlackjackState';

/**
 * The other seats in the order they play after me (ascending seat, wrapping): the order of the opponent plates.
 * When I am not seated the seat order is kept.
 */
export function opponentsOf(players: BlackjackSeat[], mySeat: number): BlackjackSeat[] {
  return opponentsInPlayOrder(players.map(p => ({ id: String(p.seat), player: p })), mySeat).map(o => o.player);
}

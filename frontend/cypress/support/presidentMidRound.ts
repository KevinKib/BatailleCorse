// A President round played far enough to show everything a seat can say (passes, a cut, a four of a kind, a place,
// "Out on a 2"), for the specs that measure the table in the middle of a round. Fixed deal, scripted guests; the
// browser is seat 0 and leads the king of hearts. Seat 1 (Bob) is the one who acts: his hand is chosen, every other
// card is a filler nobody plays (the others only pass).
import { canonicalRank } from '../../src/model/president/ranks';
import { awaitState, type Guest } from './presidentRoom';

const RANKS = ['A', '2', '3', '4', '5', '6', '7', '8', '9', '10', 'J', 'Q', 'K'];
const DECK = RANKS.flatMap((rank) => ['H', 'D', 'C', 'S'].map((suit) => `${rank}${suit}`));

/** Bob's hand: a 2 to cut with, a four of a kind, a single to lead, and a 2 to finish on. */
export const BOB_HAND = ['2S', '4H', '4D', '4C', '4S', '5H', '2H'];

/**
 * A full deal for `players` seats (card k goes to seat k % players): the browser (seat 0) holds the king of hearts,
 * Bob (seat 1) holds `BOB_HAND` as the first cards he is dealt, and the rest of the deck fills the other places.
 */
export function midRoundDeal(players: number): string[] {
  const deal: string[] = new Array(52).fill('');
  deal[0] = 'KH';
  BOB_HAND.forEach((code, i) => { deal[1 + i * players] = code; });
  const used = new Set(['KH', ...BOB_HAND]);
  const rest = DECK.filter((code) => !used.has(code));
  for (let k = 0; k < 52; k++) if (deal[k] === '') deal[k] = rest.shift()!;
  return deal;
}

/** The guest leads the first set it is offered, as soon as it is its turn (it then holds the lead, whatever its cards). */
export function scriptedLead(player: Guest) {
  awaitState(player, `seat ${player.seat}: my turn to lead`, (s) =>
    s.phase === 'PLAYING' && s.currentSeat === player.seat && s.availableActions.includes('PLAY') && s.legalPlays.length > 0);
  cy.then(() => {
    const state = player.lastState();
    const pattern = state.legalPlays[0];
    const cards = state.hand.filter((c: any) => canonicalRank(pattern.rank) === c.rank).slice(0, pattern.count);
    player.publish('/app/president/play', { cards });
  });
}

import type Card from '../Card';
import type {
  BlackjackDealer, BlackjackHand, BlackjackPhase, BlackjackSeat, BlackjackState,
} from './BlackjackState';

/**
 * What the table has told so far, while its pictures are still playing. The server answers a whole turn at once (a
 * deal, the dealer's draws and the settlement arrive in one go); the table shows it in order. The `Hold` says how far
 * the table is: the cards that have landed, whether the hole card is turned, whether the bets and the results are
 * told. `shownState` reads the latest state through it. Purely visual: the buttons and the rules read the live state.
 */
export interface Hold {
  mySeat: number;
  phase: BlackjackPhase;
  currentSeat: number;
  activeHandIndex: number;
  handNumber: number;
  /** Cards that have landed, by place (`handKey`, `DEALER_KEY`). A place without an entry is not on the table yet. */
  counts: Record<string, number>;
  /** The dealer's second card is still face down, whatever the state says. */
  holeFaceDown: boolean;
  /** The other seats' bets are still hidden (they are told together, at the deal). */
  betsSealed: boolean;
  /** The bets of the hand by seat, kept while the results are told (the state has already moved on to the next betting). */
  bets: Record<number, number>;
  /** The nets of the hands are not told yet. */
  resultsHidden: boolean;
  /** Stacks as the table told them (by seat); the live stack for a seat without an entry. */
  stacks: Record<number, number> | null;
}

export const DEALER_KEY = 'dealer';
export const handKey = (seat: number, handIndex: number): string => `s${seat}h${handIndex}`;

/** The dealer's places in order: the up card, the hole card, then the cards drawn. */
export const DEALER_HOLE_INDEX = 1;

/** How many places the dealer holds in the state (the hole card counts, face down or not). */
export function dealerSlots(dealer: BlackjackDealer): number {
  return dealer.cards.length + (dealer.holeHidden ? 1 : 0);
}

/** A hold that tells everything the state says (the starting point of a hold: nothing is hidden). */
export function holdFromState(state: BlackjackState, mySeat = 0): Hold {
  const counts: Record<string, number> = { [DEALER_KEY]: dealerSlots(state.dealer) };
  for (const p of state.players) p.hands.forEach((h, i) => { counts[handKey(p.seat, i)] = h.cards.length; });
  return {
    mySeat,
    phase: state.phase,
    currentSeat: state.currentSeat,
    activeHandIndex: state.activeHandIndex,
    handNumber: state.handNumber,
    counts,
    holeFaceDown: state.dealer.holeHidden,
    betsSealed: false,
    bets: Object.fromEntries(state.players.filter(p => p.bet !== null).map(p => [p.seat, p.bet as number])),
    resultsHidden: false,
    stacks: null,
  };
}

function shownHand(hand: BlackjackHand, count: number, hold: Hold): BlackjackHand {
  const pending = count < hand.cards.length;
  const out: BlackjackHand = { ...hand, cards: hand.cards.slice(0, count) };
  if (pending) {
    out.pending = true;
    out.status = 'WAITING';
  }
  if (pending || hold.resultsHidden) delete out.net;
  return out;
}

function shownSeat(player: BlackjackSeat, hold: Hold): BlackjackSeat {
  const hands: BlackjackHand[] = [];
  player.hands.forEach((hand, i) => {
    const count = hold.counts[handKey(player.seat, i)];
    if (count !== undefined) hands.push(shownHand(hand, Math.min(count, hand.cards.length), hold));
  });
  const seat: BlackjackSeat = { ...player, hands };
  if (hold.betsSealed && player.seat !== hold.mySeat) seat.bet = null;
  else if (seat.bet === null && hold.bets[player.seat] !== undefined) {
    seat.bet = hold.bets[player.seat];
    seat.betPlaced = true;
  }
  const stack = hold.stacks?.[player.seat];
  if (stack !== undefined) seat.stack = stack;
  return seat;
}

function shownDealer(dealer: BlackjackDealer, hold: Hold): BlackjackDealer {
  const slots = dealerSlots(dealer);
  const count = Math.min(hold.counts[DEALER_KEY] ?? 0, slots);
  const holeCovered = hold.holeFaceDown && count > DEALER_HOLE_INDEX;
  // The places in order, the hole card as a gap (null) while it is face down, and the cards the state lists.
  const places: (Card | null)[] = dealer.holeHidden
    ? [dealer.cards[0] ?? null, null, ...dealer.cards.slice(1)]
    : dealer.cards.map((c, i) => (i === DEALER_HOLE_INDEX && holeCovered ? null : c));
  const visible = places.slice(0, count);
  const cards = visible.filter((c): c is Card => c !== null);
  const holeHidden = visible.some(c => c === null);
  const complete = count >= slots && !(dealer.holeHidden === false && holeCovered);
  const out: BlackjackDealer = { ...dealer, cards, holeHidden };
  if (!complete) {
    out.pending = true;
    out.blackjack = false;
    out.bust = false;
    // The total of a hole card still face down is the server's own number only when it is face down for the server too.
    if (!(dealer.holeHidden && count >= 1)) out.total = 0;
  }
  return out;
}

/** The latest state as the table has told it so far. No hold: the state itself. */
export function shownState(live: BlackjackState, hold: Hold | null): BlackjackState {
  if (!hold) return live;
  return {
    ...live,
    phase: hold.phase,
    currentSeat: hold.currentSeat,
    activeHandIndex: hold.activeHandIndex,
    handNumber: hold.handNumber,
    dealer: shownDealer(live.dealer, hold),
    players: live.players.map(p => shownSeat(p, hold)),
    availableActions: [],
    betLimits: null,
    // The summary of the previous hand is for the quiet betting that follows a settled hand, not for a table being told.
    lastHand: null,
  };
}

/** The total net of a seat's hands once every one of them is settled, else null. */
export function roundNetOf(player: BlackjackSeat): number | null {
  if (player.hands.length === 0 || player.hands.some(h => h.net === undefined || h.pending)) return null;
  return player.hands.reduce((sum, h) => sum + (h.net ?? 0), 0);
}

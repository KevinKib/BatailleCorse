import type { KoboAction, KoboPlayer, KoboState } from './KoboState';

/** The local selection a multi-click action builds up. Never sent as is: it only decides the next click. */
export type KoboPending =
  | { kind: 'none' }
  | { kind: 'match-armed' }
  /** A card of another player was picked for a match; the next click picks which of my cards to give. */
  | { kind: 'match-give'; target: { seat: number; slot: number }; expectedTopRank: string; expectedRevision: number }
  | { kind: 'blind-own'; ownSlot: number };

/** A command a click sends, with the exact fields of the matching `/app/kobo/...` endpoint. */
export type KoboSlotCommand =
  | { type: 'swapDrawn'; slot: number }
  | { type: 'peek'; seat: number; slot: number }
  | { type: 'kingSwap'; slot: number }
  | { type: 'blindSwap'; ownSlot: number; seat: number; slot: number }
  | {
    type: 'match'; seat: number; slot: number; giveSlot?: number;
    expectedTopRank: string; expectedRevision: number;
  };

/** What a click on a slot does: send a command, or move the local selection forward. */
export type KoboSlotIntent =
  | { kind: 'send'; command: KoboSlotCommand }
  | { kind: 'pend'; pending: KoboPending };

/** The one-line instruction the screen shows (a key, the texts live in the locales). */
export type KoboPromptKey =
  | 'swapDrawn' | 'peekOwn' | 'peekOther' | 'peekKing' | 'blindOwn' | 'blindTarget' | 'kingSwap'
  | 'matchPick' | 'matchGive';

export interface KoboInteraction {
  prompt: KoboPromptKey | null;
  /** Clickable slots only, by `slotKey`; a slot absent from the map is not clickable. */
  intents: Record<string, KoboSlotIntent>;
  /** The piles a click acts on: the draw pile draws, the discard pile discards the card I hold. */
  piles: { draw: boolean; discard: boolean };
  /** True when `intents` are one-click matches (no action of the phase uses a click on a slot). */
  directMatch: boolean;
}

export interface KoboInteractionOptions {
  /** False holds the one-click match back (right after a command of mine, against a double tap). Default true. */
  directMatch?: boolean;
}

export function slotKey(seat: number, slot: number): string {
  return `${seat}:${slot}`;
}

const NO_PILES = { draw: false, discard: false };
/** No game, no seat: nothing to click. */
export const NOTHING: KoboInteraction = { prompt: null, intents: {}, piles: NO_PILES, directMatch: false };
/** Phases where nobody may match: the cards are being learnt, given or counted. */
const NO_MATCH_PHASES: KoboState['phase'][] = ['MEMORISING', 'GIFT', 'ROUND_OVER', 'FINISHED'];
/** My own turn leaves the click to a match only between turn actions: before the draw and after the discard. */
const FREE_OWN_PHASES: KoboState['phase'][] = ['DRAW', 'TURN_END'];

function occupiedSlots(player: KoboPlayer): number[] {
  return player.slots.filter(s => s.occupied).map(s => s.slot);
}

function intentsFor(
  players: KoboPlayer[],
  make: (player: KoboPlayer, slot: number) => KoboSlotIntent,
): Record<string, KoboSlotIntent> {
  const intents: Record<string, KoboSlotIntent> = {};
  for (const player of players) {
    for (const slot of occupiedSlots(player)) intents[slotKey(player.seat, slot)] = make(player, slot);
  }
  return intents;
}

/**
 * What a click on a card means, from the viewer's `availableActions`, the pending power and the local
 * selection. It computes no rule: the server lists the actions, and re-checks every command. A slot that the
 * listing does not call for is simply not clickable, and an empty slot never is.
 */
export function koboInteraction(
  state: KoboState, mySeat: number, pending: KoboPending, options: KoboInteractionOptions = {},
): KoboInteraction {
  const base = slotInteraction(state, mySeat, pending);
  const me = state.players.find(p => p.seat === mySeat);
  if (!me) return base;
  const piles = {
    draw: state.availableActions.includes('DRAW'),
    discard: state.availableActions.includes('DISCARD_DRAWN'),
  };
  if (options.directMatch !== false && allowsDirectMatch(state, mySeat, pending, base)) {
    return { ...base, piles, directMatch: true, intents: matchIntents(state, me, state.players) };
  }
  return { ...base, piles };
}

/**
 * Whether a click on an occupied slot may be a match at once. The rule is "nothing else wants the click": no local
 * selection, no slot-picking action listed (swap, peek, blind swap, king swap), no card in my hand, and on my
 * own turn no pending power (a power is still running even when the listing holds only a skip). Another
 * player's turn never gets in the way: the race is the point of the match.
 */
function allowsDirectMatch(
  state: KoboState, mySeat: number, pending: KoboPending, base: KoboInteraction,
): boolean {
  if (pending.kind !== 'none' || base.prompt !== null || Object.keys(base.intents).length > 0) return false;
  if (!state.availableActions.includes('MATCH_DISCARD') || !state.discardTop || state.revealed) return false;
  if (NO_MATCH_PHASES.includes(state.phase) || state.drawnCard !== null) return false;
  if (state.currentSeat !== mySeat) return true;
  return state.pendingPower === 'NONE' && FREE_OWN_PHASES.includes(state.phase);
}

/** Every occupied slot of `players` as a match at the rank and revision on screen (a card of another player asks for mine). */
function matchIntents(state: KoboState, me: KoboPlayer, players: KoboPlayer[]): Record<string, KoboSlotIntent> {
  const top = state.discardTop;
  if (!top) return {};
  const expectedTopRank = top.rank;
  const hasCardToGive = occupiedSlots(me).length > 0;
  return intentsFor(players, (player, slot) => {
    const expectedRevision = player.slots.find(s => s.slot === slot)!.revision;
    if (player.seat !== me.seat && hasCardToGive) {
      return {
        kind: 'pend',
        pending: { kind: 'match-give', target: { seat: player.seat, slot }, expectedTopRank, expectedRevision },
      };
    }
    return { kind: 'send', command: { type: 'match', seat: player.seat, slot, expectedTopRank, expectedRevision } };
  });
}

function slotInteraction(state: KoboState, mySeat: number, pending: KoboPending): KoboInteraction {
  const me = state.players.find(p => p.seat === mySeat);
  if (!me) return NOTHING;
  const others = state.players.filter(p => p.seat !== mySeat);
  const can = (action: KoboAction) => state.availableActions.includes(action);
  const only = (players: KoboPlayer[], make: (player: KoboPlayer, slot: number) => KoboSlotIntent) =>
    intentsFor(players, make);

  // The matching discard is an explicit mode and wins over the turn's own actions.
  if ((pending.kind === 'match-armed' || pending.kind === 'match-give') && can('MATCH_DISCARD')) {
    if (pending.kind === 'match-give') {
      const { target, expectedTopRank, expectedRevision } = pending;
      return {
        ...NOTHING,
        prompt: 'matchGive',
        intents: only([me], (_, giveSlot) => ({
          kind: 'send',
          command: { type: 'match', seat: target.seat, slot: target.slot, giveSlot, expectedTopRank, expectedRevision },
        })),
      };
    }
    if (!state.discardTop) return NOTHING;
    return { ...NOTHING, prompt: 'matchPick', intents: matchIntents(state, me, state.players) };
  }

  if (can('SWAP_DRAWN')) {
    return {
      ...NOTHING,
      prompt: 'swapDrawn',
      intents: only([me], (_, slot) => ({ kind: 'send', command: { type: 'swapDrawn', slot } })),
    };
  }

  if (can('PEEK')) {
    const send = (player: KoboPlayer, slot: number): KoboSlotIntent =>
      ({ kind: 'send', command: { type: 'peek', seat: player.seat, slot } });
    switch (state.pendingPower) {
      case 'PEEK_OWN': return { ...NOTHING, prompt: 'peekOwn', intents: only([me], send) };
      case 'PEEK_OTHER': return { ...NOTHING, prompt: 'peekOther', intents: only(others, send) };
      case 'KING': return { ...NOTHING, prompt: 'peekKing', intents: only(others, send) };
      default: return NOTHING;
    }
  }

  if (can('BLIND_SWAP')) {
    if (pending.kind === 'blind-own') {
      const ownSlot = pending.ownSlot;
      return {
        ...NOTHING,
        prompt: 'blindTarget',
        intents: only(others, (player, slot) => ({
          kind: 'send', command: { type: 'blindSwap', ownSlot, seat: player.seat, slot },
        })),
      };
    }
    return {
      ...NOTHING,
      prompt: 'blindOwn',
      intents: only([me], (_, ownSlot) => ({ kind: 'pend', pending: { kind: 'blind-own', ownSlot } })),
    };
  }

  if (can('KING_SWAP')) {
    return {
      ...NOTHING,
      prompt: 'kingSwap',
      intents: only([me], (_, slot) => ({ kind: 'send', command: { type: 'kingSwap', slot } })),
    };
  }

  return NOTHING;
}

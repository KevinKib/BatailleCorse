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
}

export function slotKey(seat: number, slot: number): string {
  return `${seat}:${slot}`;
}

const NOTHING: KoboInteraction = { prompt: null, intents: {} };

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
export function koboInteraction(state: KoboState, mySeat: number, pending: KoboPending): KoboInteraction {
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
        prompt: 'matchGive',
        intents: only([me], (_, giveSlot) => ({
          kind: 'send',
          command: { type: 'match', seat: target.seat, slot: target.slot, giveSlot, expectedTopRank, expectedRevision },
        })),
      };
    }
    const top = state.discardTop;
    if (!top) return NOTHING;
    const expectedTopRank = top.rank;
    const hasCardToGive = occupiedSlots(me).length > 0;
    return {
      prompt: 'matchPick',
      intents: only(state.players, (player, slot) => {
        const expectedRevision = player.slots.find(s => s.slot === slot)!.revision;
        if (player.seat !== mySeat && hasCardToGive) {
          return {
            kind: 'pend',
            pending: { kind: 'match-give', target: { seat: player.seat, slot }, expectedTopRank, expectedRevision },
          };
        }
        return { kind: 'send', command: { type: 'match', seat: player.seat, slot, expectedTopRank, expectedRevision } };
      }),
    };
  }

  if (can('SWAP_DRAWN')) {
    return {
      prompt: 'swapDrawn',
      intents: only([me], (_, slot) => ({ kind: 'send', command: { type: 'swapDrawn', slot } })),
    };
  }

  if (can('PEEK')) {
    const send = (player: KoboPlayer, slot: number): KoboSlotIntent =>
      ({ kind: 'send', command: { type: 'peek', seat: player.seat, slot } });
    switch (state.pendingPower) {
      case 'PEEK_OWN': return { prompt: 'peekOwn', intents: only([me], send) };
      case 'PEEK_OTHER': return { prompt: 'peekOther', intents: only(others, send) };
      case 'KING': return { prompt: 'peekKing', intents: only(others, send) };
      default: return NOTHING;
    }
  }

  if (can('BLIND_SWAP')) {
    if (pending.kind === 'blind-own') {
      const ownSlot = pending.ownSlot;
      return {
        prompt: 'blindTarget',
        intents: only(others, (player, slot) => ({
          kind: 'send', command: { type: 'blindSwap', ownSlot, seat: player.seat, slot },
        })),
      };
    }
    return {
      prompt: 'blindOwn',
      intents: only([me], (_, ownSlot) => ({ kind: 'pend', pending: { kind: 'blind-own', ownSlot } })),
    };
  }

  if (can('KING_SWAP')) {
    return {
      prompt: 'kingSwap',
      intents: only([me], (_, slot) => ({ kind: 'send', command: { type: 'kingSwap', slot } })),
    };
  }

  return NOTHING;
}

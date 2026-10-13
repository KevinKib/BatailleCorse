import { describe, it, expect } from 'vitest';
import { koboInteraction, slotKey, type KoboPending } from './interaction';
import { buildKoboState, buildKoboPlayer, buildKoboSlot, buildKoboCard } from '../fixtures/kobo';
import type { KoboAction, KoboState } from './KoboState';

const NONE: KoboPending = { kind: 'none' };

// Seat 0 is me, seats 1 and 2 are opponents; each has slots 0..3 unless a test says otherwise.
function table(overrides: Partial<KoboState> = {}): KoboState {
  return buildKoboState({
    players: [buildKoboPlayer(0, { current: true }), buildKoboPlayer(1), buildKoboPlayer(2)],
    ...overrides,
  });
}
function listing(actions: KoboAction[], overrides: Partial<KoboState> = {}): KoboState {
  return table({ availableActions: actions, ...overrides });
}
function clickable(state: KoboState, pending: KoboPending = NONE): string[] {
  return Object.keys(koboInteraction(state, 0, pending).intents).sort();
}
const own = [0, 1, 2, 3].map(s => slotKey(0, s));
const seat1 = [0, 1, 2, 3].map(s => slotKey(1, s));
const seat2 = [0, 1, 2, 3].map(s => slotKey(2, s));

describe('koboInteraction', () => {
  describe('swapping the drawn card', () => {
    const state = listing(['SWAP_DRAWN', 'DISCARD_DRAWN'], { phase: 'DECISION', hasDrawn: true, drawnCard: buildKoboCard('5') });

    it('makes my occupied slots clickable and a click sends swapDrawn', () => {
      expect(clickable(state)).toEqual([...own].sort());
      const interaction = koboInteraction(state, 0, NONE);
      expect(interaction.intents[slotKey(0, 2)]).toEqual({ kind: 'send', command: { type: 'swapDrawn', slot: 2 } });
      expect(interaction.prompt).toBe('swapDrawn');
    });

    it('never makes an empty slot clickable', () => {
      const withHole = listing(['SWAP_DRAWN'], {
        players: [
          buildKoboPlayer(0, { slots: [buildKoboSlot(0), buildKoboSlot(1, { occupied: false }), buildKoboSlot(2)] }),
          buildKoboPlayer(1),
        ],
      });
      expect(clickable(withHole)).toEqual([slotKey(0, 0), slotKey(0, 2)].sort());
    });
  });

  describe('powers', () => {
    it('PEEK with PEEK_OWN: my occupied slots, a click peeks at my seat', () => {
      const state = listing(['PEEK', 'SKIP_POWER'], { phase: 'POWER', pendingPower: 'PEEK_OWN' });
      expect(clickable(state)).toEqual([...own].sort());
      const interaction = koboInteraction(state, 0, NONE);
      expect(interaction.intents[slotKey(0, 1)]).toEqual({ kind: 'send', command: { type: 'peek', seat: 0, slot: 1 } });
      expect(interaction.prompt).toBe('peekOwn');
    });

    it('PEEK with PEEK_OTHER: the others occupied slots, a click peeks at their seat', () => {
      const state = listing(['PEEK', 'SKIP_POWER'], { phase: 'POWER', pendingPower: 'PEEK_OTHER' });
      expect(clickable(state)).toEqual([...seat1, ...seat2].sort());
      const interaction = koboInteraction(state, 0, NONE);
      expect(interaction.intents[slotKey(2, 3)]).toEqual({ kind: 'send', command: { type: 'peek', seat: 2, slot: 3 } });
      expect(interaction.prompt).toBe('peekOther');
    });

    it('PEEK with KING: the others occupied slots', () => {
      const state = listing(['PEEK', 'SKIP_POWER'], { phase: 'POWER', pendingPower: 'KING' });
      expect(clickable(state)).toEqual([...seat1, ...seat2].sort());
      expect(koboInteraction(state, 0, NONE).prompt).toBe('peekKing');
    });

    it('PEEK listed but no pending power: nothing is clickable (the client invents no rule)', () => {
      expect(clickable(listing(['PEEK'], { pendingPower: 'NONE' }))).toEqual([]);
    });

    it('BLIND_SWAP with nothing pending: my slots, a click moves on to blind-own', () => {
      const state = listing(['BLIND_SWAP', 'SKIP_POWER'], { phase: 'POWER', pendingPower: 'BLIND_SWAP' });
      expect(clickable(state)).toEqual([...own].sort());
      const interaction = koboInteraction(state, 0, NONE);
      expect(interaction.intents[slotKey(0, 3)]).toEqual({ kind: 'pend', pending: { kind: 'blind-own', ownSlot: 3 } });
      expect(interaction.prompt).toBe('blindOwn');
    });

    it('BLIND_SWAP with blind-own: the others slots, a click sends blindSwap', () => {
      const state = listing(['BLIND_SWAP'], { phase: 'POWER', pendingPower: 'BLIND_SWAP' });
      const pending: KoboPending = { kind: 'blind-own', ownSlot: 3 };
      expect(clickable(state, pending)).toEqual([...seat1, ...seat2].sort());
      const interaction = koboInteraction(state, 0, pending);
      expect(interaction.intents[slotKey(1, 0)]).toEqual({
        kind: 'send', command: { type: 'blindSwap', ownSlot: 3, seat: 1, slot: 0 },
      });
      expect(interaction.prompt).toBe('blindTarget');
    });

    it('KING_SWAP: my occupied slots, a click sends kingSwap', () => {
      const state = listing(['KING_SWAP', 'SKIP_POWER'], { phase: 'KING_DECISION', pendingPower: 'KING' });
      expect(clickable(state)).toEqual([...own].sort());
      const interaction = koboInteraction(state, 0, NONE);
      expect(interaction.intents[slotKey(0, 0)]).toEqual({ kind: 'send', command: { type: 'kingSwap', slot: 0 } });
      expect(interaction.prompt).toBe('kingSwap');
    });
  });

  describe('matching discard', () => {
    const base = listing(['MATCH_DISCARD'], {
      discardTop: buildKoboCard('KING'),
      players: [
        buildKoboPlayer(0, { current: true, slots: [buildKoboSlot(0, { revision: 4 }), buildKoboSlot(1)] }),
        buildKoboPlayer(1, { slots: [buildKoboSlot(0, { revision: 7 }), buildKoboSlot(1, { occupied: false })] }),
      ],
    });

    it('armed: every occupied slot of every player is clickable', () => {
      expect(clickable(base, { kind: 'match-armed' })).toEqual([slotKey(0, 0), slotKey(0, 1), slotKey(1, 0)].sort());
      expect(koboInteraction(base, 0, { kind: 'match-armed' }).prompt).toBe('matchPick');
    });

    it('armed: a click on my own card sends match with the rank and the revision on screen, no giveSlot', () => {
      const intent = koboInteraction(base, 0, { kind: 'match-armed' }).intents[slotKey(0, 0)];
      expect(intent).toEqual({
        kind: 'send',
        command: { type: 'match', seat: 0, slot: 0, expectedTopRank: 'KING', expectedRevision: 4 },
      });
      expect(intent).not.toHaveProperty('command.giveSlot');
    });

    it('armed: a click on another player card moves on to match-give with what was on screen', () => {
      const intent = koboInteraction(base, 0, { kind: 'match-armed' }).intents[slotKey(1, 0)];
      expect(intent).toEqual({
        kind: 'pend',
        pending: { kind: 'match-give', target: { seat: 1, slot: 0 }, expectedTopRank: 'KING', expectedRevision: 7 },
      });
    });

    it('armed: with no card of my own to give, a click on another player card sends at once without giveSlot', () => {
      const empty = listing(['MATCH_DISCARD'], {
        discardTop: buildKoboCard('KING'),
        players: [
          buildKoboPlayer(0, { current: true, slots: [buildKoboSlot(0, { occupied: false })] }),
          buildKoboPlayer(1, { slots: [buildKoboSlot(0, { revision: 7 })] }),
        ],
      });
      const intent = koboInteraction(empty, 0, { kind: 'match-armed' }).intents[slotKey(1, 0)];
      expect(intent).toEqual({
        kind: 'send',
        command: { type: 'match', seat: 1, slot: 0, expectedTopRank: 'KING', expectedRevision: 7 },
      });
      expect(intent).not.toHaveProperty('command.giveSlot');
    });

    it('match-give: only my occupied slots, a click sends match with giveSlot and the captured values', () => {
      const pending: KoboPending = {
        kind: 'match-give', target: { seat: 1, slot: 0 }, expectedTopRank: 'KING', expectedRevision: 7,
      };
      expect(clickable(base, pending)).toEqual([slotKey(0, 0), slotKey(0, 1)].sort());
      expect(koboInteraction(base, 0, pending).intents[slotKey(0, 1)]).toEqual({
        kind: 'send',
        command: { type: 'match', seat: 1, slot: 0, giveSlot: 1, expectedTopRank: 'KING', expectedRevision: 7 },
      });
      expect(koboInteraction(base, 0, pending).prompt).toBe('matchGive');
    });

    it('an empty discard pile leaves nothing to match: no clickable slot', () => {
      expect(clickable({ ...base, discardTop: null }, { kind: 'match-armed' })).toEqual([]);
    });

    it('without MATCH_DISCARD listed, a stale armed mode makes nothing clickable', () => {
      expect(clickable({ ...base, availableActions: [] }, { kind: 'match-armed' })).toEqual([]);
    });

    it('the armed mode wins over a listed swap of the drawn card', () => {
      const both = { ...base, availableActions: ['SWAP_DRAWN', 'MATCH_DISCARD'] as KoboAction[], hasDrawn: true };
      const intent = koboInteraction(both, 0, { kind: 'match-armed' }).intents[slotKey(0, 0)];
      expect(intent).toMatchObject({ kind: 'send', command: { type: 'match' } });
    });
  });

  describe('nothing to click', () => {
    it('with no slot-picking action listed, nothing is clickable and there is no prompt', () => {
      const state = listing(['DRAW', 'MATCH_DISCARD'], { phase: 'DRAW' });
      const interaction = koboInteraction(state, 0, NONE);
      expect(interaction.intents).toEqual({});
      expect(interaction.prompt).toBeNull();
    });

    it('when I am not the one holding the action (not listed for me), nothing is clickable', () => {
      expect(clickable(listing([], { phase: 'DECISION', pendingPower: 'PEEK_OWN' }))).toEqual([]);
    });

    it('a seat that is not at the table gets nothing', () => {
      const state = listing(['SWAP_DRAWN']);
      expect(Object.keys(koboInteraction(state, 5, NONE).intents)).toEqual([]);
    });
  });
});

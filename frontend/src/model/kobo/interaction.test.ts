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
      const state = listing(['DRAW'], { phase: 'DRAW' });
      const interaction = koboInteraction(state, 0, NONE);
      expect(interaction.intents).toEqual({});
      expect(interaction.prompt).toBeNull();
      expect(interaction.directMatch).toBe(false);
    });

    it('when I am not the one holding the action (not listed for me), nothing is clickable', () => {
      expect(clickable(listing([], { phase: 'DECISION', pendingPower: 'PEEK_OWN' }))).toEqual([]);
    });

    it('a seat that is not at the table gets nothing', () => {
      const state = listing(['SWAP_DRAWN']);
      expect(Object.keys(koboInteraction(state, 5, NONE).intents)).toEqual([]);
    });
  });

  describe('piles', () => {
    it('the draw pile is clickable exactly when DRAW is listed', () => {
      expect(koboInteraction(listing(['DRAW', 'MATCH_DISCARD']), 0, NONE).piles).toEqual({ draw: true, discard: false });
      expect(koboInteraction(listing(['MATCH_DISCARD']), 0, NONE).piles).toEqual({ draw: false, discard: false });
    });

    it('the discard pile is clickable exactly when DISCARD_DRAWN is listed (I hold a drawn card)', () => {
      const state = listing(['SWAP_DRAWN', 'DISCARD_DRAWN'], { phase: 'DECISION', hasDrawn: true, drawnCard: buildKoboCard('5') });
      expect(koboInteraction(state, 0, NONE).piles).toEqual({ draw: false, discard: true });
    });

    it('no pile is clickable for a seat that is not at the table', () => {
      expect(koboInteraction(listing(['DRAW']), 5, NONE).piles).toEqual({ draw: false, discard: false });
    });
  });

  // One click on an occupied slot is a match when no action of the phase uses the click on a slot.
  describe('direct match', () => {
    const matchable = (overrides: Partial<KoboState> = {}) => listing(['MATCH_DISCARD'], {
      discardTop: buildKoboCard('KING'),
      players: [
        buildKoboPlayer(0, { current: false, slots: [buildKoboSlot(0, { revision: 4 }), buildKoboSlot(1, { occupied: false })] }),
        buildKoboPlayer(1, { current: true, slots: [buildKoboSlot(0, { revision: 7 })] }),
      ],
      currentSeat: 1,
      ...overrides,
    });

    it('outside my turn, every occupied slot is a one-click match, taken at the revision on screen', () => {
      const interaction = koboInteraction(matchable(), 0, NONE);
      expect(interaction.directMatch).toBe(true);
      expect(Object.keys(interaction.intents).sort()).toEqual([slotKey(0, 0), slotKey(1, 0)]);
      expect(interaction.prompt).toBeNull();
      expect(interaction.intents[slotKey(0, 0)]).toEqual({
        kind: 'send', command: { type: 'match', seat: 0, slot: 0, expectedTopRank: 'KING', expectedRevision: 4 },
      });
    });

    it('a card of another player asks which of mine to give: a second click, like the armed mode', () => {
      expect(koboInteraction(matchable(), 0, NONE).intents[slotKey(1, 0)]).toEqual({
        kind: 'pend',
        pending: { kind: 'match-give', target: { seat: 1, slot: 0 }, expectedTopRank: 'KING', expectedRevision: 7 },
      });
    });

    it('my own turn before drawing (DRAW) and after discarding (TURN_END): still a match', () => {
      for (const phase of ['DRAW', 'TURN_END'] as const) {
        const state = matchable({
          phase, currentSeat: 0,
          players: [buildKoboPlayer(0, { current: true }), buildKoboPlayer(1)],
        });
        expect(koboInteraction(state, 0, NONE).directMatch, phase).toBe(true);
      }
    });

    it('a listed swap of the drawn card keeps the click (swap), no match', () => {
      const state = matchable({
        availableActions: ['SWAP_DRAWN', 'DISCARD_DRAWN', 'MATCH_DISCARD'], phase: 'DECISION', currentSeat: 0,
        hasDrawn: true, drawnCard: buildKoboCard('5'),
      });
      const interaction = koboInteraction(state, 0, NONE);
      expect(interaction.directMatch).toBe(false);
      expect(interaction.intents[slotKey(0, 0)]).toMatchObject({ command: { type: 'swapDrawn' } });
      expect(interaction.intents[slotKey(1, 0)]).toBeUndefined();
    });

    it('a pending power of mine (peek 7/8, swap J/Q, king) takes the clicks: no match anywhere', () => {
      const powers = [['PEEK_OWN', 'POWER'], ['PEEK_OTHER', 'POWER'], ['BLIND_SWAP', 'POWER'], ['KING', 'KING_DECISION']] as const;
      for (const [power, phase] of powers) {
        const state = matchable({
          availableActions: ['MATCH_DISCARD', 'SKIP_POWER'], phase, pendingPower: power, currentSeat: 0,
        });
        const interaction = koboInteraction(state, 0, NONE);
        expect(interaction.directMatch, power).toBe(false);
        const matches = Object.values(interaction.intents)
          .filter(i => i.kind === 'send' && i.command.type === 'match');
        expect(matches, power).toEqual([]);
      }
    });

    it('my turn, power pending but nothing listed that uses a slot: still no match (the power is not over)', () => {
      const state = matchable({ phase: 'POWER', pendingPower: 'PEEK_OWN', currentSeat: 0, availableActions: ['MATCH_DISCARD'] });
      expect(koboInteraction(state, 0, NONE).directMatch).toBe(false);
    });

    it('the power of another player does not stop me: I may match during their power', () => {
      const state = matchable({ phase: 'POWER', pendingPower: 'PEEK_OTHER', currentSeat: 1 });
      expect(koboInteraction(state, 0, NONE).directMatch).toBe(true);
    });

    it('a local selection (armed, give, blind-own) is never turned into a direct match', () => {
      const give: KoboPending = { kind: 'match-give', target: { seat: 1, slot: 0 }, expectedTopRank: 'KING', expectedRevision: 7 };
      expect(koboInteraction(matchable(), 0, give).directMatch).toBe(false);
      expect(koboInteraction(matchable(), 0, { kind: 'match-armed' }).directMatch).toBe(false);
      const blind = matchable({ availableActions: ['MATCH_DISCARD', 'BLIND_SWAP'], pendingPower: 'BLIND_SWAP', currentSeat: 0 });
      expect(koboInteraction(blind, 0, { kind: 'blind-own', ownSlot: 0 }).directMatch).toBe(false);
    });

    it('no direct match without MATCH_DISCARD, without a discard top, while memorising, in a gift or a revealed round', () => {
      expect(koboInteraction(matchable({ availableActions: [] }), 0, NONE).directMatch).toBe(false);
      expect(koboInteraction(matchable({ discardTop: null }), 0, NONE).directMatch).toBe(false);
      for (const phase of ['MEMORISING', 'GIFT', 'ROUND_OVER', 'FINISHED'] as const) {
        expect(koboInteraction(matchable({ phase }), 0, NONE).directMatch, phase).toBe(false);
      }
      expect(koboInteraction(matchable({ revealed: true }), 0, NONE).directMatch).toBe(false);
    });

    it('a drawn card I hold blocks it even if the server listing were incomplete', () => {
      const held = matchable({ currentSeat: 0, hasDrawn: true, drawnCard: buildKoboCard('5') });
      expect(koboInteraction(held, 0, NONE).directMatch).toBe(false);
    });

    it('the caller can hold it back (settling after a command of mine): the slots are then not clickable', () => {
      const interaction = koboInteraction(matchable(), 0, NONE, { directMatch: false });
      expect(interaction.directMatch).toBe(false);
      expect(interaction.intents).toEqual({});
    });

    // Option B: which phase and mode a tap means "Match" vs "turn action". The hint and the explicit Match button
    // follow the same rule, so a tap is never ambiguous: either nothing else wants the card, or a turn action does.
    describe('what a tap means (option B)', () => {
      const turnActions: [string, Partial<KoboState>][] = [
        ['swap of the drawn card', {
          availableActions: ['SWAP_DRAWN', 'DISCARD_DRAWN', 'MATCH_DISCARD'], phase: 'DECISION', currentSeat: 0,
          hasDrawn: true, drawnCard: buildKoboCard('5'),
        }],
        ['look at one of my cards', { availableActions: ['PEEK', 'SKIP_POWER', 'MATCH_DISCARD'], phase: 'POWER', pendingPower: 'PEEK_OWN', currentSeat: 0 }],
        ['look at another card', { availableActions: ['PEEK', 'SKIP_POWER', 'MATCH_DISCARD'], phase: 'POWER', pendingPower: 'PEEK_OTHER', currentSeat: 0 }],
        ['blind swap', { availableActions: ['BLIND_SWAP', 'SKIP_POWER', 'MATCH_DISCARD'], phase: 'POWER', pendingPower: 'BLIND_SWAP', currentSeat: 0 }],
        ['king swap', { availableActions: ['KING_SWAP', 'SKIP_POWER', 'MATCH_DISCARD'], phase: 'KING_DECISION', pendingPower: 'KING', currentSeat: 0 }],
      ];

      it('idle: a tap is a match, the hint line is shown and no Match button is needed', () => {
        const interaction = koboInteraction(matchable(), 0, NONE);
        expect(interaction.matchHint).toBe(true);
        expect(interaction.matchButton).toBe(false);
      });

      it.each(turnActions)('%s waiting for a card: the tap does that action, no hint, and Match stays reachable by its button', (_name, overrides) => {
        const interaction = koboInteraction(matchable(overrides), 0, NONE);
        expect(interaction.directMatch).toBe(false);
        expect(interaction.matchHint).toBe(false);
        expect(interaction.matchButton).toBe(true);
        const matches = Object.values(interaction.intents).filter(i => i.kind === 'send' && i.command.type === 'match');
        expect(matches).toEqual([]);
      });

      it('a revealed peek holds the taps too: the button is the way to match', () => {
        const interaction = koboInteraction(matchable({ revealed: true }), 0, NONE);
        expect(interaction.matchHint).toBe(false);
        expect(interaction.matchButton).toBe(true);
      });

      it('settling after my command only holds the tap back: neither the hint nor the button flicker', () => {
        const interaction = koboInteraction(matchable(), 0, NONE, { directMatch: false });
        expect(interaction.directMatch).toBe(false);
        expect(interaction.matchHint).toBe(true);
        expect(interaction.matchButton).toBe(false);
      });

      it('the armed mode keeps the button as its toggle and hides the hint', () => {
        const interaction = koboInteraction(matchable(), 0, { kind: 'match-armed' });
        expect(interaction.matchHint).toBe(false);
        expect(interaction.matchButton).toBe(true);
      });

      it('a rival card picked (give step): no hint and no Match button, only Cancel (in the bar) leaves', () => {
        const give: KoboPending = { kind: 'match-give', target: { seat: 1, slot: 0 }, expectedTopRank: 'KING', expectedRevision: 7 };
        const interaction = koboInteraction(matchable(), 0, give);
        expect(interaction.matchHint).toBe(false);
        expect(interaction.matchButton).toBe(false);
      });

      it('nothing to match (no listing, empty discard, memorising, gift): no hint, no button', () => {
        expect(koboInteraction(matchable({ availableActions: [] }), 0, NONE)).toMatchObject({ matchHint: false, matchButton: false });
        expect(koboInteraction(matchable({ discardTop: null }), 0, NONE)).toMatchObject({ matchHint: false, matchButton: false });
        for (const phase of ['MEMORISING', 'GIFT', 'ROUND_OVER', 'FINISHED'] as const) {
          expect(koboInteraction(matchable({ phase }), 0, NONE), phase).toMatchObject({ matchHint: false, matchButton: false });
        }
      });

      it('a seat that is not at the table gets neither', () => {
        expect(koboInteraction(matchable(), 5, NONE)).toMatchObject({ matchHint: false, matchButton: false });
      });

      it('a tap on a rival card only picks it (nothing is sent), the next tap on mine sends the match with the give slot', () => {
        const first = koboInteraction(matchable(), 0, NONE).intents[slotKey(1, 0)];
        expect(first.kind).toBe('pend');
        if (first.kind !== 'pend') return;
        const second = koboInteraction(matchable(), 0, first.pending).intents[slotKey(0, 0)];
        expect(second).toEqual({
          kind: 'send',
          command: { type: 'match', seat: 1, slot: 0, giveSlot: 0, expectedTopRank: 'KING', expectedRevision: 7 },
        });
        // While a rival card is picked, no rival card is a target any more.
        expect(Object.keys(koboInteraction(matchable(), 0, first.pending).intents)).toEqual([slotKey(0, 0)]);
      });
    });

    it('the armed mode still reads matchPick and keeps its clickable slots', () => {
      const interaction = koboInteraction(matchable(), 0, { kind: 'match-armed' });
      expect(interaction.prompt).toBe('matchPick');
      expect(Object.keys(interaction.intents)).toHaveLength(2);
    });
  });
});

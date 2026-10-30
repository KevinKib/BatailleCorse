import { describe, it, expect } from 'vitest';
import { DEALER_KEY, handKey, holdFromState, lastTableOf, roundNetOf, shownState, type Hold } from './shown';
import { blackjackBettingState } from './fixtures';
import { blackjackPlayingState, blackjackSettledState, card } from './fixtures';
import type { BlackjackState } from './BlackjackState';

const playing = blackjackPlayingState;

function withHold(overrides: Partial<Hold> = {}): Hold {
  return { ...holdFromState(playing), ...overrides };
}

describe('holdFromState', () => {
  it('shows every card of the state it is taken from and hides nothing', () => {
    const hold = holdFromState(playing);
    expect(hold.counts[handKey(0, 0)]).toBe(2);
    expect(hold.counts[handKey(0, 1)]).toBe(1);
    expect(hold.counts[DEALER_KEY]).toBe(2); // the up card and the hole card (face down)
    expect(hold.phase).toBe('PLAYING');
    expect(hold.currentSeat).toBe(0);
    expect(hold.betsSealed).toBe(false);
    expect(hold.resultsHidden).toBe(false);
    expect(hold.holeFaceDown).toBe(playing.dealer.holeHidden);
  });

  it('is the identity: the state shown through it is the state', () => {
    const shown = shownState(playing, holdFromState(playing));
    expect(shown.players.map(p => p.hands.map(h => h.cards.length))).toEqual(playing.players.map(p => p.hands.map(h => h.cards.length)));
    expect(shown.dealer.cards).toEqual(playing.dealer.cards);
    expect(shown.dealer.holeHidden).toBe(true);
  });
});

describe('shownState: no hold', () => {
  it('returns the state itself', () => {
    expect(shownState(playing, null)).toBe(playing);
  });
});

describe('shownState: cards that have not landed', () => {
  it('shows only the first cards of a hand and marks the hand pending (its total is not shown)', () => {
    const hold = withHold({ counts: { ...holdFromState(playing).counts, [handKey(1, 0)]: 1 } });
    const bob = shownState(playing, hold).players[1].hands[0];
    expect(bob.cards).toEqual([card('TEN', 'HEART')]);
    expect(bob.pending).toBe(true);
  });

  it('does not mark a complete hand pending', () => {
    const bob = shownState(playing, withHold()).players[1].hands[0];
    expect(bob.pending).toBeFalsy();
  });

  it('hides a hand that has no entry yet (a split hand before the split, a seat before the deal)', () => {
    const counts = { ...holdFromState(playing).counts };
    delete counts[handKey(0, 1)];
    const hands = shownState(playing, withHold({ counts })).players[0].hands;
    expect(hands).toHaveLength(1);
  });

  it('shows an empty hand (count 0) as a hand without cards', () => {
    const hands = shownState(playing, withHold({ counts: { ...holdFromState(playing).counts, [handKey(0, 0)]: 0 } })).players[0].hands;
    expect(hands[0].cards).toEqual([]);
    expect(hands[0].pending).toBe(true);
  });
});

describe('shownState: the dealer', () => {
  it('shows nothing before the first card lands', () => {
    const dealer = shownState(playing, withHold({ counts: { ...holdFromState(playing).counts, [DEALER_KEY]: 0 } })).dealer;
    expect(dealer.cards).toEqual([]);
    expect(dealer.holeHidden).toBe(false);
    expect(dealer.pending).toBe(true);
  });

  it('shows the up card alone, then the hole card face down', () => {
    const one = shownState(playing, withHold({ counts: { ...holdFromState(playing).counts, [DEALER_KEY]: 1 } })).dealer;
    expect(one.cards).toEqual([card('SIX', 'DIAMOND')]);
    expect(one.holeHidden).toBe(false);
    const two = shownState(playing, withHold()).dealer;
    expect(two.cards).toEqual([card('SIX', 'DIAMOND')]);
    expect(two.holeHidden).toBe(true);
  });

  describe('once the server has revealed the hole card', () => {
    const revealed: BlackjackState = {
      ...playing,
      dealer: {
        upCard: card('SIX', 'DIAMOND'),
        cards: [card('SIX', 'DIAMOND'), card('TEN', 'CLUB'), card('FIVE', 'HEART')],
        holeHidden: false, total: 21, soft: false, blackjack: false, bust: false,
      },
    };

    it('keeps the hole card face down until the table turns it over, and its total unknown', () => {
      const hold = holdFromState(playing); // dealer count 2 with the hole face down
      const dealer = shownState(revealed, { ...hold, counts: { ...hold.counts, [DEALER_KEY]: 2 }, holeFaceDown: true }).dealer;
      expect(dealer.cards).toEqual([card('SIX', 'DIAMOND')]);
      expect(dealer.holeHidden).toBe(true);
      expect(dealer.pending).toBe(true);
    });

    it('shows the hole card face up once turned over, then the cards drawn one by one', () => {
      const hold = { ...holdFromState(playing), holeFaceDown: false };
      const turned = shownState(revealed, { ...hold, counts: { ...hold.counts, [DEALER_KEY]: 2 } }).dealer;
      expect(turned.cards).toEqual([card('SIX', 'DIAMOND'), card('TEN', 'CLUB')]);
      expect(turned.holeHidden).toBe(false);
      expect(turned.pending).toBe(true); // the card drawn next has not landed
      const done = shownState(revealed, { ...hold, counts: { ...hold.counts, [DEALER_KEY]: 3 } }).dealer;
      expect(done.cards).toHaveLength(3);
      expect(done.pending).toBeFalsy();
      expect(done.total).toBe(21);
    });
  });
});

describe('shownState: what the table has not told yet', () => {
  it('shows the phase, the seat to play and the hand number of the hold', () => {
    const shown = shownState(blackjackSettledState, withHold({ phase: 'PLAYING', currentSeat: -1, activeHandIndex: 0, handNumber: 3 }));
    expect(shown.phase).toBe('PLAYING');
    expect(shown.currentSeat).toBe(-1);
    expect(shown.handNumber).toBe(3);
  });

  it('never offers an action or a bet through the shown state (the buttons read the live state)', () => {
    const shown = shownState(playing, withHold());
    expect(shown.availableActions).toEqual([]);
    expect(shown.betLimits).toBeNull();
  });

  it('keeps the other seats\' bets sealed until the table reveals them, never my own', () => {
    const hold = withHold({ betsSealed: true });
    const players = shownState(playing, hold).players;
    expect(players[0].bet).toBe(20); // mine (seat 0 is the viewer in the fixture)
    expect(players[1].bet).toBeNull();
    expect(players[1].betPlaced).toBe(true);
    const open = shownState(playing, withHold({ betsSealed: false })).players;
    expect(open[1].bet).toBe(10);
  });

  it('remembers the bets of the hand while the state has moved on to the next betting', () => {
    const hold = { ...holdFromState(playing), betsSealed: false };
    const players = shownState(blackjackBettingState, hold).players;
    expect(players[1].bet).toBe(10);
    expect(players[1].betPlaced).toBe(true);
  });

  it('hides the nets of the hands until the results are shown', () => {
    const settled: BlackjackState = {
      ...playing,
      players: playing.players.map(p => ({ ...p, hands: p.hands.map(h => ({ ...h, net: 10 })) })),
    };
    expect(shownState(settled, withHold({ resultsHidden: true })).players[1].hands[0].net).toBeUndefined();
    expect(shownState(settled, withHold({ resultsHidden: false })).players[1].hands[0].net).toBe(10);
  });

  it('shows the stacks the hold froze, and the live ones once it lets go', () => {
    const frozen = shownState(playing, withHold({ stacks: { 0: 70, 1: 55 } })).players;
    expect(frozen[1].stack).toBe(55);
    expect(frozen[2].stack).toBe(playing.players[2].stack);
  });

  it('hides the summary of the previous hand while the table is being told', () => {
    expect(shownState(blackjackBettingState, withHold()).lastHand).toBeNull();
    expect(shownState(blackjackBettingState, null).lastHand).not.toBeNull();
  });

  it('does not mutate the live state', () => {
    const before = JSON.stringify(playing);
    shownState(playing, withHold({ betsSealed: true, resultsHidden: true, counts: { [DEALER_KEY]: 0 } }));
    expect(JSON.stringify(playing)).toBe(before);
  });
});

describe('the last table stays until the next deal', () => {
  const betting = blackjackBettingState; // the next betting: no cards in the state, the previous hand in `lastHand`

  it('keeps my cards and the dealer\'s on the table through the next betting, from the last hand', () => {
    const shown = shownState(betting, null);
    expect(shown.dealer.cards).toEqual([card('KING'), card('QUEEN', 'HEART')]);
    expect(shown.dealer.holeHidden).toBe(false);
    expect(shown.dealer.total).toBe(20);
    expect(shown.players[0].hands).toHaveLength(1);
    expect(shown.players[0].hands[0].cards).toEqual([card('TEN', 'CLUB'), card('SEVEN', 'CLUB')]);
    expect(shown.players[0].hands[0].total).toBe(17);
    expect(shown.players[3].hands[0].status).toBe('BUST');
    expect(shown.players[3].hands[0].cards).toHaveLength(3);
  });

  it('tells no net on the old cards (the summary tells them, the new chips are not a payout)', () => {
    const shown = shownState(betting, null);
    expect(shown.players.every(p => p.hands.every(h => h.net === undefined))).toBe(true);
  });

  it('keeps everything else live: bets, stacks, phase, the previous hand summary', () => {
    const shown = shownState(betting, null);
    expect(shown.phase).toBe('BETTING');
    expect(shown.players[1].betPlaced).toBe(true);
    expect(shown.players[0].stack).toBe(betting.players[0].stack);
    expect(shown.lastHand).toBe(betting.lastHand);
    expect(shown.betLimits).toBe(betting.betLimits);
  });

  it('shows a split pair as two hands', () => {
    const lastHand = {
      ...betting.lastHand!,
      seats: [{ seat: 0, net: 0, hands: [
        { cards: [card('EIGHT'), card('TEN')], total: 18, status: 'STOOD' as const, net: 0 },
        { cards: [card('EIGHT', 'HEART'), card('NINE')], total: 17, status: 'STOOD' as const, net: 0 },
      ] }],
    };
    const shown = shownState({ ...betting, lastHand }, null);
    expect(shown.players[0].hands).toHaveLength(2);
    expect(shown.players[0].hands.every(h => h.fromSplit)).toBe(true);
  });

  it('leaves a first betting (no previous hand) and a table with cards alone', () => {
    const first = { ...betting, lastHand: null };
    expect(shownState(first, null)).toBe(first);
    expect(shownState(playing, null)).toBe(playing);
  });

  it('keeps the old cards through a hold that has not cleared the table yet, whatever the new state says', () => {
    const hold = { ...holdFromState(betting), ghost: lastTableOf(betting) };
    const shown = shownState(playing, hold);
    expect(shown.dealer.cards).toEqual([card('KING'), card('QUEEN', 'HEART')]);
    expect(shown.players[0].hands[0].cards).toEqual([card('TEN', 'CLUB'), card('SEVEN', 'CLUB')]);
  });

  it('lets go of the old cards once the hold has cleared the table', () => {
    const hold = { ...holdFromState(betting), ghost: null, counts: { [DEALER_KEY]: 0, [handKey(0, 0)]: 0 } };
    const shown = shownState(playing, hold);
    expect(shown.dealer.cards).toEqual([]);
    expect(shown.players[0].hands[0].cards).toEqual([]);
  });
});

describe('roundNetOf', () => {
  it('adds the nets of the hands once all are settled, null before', () => {
    const [alice] = playing.players;
    expect(roundNetOf(alice)).toBeNull();
    const settled = { ...alice, hands: alice.hands.map((h, i) => ({ ...h, net: i === 0 ? 20 : -10 })) };
    expect(roundNetOf(settled)).toBe(10);
    expect(roundNetOf({ ...settled, hands: [{ ...settled.hands[0], pending: true }] })).toBeNull();
  });
});

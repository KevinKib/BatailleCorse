import { describe, it, expect } from 'vitest';
import { scriptFor, slotAnchor, tagKindOf, type BlackjackTableEvent, type ScriptContext, type ScriptStep } from './motion';
import { BLACKJACK_TEMPO as TEMPO } from './tempo';
import { DEALER_KEY, handKey } from './shown';
import { card } from './fixtures';
import type { BlackjackEventData } from './BlackjackState';

const empty: BlackjackEventData = {
  seat: null, handIndex: null, card: null, value: null, cards: null, holeCard: null, handNumber: null,
  button: null, bets: null, nets: null, stacks: null, bestSeats: null,
};
const event = (eventType: string, data: Partial<BlackjackEventData> = {}): BlackjackTableEvent =>
  ({ id: 1, eventType, data: { ...empty, ...data } });
const ctx = (planned: Record<string, number> = {}, mySeat = 0): ScriptContext =>
  ({ mySeat, planned, faceAt: () => null });
const effects = (steps: ScriptStep[], type: string) => steps.filter(s => s.effect.type === type);
const flights = (steps: ScriptStep[]) => steps.filter(s => s.flight).map(s => s.flight!);

describe('slotAnchor', () => {
  it('names the n-th card place of a hand or of the dealer', () => {
    expect(slotAnchor(handKey(2, 1), 3)).toBe('s2h1:3');
    expect(slotAnchor(DEALER_KEY, 1)).toBe('dealer:1');
  });
});

describe('tagKindOf', () => {
  it('names the visible tag of an action, none for the rest', () => {
    expect(tagKindOf('HIT')).toBe('hit');
    expect(tagKindOf('STAND')).toBe('stand');
    expect(tagKindOf('DOUBLE')).toBe('double');
    expect(tagKindOf('SPLIT')).toBe('split');
    expect(tagKindOf('BUST')).toBe('bust');
    expect(tagKindOf('SETTLE')).toBeNull();
  });
});

describe('scriptFor', () => {
  it('has no script for an event with no data, a bet or an unknown event', () => {
    expect(scriptFor({ id: 1, eventType: 'DEAL', data: null }, ctx()).steps).toEqual([]);
    expect(scriptFor(event('BET', { seat: 1 }), ctx()).steps).toEqual([]);
    expect(scriptFor(event('NOPE'), ctx()).steps).toEqual([]);
  });

  describe('DEAL (the order of the spec 3.4)', () => {
    // Bets by seat 0..3, the button on seat 2: seat 2, 3, 0, 1.
    const bets = { 0: 10, 1: 20, 2: 10, 3: 10 };
    const deal = (planned: Record<string, number> = {}) => scriptFor(event('DEAL', { handNumber: 3, button: 2, bets }), ctx(planned));

    it('tells the bets together first, then clears the old cards of every bettor and the dealer', () => {
      const { steps } = deal();
      expect(steps[0].atMs).toBe(0);
      expect(steps[0].effect).toEqual({ type: 'unseal', bets });
      const clear = effects(steps, 'clear')[0];
      expect(clear.atMs).toBe(TEMPO.bets.revealMs);
      expect(clear.effect).toMatchObject({ type: 'clear', handNumber: 3 });
      expect((clear.effect as { keys: string[] }).keys.sort()).toEqual([DEALER_KEY, 's0h0', 's1h0', 's2h0', 's3h0'].sort());
    });

    it('deals a card to each seat from the button, the dealer up card, a second round, the hole card', () => {
      const moves = flights(deal().steps);
      expect(moves.map(m => [m.from, m.to])).toEqual([
        ['shoe', 's2h0:0'], ['shoe', 's3h0:0'], ['shoe', 's0h0:0'], ['shoe', 's1h0:0'], ['shoe', 'dealer:0'],
        ['shoe', 's2h0:1'], ['shoe', 's3h0:1'], ['shoe', 's0h0:1'], ['shoe', 's1h0:1'], ['shoe', 'dealer:1'],
      ]);
    });

    it('starts each card one stagger after the other once the table is cleared, and lands it when it arrives', () => {
      const { steps } = deal();
      const start = TEMPO.bets.revealMs + TEMPO.bets.clearMs;
      const legs = steps.filter(s => s.flight);
      legs.forEach((s, i) => {
        expect(s.atMs).toBe(start + i * TEMPO.deal.staggerMs);
        expect(s.flight!.durationMs).toBe(TEMPO.deal.flightMs);
        expect(s.flight!.kind).toBe('travel');
        expect(s.flight!.delayMs).toBe(0);
      });
      const lands = effects(steps, 'land');
      expect(lands).toHaveLength(10);
      expect(lands[0].atMs).toBe(start + TEMPO.deal.flightMs);
      expect(lands[0].effect).toEqual({ type: 'land', key: 's2h0' });
      expect(lands[9].effect).toEqual({ type: 'land', key: DEALER_KEY });
    });

    it('shows the face of a player card, never the hole card', () => {
      const faces = new Map<string, ReturnType<typeof card>>([['s2h0:0', card('KING')], ['dealer:0', card('SIX')], ['dealer:1', card('ACE')]]);
      const moves = flights(scriptFor(event('DEAL', { handNumber: 3, button: 2, bets }),
        { mySeat: 0, planned: {}, faceAt: (k, i) => faces.get(`${k}:${i}`) ?? null }).steps);
      expect(moves[0].card).toEqual(card('KING'));
      expect(moves[4].card).toEqual(card('SIX'));
      expect(moves[9].card).toBeNull(); // the hole card flies as a back
    });

    it('skips a seat that did not bet (eliminated or gone)', () => {
      const moves = flights(scriptFor(event('DEAL', { button: 0, bets: { 0: 5, 2: 5 } }), ctx()).steps);
      expect(moves.map(m => m.to)).toEqual(['s0h0:0', 's2h0:0', 'dealer:0', 's0h0:1', 's2h0:1', 'dealer:1']);
    });

    it('opens the play at the end: nobody is on turn yet, the phase is the play', () => {
      const { steps, spanMs } = deal();
      const last = steps[steps.length - 1];
      expect(last.effect).toEqual({ type: 'meta', phase: 'PLAYING', currentSeat: -1, activeHandIndex: 0 });
      expect(spanMs).toBeGreaterThanOrEqual(last.atMs);
    });

    it('plans the cards on a table that was cleared: the next slots start at 0', () => {
      const planned = { s0h0: 4, [DEALER_KEY]: 3 };
      deal(planned);
      expect(planned.s0h0).toBe(2);
      expect(planned[DEALER_KEY]).toBe(2);
    });

    it('has nothing to deal without a bettor', () => {
      expect(scriptFor(event('DEAL', { bets: {} }), ctx()).steps).toEqual([]);
    });
  });

  describe('HIT and DOUBLE', () => {
    it('says the action over the seat, then flies one card from the shoe to the next place of the hand', () => {
      const { steps } = scriptFor(event('HIT', { seat: 2, handIndex: 0, card: card('FIVE'), value: 15 }), ctx({ s2h0: 2 }));
      expect(steps[0]).toMatchObject({ atMs: 0, effect: { type: 'meta', phase: 'PLAYING', currentSeat: 2, activeHandIndex: 0 } });
      expect(effects(steps, 'tag')[0].effect).toEqual({ type: 'tag', seat: 2, kind: 'hit' });
      const leg = steps.find(s => s.flight)!;
      expect(leg.atMs).toBe(TEMPO.draw.tagLeadMs);
      expect(leg.flight).toMatchObject({ from: 'shoe', to: 's2h0:2', card: card('FIVE'), actor: 2, byOpponent: true });
      expect(effects(steps, 'land')[0]).toMatchObject({ atMs: TEMPO.draw.tagLeadMs + TEMPO.draw.flightMs, effect: { key: 's2h0' } });
    });

    it('is not by an opponent when the seat is mine', () => {
      const leg = scriptFor(event('HIT', { seat: 0, handIndex: 1, card: card('TWO') }), ctx({ s0h1: 1 }, 0)).steps.find(s => s.flight)!;
      expect(leg.flight).toMatchObject({ to: 's0h1:1', byOpponent: false });
    });

    it('a double flies its single card too, tagged Double', () => {
      const { steps } = scriptFor(event('DOUBLE', { seat: 1, handIndex: 0, card: card('NINE'), value: 20 }), ctx({ s1h0: 2 }));
      expect(effects(steps, 'tag')[0].effect).toEqual({ type: 'tag', seat: 1, kind: 'double' });
      expect(flights(steps)).toHaveLength(1);
    });

    it('counts the card it plans, so two hits in a row go to two places', () => {
      const planned = { s2h0: 2 };
      const a = scriptFor(event('HIT', { seat: 2, handIndex: 0, card: card('TWO') }), ctx(planned));
      const b = scriptFor(event('HIT', { seat: 2, handIndex: 0, card: card('THREE') }), ctx(planned));
      expect(flights(a.steps)[0].to).toBe('s2h0:2');
      expect(flights(b.steps)[0].to).toBe('s2h0:3');
    });

    it('holds long enough for the card to be seen', () => {
      const { spanMs } = scriptFor(event('HIT', { seat: 2, handIndex: 0, card: card('TWO') }), ctx({ s2h0: 2 }));
      expect(spanMs).toBe(TEMPO.draw.tagLeadMs + TEMPO.draw.flightMs + TEMPO.draw.endMs);
    });
  });

  describe('STAND and BUST', () => {
    it('say it over the seat and wait so that it can be read', () => {
      const stand = scriptFor(event('STAND', { seat: 3 }), ctx());
      expect(effects(stand.steps, 'tag')[0].effect).toEqual({ type: 'tag', seat: 3, kind: 'stand' });
      expect(stand.spanMs).toBe(TEMPO.say.spanMs);
      const bust = scriptFor(event('BUST', { seat: 3, value: 24 }), ctx());
      expect(effects(bust.steps, 'tag')[0].effect).toEqual({ type: 'tag', seat: 3, kind: 'bust' });
      expect(flights(bust.steps)).toEqual([]);
    });

    it('says nothing without a seat', () => {
      expect(scriptFor(event('STAND', {}), ctx()).steps).toEqual([]);
    });
  });

  describe('SPLIT', () => {
    const cards = [card('EIGHT', 'CLUB'), card('THREE', 'HEART'), card('EIGHT', 'DIAMOND')];
    const split = (all = cards, planned: Record<string, number> = { s1h0: 2 }) =>
      scriptFor(event('SPLIT', { seat: 1, handIndex: 0, cards: all }), ctx(planned));

    it('opens the second hand, moves the second card to it, then deals the first hand a card', () => {
      const { steps } = split();
      expect(effects(steps, 'tag')[0].effect).toEqual({ type: 'tag', seat: 1, kind: 'split' });
      expect(effects(steps, 'open')[0].effect).toEqual({ type: 'open', key: 's1h1' });
      expect(effects(steps, 'leave')[0].effect).toEqual({ type: 'leave', key: 's1h0' });
      expect(flights(steps).map(m => [m.from, m.to, m.card])).toEqual([
        ['s1h0:1', 's1h1:0', card('EIGHT', 'DIAMOND')],
        ['shoe', 's1h0:1', card('THREE', 'HEART')],
      ]);
    });

    it('deals the second hand a card too when the event lists a fourth card (split aces)', () => {
      const { steps } = split([...cards, card('KING', 'SPADE')]);
      expect(flights(steps).map(m => m.to)).toEqual(['s1h1:0', 's1h0:1', 's1h1:1']);
    });

    it('lands each card when it arrives, in order', () => {
      const lands = effects(split().steps, 'land').map(s => (s.effect as { key: string }).key);
      expect(lands).toEqual(['s1h1', 's1h0']);
    });
  });

  describe('the dealer', () => {
    const play = (drawn: ReturnType<typeof card>[], value: number, planned: Record<string, number> = { [DEALER_KEY]: 2 }) =>
      scriptFor(event('DEALER_PLAY', { holeCard: card('TEN'), cards: drawn, value }), ctx(planned));

    it('announces the dealer turn, turns the hole card over (a reveal on the second place), then says the end', () => {
      const { steps } = play([], 18);
      expect(effects(steps, 'banner')[0].effect).toEqual({ type: 'banner', kind: 'dealer' });
      const reveal = steps.find(s => s.flight?.kind === 'reveal')!;
      expect(reveal.flight).toMatchObject({ from: 'dealer:1', to: 'dealer:1', card: card('TEN') });
      expect(reveal.atMs).toBe(TEMPO.dealer.bannerMs);
      const hole = effects(steps, 'hole')[0];
      expect(hole.atMs).toBeGreaterThan(reveal.atMs);
      expect(hole.atMs).toBeLessThan(reveal.atMs + TEMPO.dealer.revealMs);
      expect(effects(steps, 'dealer').map(s => s.effect)).toEqual([
        { type: 'dealer', kind: 'reveals', total: 0 },
        { type: 'dealer', kind: 'stands', total: 18 },
      ]);
    });

    it('draws visibly one by one, each with its line, each at its own place', () => {
      const { steps } = play([card('FIVE'), card('FOUR')], 19);
      expect(flights(steps).filter(m => m.kind === 'travel').map(m => [m.from, m.to])).toEqual([['shoe', 'dealer:2'], ['shoe', 'dealer:3']]);
      const kinds = effects(steps, 'dealer').map(s => (s.effect as { kind: string }).kind);
      expect(kinds).toEqual(['reveals', 'draws', 'draws', 'stands']);
      const draws = steps.filter(s => s.flight?.kind === 'travel');
      expect(draws[1].atMs - draws[0].atMs).toBe(TEMPO.dealer.drawGapMs);
    });

    it('busts over 21', () => {
      const { steps } = play([card('KING')], 26);
      expect(effects(steps, 'dealer').pop()!.effect).toEqual({ type: 'dealer', kind: 'busts', total: 26 });
    });

    it('a dealer blackjack turns the hole card over and says it', () => {
      const { steps } = scriptFor(event('DEALER_BLACKJACK', { holeCard: card('KING') }), ctx({ [DEALER_KEY]: 2 }));
      expect(steps.some(s => s.flight?.kind === 'reveal')).toBe(true);
      expect(effects(steps, 'hole')).toHaveLength(1);
      expect(effects(steps, 'dealer').pop()!.effect).toEqual({ type: 'dealer', kind: 'blackjack', total: 0 });
    });
  });

  describe('SETTLE', () => {
    it('tells the results, keeps them on the table for a while, and names the phase', () => {
      const { steps, spanMs } = scriptFor(event('SETTLE', { nets: { 0: 10 }, stacks: { 0: 110 } }), ctx());
      expect(effects(steps, 'banner')[0].effect).toEqual({ type: 'banner', kind: 'results' });
      expect(effects(steps, 'settle')).toHaveLength(1);
      expect(spanMs).toBe(TEMPO.results.holdMs);
    });
  });
});

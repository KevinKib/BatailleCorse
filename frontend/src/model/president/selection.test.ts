import { describe, it, expect } from 'vitest';
import { analyseSelection, completingCards, patternOf, type SelectionContext } from './selection';
import type { PresidentPattern } from './PresidentState';
import { buildPresidentCard as card, buildPresidentTrick } from '../fixtures/president';

const set = (rank: string, count: number): PresidentPattern => ({ kind: 'SET', rank, count });

const NINE_H = card('9', 'HEART');
const NINE_S = card('9', 'SPADE');
const NINE_C = card('9', 'CLUB');
const KING_H = card('KING', 'HEART');
const KING_S = card('KING', 'SPADE');
const FOUR_D = card('4', 'DIAMOND');

/** The viewer to act: `legalPlays` as the server lists them, a trick whose top is `top`. */
function ctx(legalPlays: PresidentPattern[], top: PresidentPattern | null, actions: string[] = ['PLAY', 'PASS']): SelectionContext {
  return {
    legalPlays,
    availableActions: actions as SelectionContext['availableActions'],
    trick: buildPresidentTrick({ top, leader: 1 }),
  };
}

describe('patternOf', () => {
  it.each([
    ['nothing', [], null],
    ['one card', [NINE_H], set('9', 1)],
    ['a pair', [NINE_H, NINE_S], set('9', 2)],
    ['a triple, in any order', [NINE_S, NINE_C, NINE_H], set('9', 3)],
    ['two ranks', [NINE_H, KING_H], null],
    ['two ranks of equal count', [NINE_H, NINE_S, KING_H, KING_S], null],
  ])('%s', (_name, cards, expected) => {
    expect(patternOf(cards)).toEqual(expected);
  });
});

describe('analyseSelection', () => {
  const leading = ctx([set('9', 1), set('9', 2), set('KING', 2), set('4', 1)], null);
  const followingPair = ctx([set('KING', 2)], set('9', 2));
  const followingSingle = ctx([set('KING', 1), set('ACE', 1)], set('9', 1));

  it.each<[string, SelectionContext, typeof NINE_H[], boolean, object | null]>([
    ['leading with a single', leading, [FOUR_D], true, null],
    ['leading with a pair', leading, [NINE_H, NINE_S], true, null],
    ['leading with a rank that has no such count', leading, [NINE_H, NINE_S, NINE_C], false, { code: 'wrongCount', counts: [1, 2] }],
    ['following with a higher pair', followingPair, [KING_H, KING_S], true, null],
    ['following with a pair that does not beat', followingPair, [NINE_H, NINE_S], false, { code: 'notHigher', rank: '9', count: 2 }],
    ['following a pair with a single', followingPair, [KING_H], false, { code: 'wrongCount', counts: [2] }],
    ['following a single with a pair', followingSingle, [KING_H, KING_S], false, { code: 'wrongCount', counts: [1] }],
    ['following a single with a lower single', followingSingle, [FOUR_D], false, { code: 'notHigher', rank: '9', count: 1 }],
    ['mixed ranks', leading, [NINE_H, KING_H], false, { code: 'mixedRanks' }],
    ['nothing selected', leading, [], false, { code: 'nothingSelected' }],
  ])('%s', (_name, context, selected, legal, reason) => {
    const analysis = analyseSelection(context, selected);
    expect(analysis.legal).toBe(legal);
    expect(analysis.reason).toEqual(reason);
  });

  it('carries the pattern of a legal selection, and of an illegal single-rank one too', () => {
    expect(analyseSelection(followingPair, [KING_H, KING_S]).pattern).toEqual(set('KING', 2));
    expect(analyseSelection(followingPair, [NINE_H, NINE_S]).pattern).toEqual(set('9', 2));
    expect(analyseSelection(followingPair, [NINE_H, KING_H]).pattern).toBeNull();
  });

  it('cannot play when the server does not list PLAY (not my turn, exchange, round over)', () => {
    const waiting = ctx([], null, []);
    expect(analyseSelection(waiting, [NINE_H])).toEqual({ pattern: set('9', 1), legal: false, reason: { code: 'cannotPlay' } });
    expect(analyseSelection(waiting, []).reason).toEqual({ code: 'cannotPlay' });
  });

  it('must pass when it is my turn but nothing beats the trick', () => {
    const stuck = ctx([], set('ACE', 1));
    expect(analyseSelection(stuck, [FOUR_D]).reason).toEqual({ code: 'mustPass' });
  });

  it('decides only from the legal plays: a play the server lists is legal whatever its shape', () => {
    const odd = ctx([{ kind: 'SET', rank: '9', count: 4 }], null);
    expect(analyseSelection(odd, [NINE_H, NINE_S, NINE_C, card('9', 'DIAMOND')]).legal).toBe(true);
  });
});

describe('completingCards', () => {
  const hand = [NINE_H, NINE_S, NINE_C, KING_H, FOUR_D];

  it('lists the cards of the rank still in the hand when a bigger legal play exists', () => {
    const context = ctx([set('9', 1), set('9', 2), set('9', 3)], null);
    expect(completingCards(hand, [NINE_H], context.legalPlays)).toEqual([NINE_S, NINE_C]);
  });

  it('lists nothing when no legal play of that rank is bigger than the selection', () => {
    expect(completingCards(hand, [NINE_H, NINE_S], [set('9', 1), set('9', 2)])).toEqual([]);
  });

  it('lists nothing for an empty or a mixed selection', () => {
    expect(completingCards(hand, [], [set('9', 2)])).toEqual([]);
    expect(completingCards(hand, [NINE_H, KING_H], [set('9', 2)])).toEqual([]);
  });
});

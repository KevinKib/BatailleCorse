import { describe, it, expect } from 'vitest';
import { aceValuesFor, needsAceChoice, playableFor, isSafe } from './playable';
import {
  buildNinetyNineCard, buildNinetyNineFourSeatRound, buildNinetyNineTwelveSeatRound,
} from '../fixtures/ninetynine';

describe('playable lookup', () => {
  const round = buildNinetyNineFourSeatRound();
  const ace = round.hand[1];
  const seven = round.hand[0];

  it('finds the server entry of a card of my hand, by card name', () => {
    expect(playableFor(round, seven)).toEqual({ card: seven, safe: true, aceValues: [] });
    expect(playableFor(round, { ...ace })?.aceValues).toEqual([1, 11]);
  });

  it('knows nothing of a card that is not in my hand', () => {
    expect(playableFor(round, buildNinetyNineCard('3', 'CLUB'))).toBeNull();
  });

  it('is safe only when the server says so (the client holds no rule)', () => {
    const over = buildNinetyNineTwelveSeatRound();
    expect(isSafe(over, over.hand[0])).toBe(true);
    expect(isSafe(over, over.hand[1])).toBe(false);
    expect(isSafe(over, buildNinetyNineCard('3', 'CLUB'))).toBe(false);
  });

  it('lists the Ace values the server allows, and none for another card', () => {
    expect(aceValuesFor(round, ace)).toEqual([1, 11]);
    expect(aceValuesFor(round, seven)).toEqual([]);
  });

  it('asks the player to choose only when two Ace values are allowed', () => {
    expect(needsAceChoice(round, ace)).toBe(true);
    expect(needsAceChoice(round, seven)).toBe(false);
    const onlyOne = { ...round, playable: [{ card: ace, safe: true, aceValues: [1] }] };
    expect(needsAceChoice(onlyOne, ace)).toBe(false);
  });
});

import { describe, it, expect } from 'vitest';
import { lastPlayLine, totalDanger } from './totalView';
import { buildNinetyNineCard } from '../fixtures/ninetynine';
import { messagesEn } from '../../locales/en';
import type { NinetyNineLastPlay } from './NinetyNineState';

const ui = messagesEn.ninetyNineUi;
const nameOf = (seat: number) => (seat === 0 ? 'You' : `P${seat}`);
const play = (overrides: Partial<NinetyNineLastPlay>): NinetyNineLastPlay => ({
  seat: 1, card: buildNinetyNineCard('7', 'HEART'), aceValue: null, totalBefore: 10, totalAfter: 17, reversed: false, ...overrides,
});

describe('totalDanger', () => {
  it.each([[0, 'calm'], [79, 'calm'], [80, 'warm'], [89, 'warm'], [90, 'hot'], [99, 'hot']])('givenATotalOf%i_thenItIs_%s', (total, zone) => {
    expect(totalDanger(total)).toBe(zone);
  });
});

describe('lastPlayLine', () => {
  it('givenNoPlayYet_thenTheLineIsEmpty', () => {
    expect(lastPlayLine(null, nameOf, ui)).toBe('');
  });

  it('givenAPlainCard_thenItNamesWhoPlayedWhat', () => {
    expect(lastPlayLine(play({}), nameOf, ui)).toBe('P1 played 7 of hearts.');
  });

  it('givenAnAce_thenItNamesTheValueChosen', () => {
    const line = lastPlayLine(play({ card: buildNinetyNineCard('ACE', 'CLUB'), aceValue: 11 }), nameOf, ui);
    expect(line).toBe('P1 played Ace of clubs as +11.');
  });

  it('givenAFour_thenItSaysTheDirectionReversed', () => {
    const line = lastPlayLine(play({ card: buildNinetyNineCard('4', 'DIAMOND'), reversed: true }), nameOf, ui);
    expect(line).toBe('P1 played 4 of diamonds: reverse.');
  });

  it('givenMyOwnPlay_thenItUsesMyName', () => {
    expect(lastPlayLine(play({ seat: 0 }), nameOf, ui)).toBe('You played 7 of hearts.');
  });
});

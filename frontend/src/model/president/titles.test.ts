import { describe, it, expect } from 'vitest';
import { PRESIDENT_TITLES, titleKey, titleOrder, seatsByPlace } from './titles';
import { buildPresidentPlayer } from '../fixtures/president';

describe('titles', () => {
  it('lists the titles from the top of the table to the bottom', () => {
    expect(PRESIDENT_TITLES).toEqual(['PRESIDENT', 'VICE_PRESIDENT', 'NEUTRAL', 'VICE_SCUM', 'SCUM']);
  });

  it.each([
    ['PRESIDENT', 'president'],
    ['VICE_PRESIDENT', 'vicePresident'],
    ['NEUTRAL', 'neutral'],
    ['VICE_SCUM', 'viceScum'],
    ['SCUM', 'scum'],
  ] as const)('%s reads its text at presidentUi.titles.%s', (title, key) => {
    expect(titleKey(title)).toBe(key);
  });

  it('orders titles, an absent title last', () => {
    const sorted = [null, 'SCUM', 'PRESIDENT', 'NEUTRAL'].sort((a, b) =>
      titleOrder(a as never) - titleOrder(b as never));
    expect(sorted).toEqual(['PRESIDENT', 'NEUTRAL', 'SCUM', null]);
  });

  it('puts the seats in finishing order, those still playing after, by seat', () => {
    const players = [
      buildPresidentPlayer(0, { place: 2, out: true }),
      buildPresidentPlayer(1),
      buildPresidentPlayer(2, { place: 1, out: true }),
      buildPresidentPlayer(3),
    ];
    expect(seatsByPlace(players).map(p => p.seat)).toEqual([2, 0, 1, 3]);
  });
});

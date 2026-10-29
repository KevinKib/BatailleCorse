import { describe, it, expect } from 'vitest';
import { roundSummary } from './roundSummary';
import { kiloCard, kiloDeMerdeRoundOverState } from './fixtures';
import type { KiloDeMerdeRoundOutcome } from './KiloDeMerdeState';

const outcome = (overrides: Partial<KiloDeMerdeRoundOutcome>): KiloDeMerdeRoundOutcome => ({
  ...kiloDeMerdeRoundOverState.lastOutcome!, ...overrides,
});

describe('roundSummary', () => {
  it('names the last slapper who took a kilo card, with the card and its kilos', () => {
    const lines = roundSummary(outcome({
      draws: [{ seat: 2, card: kiloCard('KING', 'SPADE'), flushed: false, kilosAfter: 13 }],
    }));
    expect(lines).toEqual([{ code: 'LAST_DRAWS', seat: 2, card: kiloCard('KING', 'SPADE'), kg: 13 }]);
  });

  it('names a false slapper who took a kilo card', () => {
    const lines = roundSummary(outcome({
      kind: 'FALSE_SLAP', firstSlapper: 1, slapOrder: [1], holders: [],
      draws: [{ seat: 1, card: kiloCard('9', 'CLUB'), flushed: false, kilosAfter: 9 }],
    }));
    expect(lines).toEqual([{ code: 'FALSE_SLAP', seat: 1, card: kiloCard('9', 'CLUB'), kg: 9 }]);
  });

  it('a drawn 7 is a flush, whatever the kind of round', () => {
    expect(roundSummary(kiloDeMerdeRoundOverState.lastOutcome!)).toEqual([
      { code: 'FLUSH', seat: 2, card: kiloCard('7', 'HEART'), kg: 0 },
    ]);
    const falseFlush = roundSummary(outcome({
      kind: 'FALSE_SLAP', draws: [{ seat: 1, card: kiloCard('7', 'CLUB'), flushed: true, kilosAfter: 0 }],
    }));
    expect(falseFlush.map(l => l.code)).toEqual(['FLUSH']);
  });

  it('lists every drawer of a race that timed out, in draw order', () => {
    const lines = roundSummary(outcome({
      draws: [
        { seat: 2, card: kiloCard('5', 'CLUB'), flushed: false, kilosAfter: 5 },
        { seat: 3, card: kiloCard('7', 'CLUB'), flushed: true, kilosAfter: 0 },
      ],
    }));
    expect(lines.map(l => [l.code, l.seat])).toEqual([['LAST_DRAWS', 2], ['FLUSH', 3]]);
  });

  it('says nobody took kilos when the round stalled', () => {
    expect(roundSummary(outcome({ kind: 'STALLED', draws: [], holders: [], slapOrder: [], firstSlapper: null })))
      .toEqual([{ code: 'NOTHING', seat: null, card: null, kg: 0 }]);
  });

  it('names a drawer who found the kilo pile empty (a skipped draw), rather than saying nobody completed a set', () => {
    const lines = roundSummary(outcome({ draws: [{ seat: 2, card: null, flushed: false, kilosAfter: 4 }] }));
    expect(lines).toEqual([{ code: 'NO_CARD', seat: 2, card: null, kg: 0 }]);
    const timeout = roundSummary(outcome({
      draws: [
        { seat: 2, card: kiloCard('5', 'CLUB'), flushed: false, kilosAfter: 5 },
        { seat: 3, card: null, flushed: false, kilosAfter: 0 },
      ],
    }));
    expect(timeout.map(l => [l.code, l.seat])).toEqual([['LAST_DRAWS', 2], ['NO_CARD', 3]]);
  });

  it('says the round is void after a forfeit', () => {
    expect(roundSummary(outcome({ kind: 'VOIDED', draws: [] })).map(l => l.code)).toEqual(['VOIDED']);
  });

  it('is empty without an outcome', () => {
    expect(roundSummary(null)).toEqual([]);
  });
});

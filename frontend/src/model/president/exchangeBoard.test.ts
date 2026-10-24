import { describe, expect, it } from 'vitest';
import { exchangeBoard, exchangeLegKey } from './exchangeBoard';
import { buildPresidentState } from '../fixtures/president';

describe('exchangeLegKey', () => {
  it('givenAPairAndALeg_thenTheKeyIsHighDashLowColonLeg', () => {
    expect(exchangeLegKey({ high: 0, low: 2 }, 'best')).toBe('0-2:best');
    expect(exchangeLegKey({ high: 3, low: 1 }, 'back')).toBe('3-1:back');
  });

  it('givenTwoPairsOrLegs_thenTheKeysDiffer', () => {
    const keys = [
      exchangeLegKey({ high: 0, low: 3 }, 'best'), exchangeLegKey({ high: 0, low: 3 }, 'back'),
      exchangeLegKey({ high: 1, low: 2 }, 'best'), exchangeLegKey({ high: 1, low: 2 }, 'back'),
    ];
    expect(new Set(keys).size).toBe(4);
  });
});

describe('exchangeBoard (declared by the shared contract, implemented by the board chunk)', () => {
  it('givenAnyState_thenItIsNullForNow', () => {
    const state = buildPresidentState({ round: 2, phase: 'EXCHANGE', exchange: [{ high: 0, low: 2, count: 2, done: false }] });
    expect(exchangeBoard(state, 0, new Set())).toBeNull();
  });
});

import { describe, expect, it } from 'vitest';
import { holdValues, liveHeld, mergeHeld, type Held } from './heldValues';

describe('holdValues', () => {
  it('givenValuesByPlace_thenEachIsHeldUntilTheTime', () => {
    expect(holdValues({ 'slot-1-0': 3, discard: 7 }, 5000)).toEqual({
      'slot-1-0': { value: 3, until: 5000 },
      discard: { value: 7, until: 5000 },
    });
  });

  it('givenNoValue_thenNothingIsHeld', () => {
    expect(holdValues({}, 5000)).toEqual({});
  });
});

describe('mergeHeld', () => {
  it('givenAPlaceAlreadyHeld_thenItKeepsTheValueItShowedFirstAndTheLaterEnd', () => {
    const current: Held<number> = { a: { value: 1, until: 4000 } };
    const incoming: Held<number> = { a: { value: 2, until: 6000 }, b: { value: 9, until: 5000 } };
    expect(mergeHeld(current, incoming)).toEqual({ a: { value: 1, until: 6000 }, b: { value: 9, until: 5000 } });
  });

  it('givenAnEarlierEndOnIncoming_thenTheLaterEndOfTheCurrentOneStays', () => {
    expect(mergeHeld({ a: { value: 1, until: 9000 } }, { a: { value: 2, until: 4000 } })).toEqual({ a: { value: 1, until: 9000 } });
  });

  it('doesNotChangeItsArguments', () => {
    const current: Held<number> = { a: { value: 1, until: 4000 } };
    mergeHeld(current, { a: { value: 2, until: 6000 } });
    expect(current).toEqual({ a: { value: 1, until: 4000 } });
  });
});

describe('liveHeld', () => {
  it('givenAFlightThatHasLanded_thenItsValueIsDropped', () => {
    const held: Held<number> = { a: { value: 1, until: 4000 }, b: { value: 2, until: 6000 } };
    expect(liveHeld(held, 5000)).toEqual({ b: { value: 2, until: 6000 } });
    expect(liveHeld(held, 4000)).toEqual({ b: { value: 2, until: 6000 } });
    expect(liveHeld(held, 6000)).toEqual({});
  });
});

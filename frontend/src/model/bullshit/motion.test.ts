import { describe, it, expect } from 'vitest';
import { planDiscard, planTake, settleMs, resolveAnchor, displayedPlaces, TAKE_GHOSTS } from './motion';
import { sortHand } from './sortHand';
import { CLAIM_MODE_RANK, CLAIM_MODE_SUIT } from './claimMode';

const rect = (left: number, top: number, width: number, height: number) => ({ left, top, width, height });
const ace = { rank: 'ACE', suit: 'HEART', name: 'HEART_ACE' };
const king = { rank: 'KING', suit: 'SPADE', name: 'SPADE_KING' };
const at = (r: ReturnType<typeof rect>) => ({ kind: 'rect', rect: r });

describe('displayedPlaces', () => {
  const c = (rank: string, suit: string) => ({ rank, suit, name: `${suit}_${rank}` });
  // Sent by the server in no particular order.
  const sent = [c('KING', 'SPADE'), c('ACE', 'HEART'), c('7', 'CLUB'), c('7', 'SPADE'), c('2', 'DIAMOND')];

  for (const mode of [CLAIM_MODE_RANK, CLAIM_MODE_SUIT]) {
    it(`givenAHandSentUnsorted_thenEachSelectedCardHasItsPlaceInTheDisplayOrder_${mode}`, () => {
      const shown = sortHand(sent, mode);
      const places = displayedPlaces(shown, [sent[0], sent[2]]);
      expect(places.map(p => shown[p].name)).toEqual([sent[0].name, sent[2].name]);
    });
  }

  it('givenSeveralCards_thenPlacesFollowTheSelectionOrderAndUnknownCardsAreDropped', () => {
    const shown = sortHand(sent, CLAIM_MODE_RANK);
    expect(displayedPlaces(shown, [sent[3], c('QUEEN', 'CLUB'), sent[1]]).map(p => shown[p].name))
      .toEqual([sent[3].name, sent[1].name]);
  });
});

describe('planDiscard', () => {
  const pile = rect(200, 100, 90, 130);

  it('givenTwoCards_thenOneStaggeredTravelEachFromItsHandPlaceToThePileTiltedAndFading', () => {
    const plan = planDiscard([{ rect: rect(30, 500, 60, 90), card: ace }, { rect: rect(100, 500, 60, 90), card: king }], pile)!;
    expect(plan.movements).toHaveLength(2);
    const [first, second] = plan.movements;
    expect(first).toMatchObject({
      kind: 'travel', card: ace, flip: false, turnDown: false, byOpponent: false, marks: [], delayMs: 0,
      from: at(rect(30, 500, 60, 90)), landing: { tiltDeg: 3, fade: true }, easing: 'cubic-bezier(0.22, 0.8, 0.3, 1)',
    });
    expect(second).toMatchObject({ card: king, delayMs: 70, landing: { tiltDeg: -4, fade: true } });
    expect(second.from).toEqual(at(rect(100, 500, 60, 90)));
  });

  it('givenTheFlight_thenItLastsTheOldDurationAndFadesInTheLast8Percent', () => {
    const [m] = planDiscard([{ rect: rect(30, 500, 60, 90), card: ace }], pile)!.movements;
    expect(m.durationMs + m.holdAfterMs).toBeCloseTo(380, 9);
    expect(m.durationMs / (m.durationMs + m.holdAfterMs)).toBeCloseTo(0.92, 9);
  });

  it('givenAPileCardOfAnotherShape_thenTheCopyLandsOnItsCornerKeepingItsOwnProportions', () => {
    // 90 / 60 = 1.5: the copy is 135 high when landed, whatever the height of the pile card.
    const [m] = planDiscard([{ rect: rect(30, 500, 60, 90), card: ace }], pile)!.movements;
    expect(m.to).toEqual(at(rect(200, 100, 90, 135)));
  });

  it('givenACardWithoutSize_thenItIsSkippedButTheOthersKeepTheirPlaceInTheFan', () => {
    const plan = planDiscard([{ rect: rect(0, 0, 0, 0), card: ace }, { rect: rect(100, 500, 60, 90), card: king }], pile)!;
    expect(plan.movements).toHaveLength(1);
    expect(plan.movements[0]).toMatchObject({ card: king, delayMs: 70, landing: { tiltDeg: -4 } });
  });

  it('givenNoPileNoCardOnlyUnsizedCards_thenNoPlan', () => {
    expect(planDiscard([{ rect: rect(30, 500, 60, 90), card: ace }], null)).toBeNull();
    expect(planDiscard([], pile)).toBeNull();
    expect(planDiscard([{ rect: rect(0, 0, 0, 0), card: ace }], pile)).toBeNull();
  });
});

describe('planTake', () => {
  const source = rect(200, 100, 90, 130);
  const taker = rect(500, 20, 40, 60);

  it('givenABigPile_thenThreeBacksStaggeredAndTiltedLeavingThePileForTheTaker', () => {
    const plan = planTake(source, taker, 8)!;
    expect(plan.movements).toHaveLength(TAKE_GHOSTS);
    expect(plan.movements.map(m => m.delayMs)).toEqual([0, 90, 180]);
    expect(plan.movements.map(m => m.landing!.tiltDeg)).toEqual([0, -6, 12]);
    for (const m of plan.movements) {
      expect(m).toMatchObject({
        kind: 'travel', card: null, byOpponent: false, marks: [], from: at(source), to: at(taker),
        landing: { fade: true, scaleTo: 'centre' }, easing: 'cubic-bezier(0.5, 0, 0.75, 0.3)', durationMs: 414, holdAfterMs: 46,
      });
    }
  });

  it('givenASmallPile_thenOneBackPerCard', () => {
    expect(planTake(source, taker, 1)!.movements).toHaveLength(1);
    expect(planTake(source, taker, 2)!.movements).toHaveLength(2);
  });

  it('givenNoSourceNoTakerNoSizeOrNoCards_thenNoPlan', () => {
    expect(planTake(null, taker, 5)).toBeNull();
    expect(planTake(source, null, 5)).toBeNull();
    expect(planTake(rect(0, 0, 0, 0), taker, 5)).toBeNull();
    expect(planTake(source, taker, 0)).toBeNull();
  });
});

describe('settleMs', () => {
  it('givenTheTakeFlight_thenTheLastCopyHasLandedAfter640Ms', () => {
    expect(settleMs(planTake(rect(0, 0, 90, 130), rect(500, 20, 40, 60), 8)!)).toBe(640);
  });
  it('givenNoPlan_thenZero', () => {
    expect(settleMs(null)).toBe(0);
  });
});

describe('resolveAnchor', () => {
  it('givenARectAnchor_thenThatRectWithNoCardToHide', () => {
    expect(resolveAnchor(at(rect(1, 2, 3, 4)) as never)).toEqual({ rect: rect(1, 2, 3, 4), cards: [] });
  });
});

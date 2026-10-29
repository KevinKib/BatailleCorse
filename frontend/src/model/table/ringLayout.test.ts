import { describe, it, expect } from 'vitest';
import {
  boxGap, centerBox, centerRowSizes, centerRowWidth, chooseLayout, directionArrows, plateSizeFor, ringBoxes, ringCenters, ringMinGap, MIN_GAP,
} from './ringLayout';

const frame = (width: number, height: number) => ({ width, height });

describe('ringCenters', () => {
  it('givenAnySeatCount_thenTheFirstSeatIsMineAtTheBottomAndTheOthersFollowOnScreenClockwise', () => {
    const f = frame(400, 400);
    const centers = ringCenters(4, f, 0.4, 0.4);
    expect(centers[0].x).toBeCloseTo(200);
    expect(centers[0].y).toBeCloseTo(200 + 160); // bottom
    expect(centers[1].x).toBeCloseTo(200 - 160); // then the left
    expect(centers[2].y).toBeCloseTo(200 - 160); // then the top
    expect(centers[3].x).toBeCloseTo(200 + 160); // then the right
  });

  it('givenOneSeat_thenItIsAtTheBottom', () => {
    expect(ringCenters(1, frame(300, 300), 0.4, 0.4)).toHaveLength(1);
  });
});

describe('boxGap', () => {
  it('givenTwoBoxesApartHorizontally_thenTheGapIsTheClearSpaceBetweenThem', () => {
    expect(boxGap({ x: 0, y: 0, w: 10, h: 10 }, { x: 18, y: 0, w: 10, h: 10 })).toBe(8);
  });

  it('givenTwoOverlappingBoxes_thenTheGapIsNegative', () => {
    expect(boxGap({ x: 0, y: 0, w: 10, h: 10 }, { x: 5, y: 5, w: 10, h: 10 })).toBeLessThan(0);
  });
});

describe('plateSizeFor', () => {
  it('givenAPhoneFrame_thenThePlateIsTheCompactOne', () => {
    expect(plateSizeFor(351)).toEqual({ w: 60, h: 40 });
  });

  it('givenAWideFrame_thenThePlateIsTheFullOne', () => {
    expect(plateSizeFor(1000)).toEqual({ w: 116, h: 52 });
  });
});

describe('the ring of 12 seats keeps 8 px between plates (spacing rule)', () => {
  // Frames are the felt of the table: the viewport minus the gutters and the top bar / hand / footer.
  it.each([
    [375, 667, 359, 395],
    [360, 640, 344, 365],
    [390, 844, 374, 560],
    [768, 1024, 752, 640],
    [1280, 720, 1100, 330],
  ])('at %ix%i (felt %ix%i)', (_vw, _vh, w, h) => {
    const f = frame(w, h);
    expect(ringMinGap(12, f)).toBeGreaterThanOrEqual(MIN_GAP);
    // the plates stay inside the felt and never touch the middle block
    for (const box of ringBoxes(12, f, plateSizeFor(w))) {
      expect(box.x).toBeGreaterThanOrEqual(0);
      expect(box.x + box.w).toBeLessThanOrEqual(w);
      expect(box.y).toBeGreaterThanOrEqual(0);
      expect(box.y + box.h).toBeLessThanOrEqual(h);
    }
  });

  it.each([2, 3, 4, 6, 8, 11])('givenASmallerTable_thenItAlsoKeepsTheGap_%i', (count) => {
    expect(ringMinGap(count, frame(344, 365))).toBeGreaterThanOrEqual(MIN_GAP);
  });
});

describe('the middle block', () => {
  it('givenTwelveSeatsOnAPhone_thenNoPlateTouchesTheMiddleBlock', () => {
    const f = frame(344, 365);
    const middle = centerBox(f);
    for (const box of ringBoxes(12, f, plateSizeFor(344))) expect(boxGap(box, middle)).toBeGreaterThanOrEqual(MIN_GAP);
  });
});

describe('the centre row (pile, total, stock)', () => {
  it.each([[344, 365], [1100, 420]])('givenAFeltOf%ix%i_thenTheRowWithAThreeDigitTotalFitsTheMiddleBlock', (w, h) => {
    const f = frame(w, h);
    const sizes = centerRowSizes(w < 600);
    // the stock's count badge sticks out on the right
    expect(centerRowWidth(sizes) + sizes.badgeOverhang).toBeLessThanOrEqual(centerBox(f).w);
  });

  it('givenTheTotal_thenItsSlotIsReservedForThreeDigits', () => {
    const sizes = centerRowSizes(false);
    expect(sizes.totalW).toBeGreaterThanOrEqual(Math.ceil(sizes.totalSize * 0.6 * 3));
  });
});

describe('directionArrows', () => {
  const f = frame(344, 365);

  const wide = frame(1100, 420);

  it('givenTheNormalDirection_thenTheArrowAtTheTopPointsRight_whichIsClockwiseOnScreen', () => {
    const top = directionArrows(wide, false).find(a => a.angle === 90)!;
    expect(top.tip.x).toBeGreaterThan(top.base.x);
  });

  it('givenAReversedDirection_thenTheArrowAtTheTopPointsLeft', () => {
    const top = directionArrows(wide, true).find(a => a.angle === 90)!;
    expect(top.tip.x).toBeLessThan(top.base.x);
  });

  it('givenAPhone_thenFourArrowsAreDrawn', () => {
    expect(directionArrows(f, false)).toHaveLength(4);
  });

  it('givenTheArrows_thenTheyStayClearOfTheMiddleBlock', () => {
    const middle = centerBox(f);
    for (const a of directionArrows(f, false)) {
      expect(boxGap({ x: a.at.x - 6, y: a.at.y - 6, w: 12, h: 12 }, middle)).toBeGreaterThanOrEqual(0);
    }
  });

  it('givenTheArrows_thenEachIsATriangleOfThreePoints', () => {
    for (const a of directionArrows(f, false)) expect(a.points).toMatch(/^[\d. -]+,[\d. -]+ [\d. -]+,[\d. -]+ [\d. -]+,[\d. -]+$/);
  });
});

describe('chooseLayout', () => {
  it('givenAFrameWhereTheRingKeepsTheGap_thenTheRingIsUsed', () => {
    expect(chooseLayout(12, frame(344, 365))).toBe('ring');
    expect(chooseLayout(4, frame(1100, 330))).toBe('ring');
  });

  it('givenAFrameTooNarrowForTheGap_thenTheRailIsUsed', () => {
    expect(chooseLayout(12, frame(300, 365))).toBe('rail');
  });

  it('givenAFrameTooShortForTheGap_thenTheRailIsUsed', () => {
    expect(chooseLayout(12, frame(344, 200))).toBe('rail');
  });

  it('givenNoMeasureYet_thenTheRingIsUsed', () => {
    expect(chooseLayout(12, null)).toBe('ring');
  });
});

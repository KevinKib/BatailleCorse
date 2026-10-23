import { describe, expect, it } from 'vitest';
import { CARD_RATIO, cardAt, cardIn, visibleRect } from './rect';

const box = (left: number, top: number, width: number, height: number): HTMLElement => {
  const el = document.createElement('div');
  el.getBoundingClientRect = () => ({ left, top, width, height, right: left + width, bottom: top + height, x: left, y: top, toJSON() {} }) as DOMRect;
  return el;
};

describe('CARD_RATIO', () => {
  it('isTheHeightOverTheWidthOfACard', () => {
    expect(CARD_RATIO).toBeCloseTo(243.1375 / 167.575, 10);
  });
});

describe('visibleRect', () => {
  it('givenAnElementOnScreen_thenItsRectangle', () => {
    expect(visibleRect(box(10, 20, 30, 40))).toEqual({ left: 10, top: 20, width: 30, height: 40 });
  });

  it('givenAnEmptyOrMissingElement_thenNull', () => {
    expect(visibleRect(box(10, 20, 0, 40))).toBeNull();
    expect(visibleRect(box(10, 20, 30, 0))).toBeNull();
    expect(visibleRect(null)).toBeNull();
    expect(visibleRect(undefined)).toBeNull();
  });
});

describe('cardAt', () => {
  it('givenABoxAndAWidth_thenACardSizedRectangleInTheMiddle', () => {
    const rect = cardAt({ left: 100, top: 200, width: 100, height: 300 }, 40);
    expect(rect.left).toBe(130);
    expect(rect.width).toBe(40);
    expect(rect.height).toBeCloseTo(40 * CARD_RATIO, 10);
    expect(rect.top).toBeCloseTo(200 + (300 - 40 * CARD_RATIO) / 2, 10);
  });
});

describe('cardIn', () => {
  it('givenNoHost_thenNull', () => {
    expect(cardIn(null)).toBeNull();
  });

  it('givenAPlayingCard_thenItsRectangleAndTheCardToHide', () => {
    const host = box(0, 0, 100, 100);
    const card = box(5, 6, 50, 70);
    card.className = 'playing_card';
    host.appendChild(card);
    const target = cardIn(host);
    expect(target?.rect).toEqual({ left: 5, top: 6, width: 50, height: 70 });
    expect(target?.cards).toEqual([card]);
  });

  it('givenAnEmptyPilePlaceholder_thenItsRectangleAndNoCard', () => {
    const host = box(0, 0, 100, 100);
    const empty = box(8, 9, 44, 60);
    empty.className = 'pile-empty';
    host.appendChild(empty);
    expect(cardIn(host)).toEqual({ rect: { left: 8, top: 9, width: 44, height: 60 }, cards: [] });
  });

  it('givenAnEmptyPlace_thenACardSizedRectangleInTheMiddleOfItsBox', () => {
    const target = cardIn(box(0, 0, 200, 300));
    expect(target?.rect.width).toBe(44);
    expect(target?.rect.left).toBe(78);
    expect(target?.cards).toEqual([]);
  });

  it('givenAFallbackWidthAndANarrowBox_thenTheNarrowerOne', () => {
    expect(cardIn(box(0, 0, 30, 300), 60)?.rect.width).toBe(30);
    expect(cardIn(box(0, 0, 200, 300), 60)?.rect.width).toBe(60);
  });

  it('givenAnInvisibleHost_thenNull', () => {
    expect(cardIn(box(0, 0, 0, 0))).toBeNull();
  });
});

import { CARD_RATIO, type MotionRect } from '../../animation';

/** How far a card covers the one before it in a row of cards (the CSS reads it from the same number). */
export const overlapOf = (cardWidth: number): number => Math.round(cardWidth * 0.3);

/**
 * Where the card at `index` of a row lands, in viewport pixels: the row is centred on the middle of its box (a hand
 * or the dealer's cards) and holds `index + 1` cards once the card is there. `box` is the row as it is now, so only
 * its middle and its height matter. Pure.
 */
export function slotRect(box: MotionRect, cardWidth: number, index: number): MotionRect {
  const step = cardWidth - overlapOf(cardWidth);
  const rowWidth = cardWidth + index * step;
  const height = cardWidth * CARD_RATIO;
  return {
    left: box.left + box.width / 2 - rowWidth / 2 + index * step,
    top: box.top + (box.height - height) / 2,
    width: cardWidth,
    height,
  };
}

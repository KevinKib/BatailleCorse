/** A place of the screen, in viewport pixels. */
export interface MotionRect { left: number; top: number; width: number; height: number }

/** What a table's place is on screen, for `CardMotionLayer`. */
export interface MotionTarget {
  rect: MotionRect;
  /** Cards of the table hidden while a copy lands on them (the real card is under it); shown again at the end. */
  cards: HTMLElement[];
}

/** The height of a card over its width. */
export const CARD_RATIO = 243.1375 / 167.575;

/** The rectangle of an element, or null when it is missing or has no size on screen. */
export function visibleRect(el: Element | null | undefined): MotionRect | null {
  if (!el) return null;
  const r = el.getBoundingClientRect();
  return r.width > 0 && r.height > 0 ? { left: r.left, top: r.top, width: r.width, height: r.height } : null;
}

/** A card-sized rectangle, `width` wide, in the middle of a box. */
export function cardAt(box: MotionRect, width: number): MotionRect {
  const height = width * CARD_RATIO;
  return { left: box.left + (box.width - width) / 2, top: box.top + (box.height - height) / 2, width, height };
}

/**
 * The card inside a place: its picture (which the layer hides while a copy lands on it), or the rectangle of an empty
 * pile's outline, or a card-sized rectangle (at most `fallbackWidth` wide) in the middle of the place. A card leaving
 * or landing on an empty place has the same size and position as when the place holds a card: no jump between the two.
 */
export function cardIn(host: Element | null, fallbackWidth = 44): MotionTarget | null {
  if (!host) return null;
  const card = host.querySelector<HTMLElement>('.playing_card');
  const rect = card ? visibleRect(card) : null;
  if (card && rect) return { rect, cards: [card] };
  const placeholder = host.querySelector<HTMLElement>('.pile-empty');
  const placeholderRect = placeholder ? visibleRect(placeholder) : null;
  if (placeholderRect) return { rect: placeholderRect, cards: [] };
  const box = visibleRect(host);
  if (!box) return null;
  return { rect: cardAt(box, Math.min(box.width, fallbackWidth)), cards: [] };
}

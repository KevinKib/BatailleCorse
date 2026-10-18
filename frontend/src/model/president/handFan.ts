/** The most card backs a seat's fan draws; past it, only the number grows. */
export const FAN_MAX_BACKS = { wide: 13, narrow: 9 } as const;

const CARD_W = { wide: 22, narrow: 16 } as const;
const STEP = { wide: 9, narrow: 7 } as const;
const MAX_WIDTH = { wide: 96, narrow: 60 } as const;

export interface FanLayout {
  /** How many backs to draw. */
  backs: number;
  /** Width of one back, in px. */
  cardW: number;
  /** Distance between two backs, in px (the fan tightens as it grows). */
  stepPx: number;
  /** The fan never gets wider than this, in px. */
  maxWidth: number;
}

/**
 * The fan of card backs that shows how many cards a seat still holds: one back per card up to a cap, overlapping
 * more as it grows so that eight seats fit on a phone. The number is always shown beside it. Pure.
 */
export function fanLayout(count: number, narrow: boolean): FanLayout {
  const size = narrow ? 'narrow' : 'wide';
  const backs = Math.max(0, Math.min(count, FAN_MAX_BACKS[size]));
  const cardW = CARD_W[size];
  const maxWidth = MAX_WIDTH[size];
  const stepPx = backs > 1 ? Math.min(STEP[size], (maxWidth - cardW) / (backs - 1)) : STEP[size];
  return { backs, cardW, stepPx, maxWidth };
}

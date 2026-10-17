/** Card widths (px) of a Kobo table; `null` leaves the stylesheet's default for that grid. */
export interface KoboCardWidths {
  /** Width of a card in an opponent's grid. */
  opponent: number | null;
  /** Width of a card in my grid. */
  mine: number | null;
}

/**
 * Two players leave the table room for bigger cards, with the same layout as every other count: the widths
 * grow, nothing moves. From two opponents up the defaults stay. Layout only.
 */
export function koboCardWidths(opponentCount: number, narrow: boolean): KoboCardWidths {
  if (opponentCount !== 1) return { opponent: null, mine: null };
  return narrow ? { opponent: 44, mine: 54 } : { opponent: 52, mine: 68 };
}

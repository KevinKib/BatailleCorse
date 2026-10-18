import type Card from '../Card';

/**
 * A card that moves on a table, from one place to another, as the table names its places (`Anchor`: the screen turns
 * it into a rectangle). Shared by every table that animates (Kobo, President) and played by `CardMotionLayer`. Purely
 * visual: nothing here is read back by the game logic.
 */
export interface TableMovement<Anchor> {
  /**
   * `travel`: one way. `glance`: out and back (a peek). `reveal`: every card turns over in place. `ring`: no card
   * moves, only the rings of `marks` show (a place the event points at, such as the winner of a trick).
   */
  kind: 'travel' | 'glance' | 'reveal' | 'ring';
  from: Anchor;
  to: Anchor;
  /** The face shown in flight; null is a card back. */
  card: Card | null;
  /** Starts face down and turns face up on the way. */
  flip: boolean;
  /** Travels face up and turns face down once landed. */
  turnDown: boolean;
  /**
   * What the destination keeps showing until the copy lands, so no card seems to vanish or appear from nowhere:
   * undefined nothing, null a card back, a card its face.
   */
  restsOn?: Card | null;
  /** The source keeps showing a card back until the copy leaves it. */
  leavesBack?: boolean;
  actor: number;
  /** An opponent's move gets a faint arrow as well: the one place where reading the table is hard. */
  byOpponent: boolean;
  /** The hold on the source before the card leaves (plus the wait behind a previous move of the same event). */
  delayMs: number;
  /** The flight itself. */
  durationMs: number;
  /** The hold on the destination once landed. */
  holdAfterMs: number;
  /** The places to ring while this move plays (dashed on its source, plain elsewhere). */
  marks: Anchor[];
}

/** When the last flight of an event is over (the holds after landing excluded): the next event may start then. */
export function eventSpanMs(movements: readonly Pick<TableMovement<unknown>, 'delayMs' | 'durationMs'>[]): number {
  return movements.reduce((span, m) => Math.max(span, m.delayMs + m.durationMs), 0);
}

/** The same movements played `factor` times as long (0.5: twice as fast). */
export function scaleMovements<M extends Pick<TableMovement<unknown>, 'delayMs' | 'durationMs' | 'holdAfterMs'>>(
  movements: readonly M[], factor: number,
): M[] {
  if (factor === 1) return [...movements];
  const scale = (ms: number) => Math.round(ms * factor);
  return movements.map(m => ({
    ...m, delayMs: scale(m.delayMs), durationMs: scale(m.durationMs), holdAfterMs: scale(m.holdAfterMs),
  }));
}

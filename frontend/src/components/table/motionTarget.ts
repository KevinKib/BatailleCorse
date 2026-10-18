/** A place of the screen, in viewport pixels. */
export interface MotionRect { left: number; top: number; width: number; height: number }

/** What a table's place is on screen, for `CardMotionLayer`. */
export interface MotionTarget {
  rect: MotionRect;
  /** Cards of the table hidden while a copy lands on them (the real card is under it); shown again at the end. */
  cards: HTMLElement[];
}

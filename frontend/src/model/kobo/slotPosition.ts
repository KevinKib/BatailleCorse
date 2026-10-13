/** A cell of a tableau grid: row 0 is the top row, row 1 the bottom one. */
export interface SlotCell {
  row: 0 | 1;
  col: number;
}

/**
 * Where a slot sits in its owner's grid. Slots 0 and 1 are the bottom row of the dealt square, 2 and 3 the
 * top row; every further slot (penalty cards) opens a new column on the right, bottom first. Layout only:
 * the slot index stays the server's.
 */
export function slotPosition(slot: number): SlotCell {
  if (slot < 4) return { row: slot < 2 ? 1 : 0, col: slot % 2 };
  const extra = slot - 4;
  return { row: extra % 2 === 0 ? 1 : 0, col: 2 + Math.floor(extra / 2) };
}

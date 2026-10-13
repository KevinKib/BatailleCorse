/**
 * Angles (degrees) around the table for `count` opponents, skipping the bottom (my zone).
 * 0 = right, 90 = top, 180 = left. A single opponent sits at the top; a pair sits on the top
 * corners; 3 or more spread evenly from left, over the top, to the right.
 */
export function seatAngles(count: number): number[] {
  if (count <= 0) return [];
  if (count === 1) return [90];
  if (count === 2) return [135, 45];
  return Array.from({ length: count }, (_, i) => 180 - i * (180 / (count - 1)));
}

/** Horizontal radius of the table ellipse (% of the frame). */
const RX = 44;
/** Vertical radius (% of the frame): keeps the top seat inside the oval. */
const RY = 36;

/** Maps each of `count` opponents to a point on the table ellipse, in percent of the frame. */
export function seatPositions(count: number): { left: number; top: number }[] {
  return seatAngles(count).map(deg => {
    const r = (deg * Math.PI) / 180;
    return { left: 50 + RX * Math.cos(r), top: 50 - RY * Math.sin(r) };
  });
}

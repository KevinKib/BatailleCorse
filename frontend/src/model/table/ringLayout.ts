/**
 * The geometry of the 99 table: up to 12 plates on an ellipse around the middle block (total, pile, stock), me at the
 * bottom. Pure, so that the spacing rule (8 px between independent controls, `frontend-spacing`) is checked in a unit
 * test for the largest table, and so that the screen can fall back to the rail of seats when a felt is too small for
 * the ring. The CSS of the table reads the same numbers (`--nn-*` custom properties set from here).
 */

export interface Frame { width: number; height: number }
export interface Box { x: number; y: number; w: number; h: number }
export interface PlateSize { w: number; h: number }

/** Two independent controls never touch: at least this many px between their boxes (`--space-2`). */
export const MIN_GAP = 8;

/** The felt is a phone one below this width (px): compact plates, a smaller middle block. */
export const WIDE_FELT_MIN_WIDTH = 600;

const isWide = (width: number) => width >= WIDE_FELT_MIN_WIDTH;

/** A plate: the full one (name, hearts, cards) on a wide felt, the compact one (name, hearts) on a phone. */
export function plateSizeFor(frameWidth: number): PlateSize {
  return isWide(frameWidth) ? { w: 116, h: 52 } : { w: 60, h: 40 };
}

/** Radii of the ring as a share of the felt; the side plates stay inside the felt (2 px to spare). */
export function radiiFor(frame: Frame): { rx: number; ry: number } {
  const plate = plateSizeFor(frame.width);
  const wide = isWide(frame.width);
  return {
    rx: Math.min(wide ? 0.44 : 0.4, 0.5 - (plate.w / 2 + 2) / frame.width),
    // a phone felt is tall and narrow: the ring is taller there, to keep the plates of 12 seats apart
    ry: Math.min(wide ? 0.42 : 0.44, 0.5 - (plate.h / 2 + 2) / frame.height),
  };
}

/** The middle block (direction chip, pile, total, stock, last move) kept clear of the plates. */
export function centerBox(frame: Frame): Box {
  const wide = isWide(frame.width);
  const w = wide ? 340 : 156;
  const h = wide ? 150 : 112;
  // Slightly above the middle of the felt: the plates of the lower half are lower than the ones of the upper half.
  const cy = frame.height * 0.46;
  return { x: frame.width / 2 - w / 2, y: cy - h / 2, w, h };
}

/** The total is never wider than this many digits (0 to 100): its slot is reserved for all of them at all times. */
export const TOTAL_RESERVED_DIGITS = 3;
/** A tabular digit is about this wide, in em, in the title font. */
const DIGIT_EM = 0.6;
/** The gap between the pile, the total and the stock. */
export const CENTER_GAP = 6;

export interface CenterRowSizes {
  pileW: number;
  totalSize: number;
  /** The reserved width of the total's slot: three digits, whatever the total. */
  totalW: number;
  gap: number;
  /** How far the stock's count badge sticks out on the right of the row. */
  badgeOverhang: number;
}

/** The pile, total and stock sizes, all derived from the three-digit reserve so that the row fits `centerBox`. */
export function centerRowSizes(compact: boolean): CenterRowSizes {
  const totalSize = compact ? 36 : 76;
  return {
    pileW: compact ? 36 : 64,
    totalSize,
    totalW: Math.ceil(totalSize * DIGIT_EM * TOTAL_RESERVED_DIGITS),
    gap: CENTER_GAP,
    badgeOverhang: 6,
  };
}

/** The width of the row (pile, total, stock), without the badge. */
export function centerRowWidth(s: CenterRowSizes): number {
  return s.pileW * 2 + s.gap * 2 + s.totalW;
}

/** The centre of each of `count` plates: seat 0 (me) at the bottom, the next seats on screen clockwise. */
export function ringCenters(count: number, frame: Frame, rx: number, ry: number): { x: number; y: number }[] {
  return Array.from({ length: count }, (_, k) => {
    const angle = ((270 - (360 / count) * k) * Math.PI) / 180;
    return {
      x: frame.width / 2 + rx * frame.width * Math.cos(angle),
      y: frame.height / 2 - ry * frame.height * Math.sin(angle),
    };
  });
}

/** The box of each plate. */
export function ringBoxes(count: number, frame: Frame, plate: PlateSize = plateSizeFor(frame.width)): Box[] {
  const { rx, ry } = radiiFor(frame);
  return ringCenters(count, frame, rx, ry).map(c => ({ x: c.x - plate.w / 2, y: c.y - plate.h / 2, w: plate.w, h: plate.h }));
}

/** The clear space between two boxes (the larger of the horizontal and vertical gaps, as `cypress/support/spacing.ts`); negative when they overlap. */
export function boxGap(a: Box, b: Box): number {
  const horizontal = Math.max(a.x, b.x) - Math.min(a.x + a.w, b.x + b.w);
  const vertical = Math.max(a.y, b.y) - Math.min(a.y + a.h, b.y + b.h);
  return Math.max(horizontal, vertical);
}

/** The smallest gap between two plates, or between a plate and the middle block; negative when a plate leaves the felt. */
export function ringMinGap(count: number, frame: Frame): number {
  const boxes = ringBoxes(count, frame);
  const middle = centerBox(frame);
  let min = Infinity;
  boxes.forEach((box, i) => {
    min = Math.min(min, boxGap(box, middle));
    for (let j = i + 1; j < boxes.length; j++) min = Math.min(min, boxGap(box, boxes[j]));
    // a plate must also stay inside the felt (no gap is asked from its edge, only that it does not leave)
    const inside = Math.min(box.x, box.y, frame.width - (box.x + box.w), frame.height - (box.y + box.h));
    if (inside < 0) min = Math.min(min, inside);
  });
  return min;
}

/** The dashed track the arrows sit on: inside the ring of plates, around the middle block. */
export function trackRadii(frame: Frame): { rx: number; ry: number } {
  return { rx: frame.width * 0.34, ry: frame.height * 0.3 };
}

export interface DirectionArrow {
  angle: number;
  /** The middle of the arrow. */
  at: { x: number; y: number };
  base: { x: number; y: number };
  tip: { x: number; y: number };
  /** The three corners, as an SVG `points` attribute. */
  points: string;
}

/**
 * Small arrowheads on the track, all pointing the way play goes. Seat order is clockwise on screen (the plates are laid
 * out that way); a reversed direction flips every arrow.
 */
export function directionArrows(frame: Frame, reversed: boolean): DirectionArrow[] {
  const { rx, ry } = trackRadii(frame);
  const cx = frame.width / 2;
  const cy = frame.height / 2;
  const angles = isWide(frame.width) ? [30, 90, 150, 210, 270, 330] : [45, 135, 225, 315];
  const sign = reversed ? -1 : 1;
  const wide = isWide(frame.width);
  const length = wide ? 8 : 7;
  const half = wide ? 6 : 5;
  const round = (n: number) => Math.round(n * 10) / 10;
  return angles.map((angle) => {
    const t = (angle * Math.PI) / 180;
    const at = { x: cx + rx * Math.cos(t), y: cy - ry * Math.sin(t) };
    // Clockwise on screen is the way the angle decreases: the tangent there (screen coordinates, y down).
    let ux = sign * rx * Math.sin(t);
    let uy = sign * ry * Math.cos(t);
    const norm = Math.hypot(ux, uy);
    ux /= norm;
    uy /= norm;
    const tip = { x: at.x + ux * length, y: at.y + uy * length };
    const back = { x: at.x - ux * (length * 0.6), y: at.y - uy * (length * 0.6) };
    const left = { x: back.x - uy * half, y: back.y + ux * half };
    const right = { x: back.x + uy * half, y: back.y - ux * half };
    return {
      angle, at, base: back, tip,
      points: `${round(tip.x)},${round(tip.y)} ${round(left.x)},${round(left.y)} ${round(right.x)},${round(right.y)}`,
    };
  });
}

export type TableLayout = 'ring' | 'rail';

/**
 * The ring when the felt keeps `MIN_GAP` between every plate (and the middle block), else the rail: the seats as a
 * grid in turn order above the middle block. An unmeasured felt gets the ring.
 */
export function chooseLayout(count: number, frame: Frame | null): TableLayout {
  if (!frame || frame.width <= 0 || frame.height <= 0) return 'ring';
  return ringMinGap(count, frame) >= MIN_GAP ? 'ring' : 'rail';
}

/**
 * Where a message of the table (a banner) can sit without touching a plate: in the free band, above or below the
 * middle block (the larger one), as wide as the middle block, 8 px clear of every plate and of the block. The band
 * can be thin on a crowded phone felt: the banner clamps its text to the height it is given.
 */
export function bannerBox(count: number, frame: Frame): Box {
  const middle = centerBox(frame);
  const plates = ringBoxes(count, frame);
  const inColumn = plates.filter(p => p.x < middle.x + middle.w + MIN_GAP && p.x + p.w + MIN_GAP > middle.x);
  const above = inColumn.filter(p => p.y + p.h <= middle.y + middle.h / 2);
  const below = inColumn.filter(p => p.y >= middle.y + middle.h / 2);
  const aboveTop = Math.max(0, ...above.map(p => p.y + p.h + MIN_GAP));
  const aboveBottom = middle.y - MIN_GAP;
  const belowTop = middle.y + middle.h + MIN_GAP;
  const belowBottom = Math.min(frame.height, ...below.map(p => p.y - MIN_GAP));
  const roomAbove = aboveBottom - aboveTop;
  const roomBelow = belowBottom - belowTop;
  return roomAbove >= roomBelow
    ? { x: middle.x, y: aboveTop, w: middle.w, h: Math.max(0, roomAbove) }
    : { x: middle.x, y: belowTop, w: middle.w, h: Math.max(0, roomBelow) };
}

import type { MotionPlan, MotionTarget, TableMovement, MotionRect } from '../../animation';
import type Card from '../Card';

// The two pictures of a Bullshit table, as plans for the shared card layer: my discarded cards flying from where they
// were in my hand to the pile, and the pile (a few backs) flying to the player who takes it. Pure and unit-tested. Purely
// visual: the pile and the counts come from the state, a missed picture hides nothing.

/** Both ends of a flight are rectangles measured when the move happens (my hand cards, the pile, the taker). */
export type BullshitAnchor = { kind: 'rect'; rect: MotionRect };
type Movement = TableMovement<BullshitAnchor>;

/** The level of the layer over the page (the copies fly over the notices of the table). */
export const BULLSHIT_LAYER_LEVEL = 3000;

// Each copy is one animation of flightMs + fadeMs (380 ms: 92% flying, 8% fading; 460 ms: 90% / 10%), as before.
export const DISCARD = {
  flightMs: 349.6,
  fadeMs: 30.4,
  staggerMs: 70,
  easing: 'cubic-bezier(0.22, 0.8, 0.3, 1)',
};
export const TAKE = {
  flightMs: 414,
  fadeMs: 46,
  staggerMs: 90,
  easing: 'cubic-bezier(0.5, 0, 0.75, 0.3)',
};
/** How many backs fly with a pile, however big it is. */
export const TAKE_GHOSTS = 3;
/** A burst never queues: a discard and a taken pile are a whole verdict apart. */
export const BULLSHIT_QUEUE = { maxQueued: 4, maxLagMs: 3500 };

const at = (rect: MotionRect): BullshitAnchor => ({ kind: 'rect', rect });
const sized = (r: MotionRect) => r.width > 0;

/** Where a flight starts or ends: the measured rectangle itself, with no card of the table to hide under it. */
export function resolveAnchor(anchor: BullshitAnchor): MotionTarget {
  return { rect: anchor.rect, cards: [] };
}

/**
 * My discarded cards, each from the place it had in my hand to the pile: it grows to the pile card's width, lands on
 * its corner, tilted a little more for each card (cards dropped on a pile) and fades once landed. Null when there is
 * nothing to fly or nowhere to fly it to.
 */
export function planDiscard(sources: { rect: MotionRect; card: Card }[], pile: MotionRect | null): MotionPlan<BullshitAnchor> | null {
  if (!pile) return null;
  const movements: Movement[] = [];
  sources.forEach(({ rect, card }, i) => {
    if (!sized(rect)) return;
    movements.push({
      kind: 'travel', from: at(rect),
      // Landing on the pile card's corner, keeping the hand card's proportions.
      to: at({ left: pile.left, top: pile.top, width: pile.width, height: rect.height * (pile.width / rect.width) }),
      card, flip: false, turnDown: false, actor: 0, byOpponent: false,
      delayMs: i * DISCARD.staggerMs, durationMs: DISCARD.flightMs, holdAfterMs: DISCARD.fadeMs, marks: [],
      landing: { tiltDeg: (i % 2 === 0 ? 1 : -1) * (3 + i), fade: true },
      easing: DISCARD.easing,
    });
  });
  return movements.length > 0 ? { movements } : null;
}

/**
 * A few face-down copies of the pile (one per card, at most three) to the player who takes it: they shrink (never
 * enlarged, never under a third) onto the centre of the taker's place and fade as they arrive.
 */
export function planTake(pile: MotionRect | null, taker: MotionRect | null, pileSize: number): MotionPlan<BullshitAnchor> | null {
  if (!pile || !taker || !sized(pile) || pileSize <= 0) return null;
  const movements: Movement[] = [];
  for (let i = 0; i < Math.min(TAKE_GHOSTS, pileSize); i++) {
    movements.push({
      kind: 'travel', from: at(pile), to: at(taker), card: null, flip: false, turnDown: false, actor: 0, byOpponent: false,
      delayMs: i * TAKE.staggerMs, durationMs: TAKE.flightMs, holdAfterMs: TAKE.fadeMs, marks: [],
      landing: { tiltDeg: (i % 2 === 0 ? 1 : -1) * i * 6, fade: true, scaleTo: 'centre' },
      easing: TAKE.easing,
    });
  }
  return { movements };
}

/** How long until the last copy of a plan has landed and faded, counted from when it starts. */
export function settleMs(plan: MotionPlan<BullshitAnchor> | null): number {
  return plan?.movements.reduce((end, m) => Math.max(end, m.delayMs + m.durationMs + m.holdAfterMs), 0) ?? 0;
}

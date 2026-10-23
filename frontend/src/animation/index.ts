// The public API of the shared card animation module. A game imports this barrel (`../../animation`), never a file
// inside the module (checked by `boundaries.test.ts`). See README.md for how a table plugs in.

// core: pure TypeScript
export type { TableMovement, MotionKind, MotionPlan } from './core/movement';
export { eventSpanMs, scaleMovements, scalePlan } from './core/movement';
export { leg, type LegTempo, type QueueBounds } from './core/tempo';
export { createTempoQueue, type Admission } from './core/tempoQueue';
export { ANIMATION_SPEEDS, nextAnimationSpeed, speedFactor, type AnimationSpeed } from './core/speed';
export { prefersReducedMotion, canAnimate } from './core/reducedMotion';
export { freshEntries } from './core/freshEntries';
export { holdValues, mergeHeld, liveHeld, type Held } from './core/heldValues';
export { createDisplayQueue, type Displayed } from './core/displayQueue';

// dom: measuring places on screen
export { CARD_RATIO, visibleRect, cardAt, cardIn, type MotionRect, type MotionTarget } from './dom/rect';
export { defineAnchors, type AnchorResolvers, type DomQuery } from './dom/anchors';

// vue: the layer and the composables
export { default as CardMotionLayer } from './vue/CardMotionLayer.vue';
export { default as AnimationSpeedButton } from './vue/AnimationSpeedButton.vue';
export { ANIMATION_SPEED_KEY, useAnimationSpeed, type SpeedStorage } from './vue/useAnimationSpeed';
export {
  useTableMotion, type CardMotionLayerPlay, type Played, type TableMotion, type TableMotionOptions,
} from './vue/useTableMotion';
export { useDisplayedState } from './vue/useDisplayedState';
export { useInFlightCounts } from './vue/useInFlightCounts';
export { useSeatBubbles, type SeatBubble, type ShownBubble } from './vue/useSeatBubbles';
export { useTableSettled, type TableSettledSource } from './vue/useTableSettled';

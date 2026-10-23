/** How fast a table plays its animations: the pace of today's Fast is the old default, Normal is twice as slow. */
export type AnimationSpeed = 'normal' | 'fast' | 'instant';

export const ANIMATION_SPEEDS: readonly AnimationSpeed[] = ['normal', 'fast', 'instant'];

/** Normal, then Fast, then Instant, then Normal again. */
export function nextAnimationSpeed(speed: AnimationSpeed): AnimationSpeed {
  return ANIMATION_SPEEDS[(ANIMATION_SPEEDS.indexOf(speed) + 1) % ANIMATION_SPEEDS.length];
}

/** How long the animations last at a speed, as a share of Normal (0: nothing moves). */
export function speedFactor(speed: AnimationSpeed): number {
  return speed === 'normal' ? 1 : speed === 'fast' ? 0.5 : 0;
}

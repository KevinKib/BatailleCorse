import { ref, type Ref } from 'vue';
import { prefersReducedMotion } from './usePileAnimation';

/** How fast a table plays its animations: the pace of today's Fast is the old default, Normal is twice as slow. */
export type AnimationSpeed = 'normal' | 'fast' | 'instant';

export const ANIMATION_SPEEDS: readonly AnimationSpeed[] = ['normal', 'fast', 'instant'];

/** Where the player's choice is kept (per browser). */
export const ANIMATION_SPEED_KEY = 'animationSpeed';

/** The part of `Storage` the preference needs. */
export type SpeedStorage = Pick<Storage, 'getItem' | 'setItem'>;

export interface AnimationSpeedOptions {
  /** Defaults to `window.localStorage`; `null` for none. */
  storage?: SpeedStorage | null;
  /** Defaults to the `prefers-reduced-motion` media query. */
  reducedMotion?: () => boolean;
}

const isSpeed = (value: unknown): value is AnimationSpeed => ANIMATION_SPEEDS.includes(value as AnimationSpeed);

/** Normal, then Fast, then Instant, then Normal again. */
export function nextAnimationSpeed(speed: AnimationSpeed): AnimationSpeed {
  return ANIMATION_SPEEDS[(ANIMATION_SPEEDS.indexOf(speed) + 1) % ANIMATION_SPEEDS.length];
}

function defaultStorage(): SpeedStorage | null {
  try {
    return window.localStorage;
  } catch {
    return null;
  }
}

/**
 * The animation speed of a table, a preference of the player kept in the browser (the game works without storage).
 * Before the player ever chose, it is Normal, or Instant when the device asks for reduced motion; a choice, once
 * made, wins over the device. Written to be shared by every table that animates.
 */
export function useAnimationSpeed(options: AnimationSpeedOptions = {}): { speed: Ref<AnimationSpeed>; cycle: () => void } {
  const storage = options.storage === undefined ? defaultStorage() : options.storage;
  const reducedMotion = options.reducedMotion ?? prefersReducedMotion;

  function stored(): AnimationSpeed | null {
    try {
      const value = storage?.getItem(ANIMATION_SPEED_KEY);
      return isSpeed(value) ? value : null;
    } catch {
      return null;
    }
  }

  const speed = ref<AnimationSpeed>(stored() ?? (reducedMotion() ? 'instant' : 'normal'));

  function cycle(): void {
    speed.value = nextAnimationSpeed(speed.value);
    try {
      storage?.setItem(ANIMATION_SPEED_KEY, speed.value);
    } catch { /* storage unavailable: the choice holds for this visit */ }
  }

  return { speed, cycle };
}

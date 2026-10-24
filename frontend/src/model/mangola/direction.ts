import type { MangolaState } from './MangolaState';

/**
 * The way both loops run seen from above, chosen by the host in the lobby and sent by the server in every state.
 * Clockwise is the anticlockwise board mirrored left to right.
 */
export type MangolaDirection = 'anticlockwise' | 'clockwise';

export const MANGOLA_DIRECTIONS: readonly MangolaDirection[] = ['anticlockwise', 'clockwise'];

export const DEFAULT_MANGOLA_DIRECTION: MangolaDirection = 'anticlockwise';

/** The state's direction; an absent or unknown one reads as the default. */
export function directionOf(state: Pick<MangolaState, 'direction'>): MangolaDirection {
  const direction = state.direction;
  return direction !== undefined && MANGOLA_DIRECTIONS.includes(direction) ? direction : DEFAULT_MANGOLA_DIRECTION;
}

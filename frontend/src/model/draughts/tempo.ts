/** How long a move takes to show: one glide per leg of the path, then one removal per captured piece. */
export interface DraughtsTempo {
  /** The time a piece takes to glide from one landing square to the next. */
  legMs: number;
  /** The time between one captured piece disappearing and the next. */
  removalMs: number;
}

export type DraughtsSpeed = 'normal' | 'fast' | 'instant';

export const NORMAL_TEMPO: DraughtsTempo = { legMs: 260, removalMs: 140 };
export const FAST_TEMPO: DraughtsTempo = { legMs: 130, removalMs: 70 };
/** Reduced motion and the Instant speed: no glide, the change is immediate. */
export const INSTANT_TEMPO: DraughtsTempo = { legMs: 0, removalMs: 0 };

export function tempoFor(speed: DraughtsSpeed): DraughtsTempo {
  switch (speed) {
    case 'normal': return NORMAL_TEMPO;
    case 'fast': return FAST_TEMPO;
    case 'instant': return INSTANT_TEMPO;
  }
}

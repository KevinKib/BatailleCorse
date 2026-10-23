/**
 * The shape of a table's tempo numbers. A game keeps its own numbers (its `tempo.ts`); the module only names the
 * pieces they are made of. A move reads as: hold (the copy sits on its source) -> flight -> hold after landing (the
 * copy stays on its destination). Everything is in milliseconds. Purely visual: no rule of a game depends on it.
 */
export interface LegTempo {
  /** The card waits on its source before it leaves, so the eye can find it. */
  holdBeforeMs: number;
  flightMs: number;
  /** The card stays on its destination once landed, so its face can be read. */
  holdAfterMs: number;
}

/** How far a table's picture may fall behind its game. */
export interface QueueBounds {
  /** More events than this waiting to start: the extra ones only get their markers, at once. */
  maxQueued: number;
  /** An event that would start later than this after it arrived only gets its markers, at once. */
  maxLagMs: number;
}

/** The leg of a move: an opponent's (the hard ones to follow, hence usually the slowest) or mine. */
export function leg(byOpponent: boolean, tempo: { opponent: LegTempo; own: LegTempo }): LegTempo {
  return byOpponent ? tempo.opponent : tempo.own;
}

import type { MorrisColour, MorrisState } from './MorrisState';

/**
 * Pure selection logic of the Three Men's Morris board. The server is the only source of the rules: what may be
 * played comes from `availableActions`, `legalPlacements`, `legalMoves` and (after a mill) `legalRemovals`, and no
 * adjacency is known here. The
 * drawing of each variant is in `geometries.ts`.
 */

/** The colour of a seat: the server's `players[].colour`, else `whiteSeat`, else seat 0 plays White (the default). */
export function colourOf(state: Pick<MorrisState, 'players' | 'whiteSeat'>, seat: number): MorrisColour {
  const given = state.players.find(p => p.seat === seat)?.colour;
  if (given) return given;
  return (state.whiteSeat ?? 0) === seat ? 'WHITE' : 'BLACK';
}

/** Whether the viewer has something to do now: the server gave an action, and it is the viewer's seat. */
function myTurn(state: MorrisState, mySeat: number): boolean {
  return state.outcome.status !== 'FINISHED' && state.currentSeat === mySeat && state.availableActions.length > 0;
}

/** The points the viewer may click now: where a piece may be placed, or the pieces that have a legal move. */
export function selectable(state: MorrisState, mySeat: number): number[] {
  if (!myTurn(state, mySeat)) return [];
  if (state.availableActions.includes('REMOVE')) {
    // After a mill: only the pieces the server lists (those in a mill are left out while others can be taken).
    return state.legalRemovals ?? [];
  }
  if (state.availableActions.includes('PLACE')) {
    // The server lists the placements for the variants that restrict them; for Tapatan it leaves them out
    // and every free point is open.
    return state.legalPlacements ?? state.board.flatMap((owner, point) => (owner === null ? [point] : []));
  }
  if (state.availableActions.includes('MOVE')) {
    return [...new Set(state.legalMoves.map(m => m.from))].sort((a, b) => a - b);
  }
  return [];
}

/** The legal destinations of the selected piece. */
export function targets(state: MorrisState, selected: number | null): number[] {
  if (selected === null) return [];
  return state.legalMoves.filter(m => m.from === selected).map(m => m.to);
}

export type PointIntent =
  | { kind: 'place'; point: number }
  | { kind: 'select'; point: number }
  | { kind: 'move'; from: number; to: number }
  | { kind: 'remove'; point: number }
  | { kind: 'clear' }
  | { kind: 'none' };

/** What a click (or Enter) on a point means; nothing is applied locally, the server answers with the state. */
export function onPoint(state: MorrisState, mySeat: number, selected: number | null, point: number): PointIntent {
  const clickable = selectable(state, mySeat);
  if (clickable.length === 0) return { kind: 'none' };
  if (state.availableActions.includes('REMOVE')) {
    return clickable.includes(point) ? { kind: 'remove', point } : { kind: 'none' };
  }
  if (state.availableActions.includes('PLACE')) {
    return clickable.includes(point) ? { kind: 'place', point } : { kind: 'none' };
  }
  if (selected !== null && targets(state, selected).includes(point)) {
    return { kind: 'move', from: selected, to: point };
  }
  if (selected === point) return { kind: 'clear' };
  if (clickable.includes(point)) return { kind: 'select', point };
  return selected !== null ? { kind: 'clear' } : { kind: 'none' };
}

/** How long the winning line stays on the board before the end screen takes over (the end screen adds its own beat). */
export const WINNING_LINE_HOLD_MS = 600;

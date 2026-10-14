import type { KoboPhase } from './KoboState';

/** Phases the whole table waits in: said to everyone. */
const SHARED_PHASES: readonly KoboPhase[] = ['MEMORISING', 'ROUND_OVER', 'FINISHED'];

export interface LiveInput {
  phase: KoboPhase;
  /** Who has to act: the player on turn, or the announcer during the gift. */
  actor: number | null;
  mySeat: number;
  /** The instruction while a click on a card is expected. */
  promptText: string | null;
  phaseText: string;
  roundText: string;
}

/**
 * What the screen-reader region says. It speaks only of what the event log (a polite line of its own) does not:
 * what I am to do now, the start of a round and the end of one. An opponent's moves are the log's alone, so
 * nothing is said twice. Empty means nothing to say. Pure.
 */
export function liveAnnouncement(input: LiveInput): string {
  if (input.phase === 'MEMORISING') return `${input.roundText}. ${input.phaseText}`;
  if (SHARED_PHASES.includes(input.phase)) return input.phaseText;
  // The turn cue ("Your turn", its own status region) already says it is my turn to draw.
  if (input.actor !== input.mySeat || input.phase === 'DRAW') return '';
  return input.promptText ?? input.phaseText;
}

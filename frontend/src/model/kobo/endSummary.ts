import type { KoboState } from './KoboState';
import { winnersLowestTotal } from './roundScores';

export interface EndSummary {
  /** Seats that won, in the server's order; several when the lowest total is tied. */
  winners: number[];
  iWon: boolean;
  /** The others left (the server says FORFEIT); otherwise the game ended on the scores. */
  byForfeit: boolean;
  /** The winners' total, null when none of them is listed any more. */
  lowestTotal: number | null;
}

/** What the end screen says, from the finished game. Null while the game goes on. */
export function endSummary(game: KoboState, mySeat: number): EndSummary | null {
  const outcome = game.outcome;
  if (outcome.status !== 'FINISHED') return null;
  return {
    winners: outcome.winners,
    iWon: outcome.winners.includes(mySeat),
    byForfeit: outcome.reason === 'FORFEIT',
    lowestTotal: winnersLowestTotal(game.players, outcome.winners),
  };
}

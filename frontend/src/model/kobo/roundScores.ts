import type { KoboPlayer, KoboRound, KoboRoundPlayer } from './KoboState';

/** The scores of the round that just ended, as the table shows them. Every number comes from the server. */

export interface RoundRow {
  seat: number;
  handValue: number;
  roundPoints: number;
  totalBefore: number;
  totalAfter: number;
  /** The total was put back to 50 (see `resetFrom` for the total it fell from). */
  reset: boolean;
  /** The player who called Kobo. */
  announcer: boolean;
  /** The player who received the 10 points of a winning Kobo. */
  gift: boolean;
}

export type RoundVerdict = 'won' | 'lost' | 'none';

/** One row per player of the round, in seat order. */
export function roundRows(round: KoboRound): RoundRow[] {
  return [...round.players]
    .sort((a, b) => a.seat - b.seat)
    .map(p => ({
      seat: p.seat,
      handValue: p.handValue,
      roundPoints: p.roundPoints,
      totalBefore: p.totalBefore,
      totalAfter: p.totalAfter,
      reset: p.reset,
      announcer: round.announcer === p.seat,
      gift: round.giftTo === p.seat,
    }));
}

/** Points a winning announcer gives: the DTO flags the receiver but carries no amount, only the totals. */
const GIFT_POINTS_SHOWN = 10;

/**
 * The total a reset player had reached before it fell to 50 ("75 → 50"). The server sends the totals around the
 * round but not that intermediate one, so it is the sum of what it did send; display only, nothing is decided here.
 */
export function resetFrom(player: KoboRoundPlayer, receivedGift: boolean): number | null {
  if (!player.reset) return null;
  return player.totalBefore + player.roundPoints + (receivedGift ? GIFT_POINTS_SHOWN : 0);
}

/** How the announcer did: won (and gives 10), lost (and takes the penalty) or nobody called Kobo. */
export function roundVerdict(round: KoboRound): RoundVerdict {
  if (round.announcer === null) return 'none';
  return round.announcerWon ? 'won' : 'lost';
}

export interface StandingRow {
  seat: number;
  total: number;
  /** 1 for the lowest total; players on the same total share the rank and the next one is skipped. */
  rank: number;
  /** The total went past 100, which ends the game. */
  over: boolean;
}

/** Past this total the game is over; the pill "over 100" of the final table. */
const OVER_TOTAL = 100;

/** The final table: lowest total first, seat order between equal totals. */
export function finalStandings(players: KoboPlayer[]): StandingRow[] {
  const sorted = [...players].sort((a, b) => a.total - b.total || a.seat - b.seat);
  return sorted.map(p => {
    const firstWithTotal = sorted.findIndex(q => q.total === p.total);
    return { seat: p.seat, total: p.total, rank: firstWithTotal + 1, over: p.total > OVER_TOTAL };
  });
}

/** The lowest total among the winners still at the table, for "Lowest total: N points". */
export function winnersLowestTotal(players: KoboPlayer[], winners: number[]): number | null {
  const totals = players.filter(p => winners.includes(p.seat)).map(p => p.total);
  return totals.length > 0 ? Math.min(...totals) : null;
}

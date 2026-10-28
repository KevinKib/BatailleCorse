import type { BlackjackSeat } from './BlackjackState';

export type StandingStatus = 'live' | 'eliminated' | 'forfeited';

export interface StandingRow {
  player: BlackjackSeat;
  place: number;
  /** Another seat holds the same place. */
  shared: boolean;
  status: StandingStatus;
}

const statusOf = (p: BlackjackSeat): StandingStatus =>
  p.forfeited ? 'forfeited' : p.eliminated ? 'eliminated' : 'live';

const GROUP: Record<StandingStatus, number> = { live: 0, eliminated: 1, forfeited: 2 };

/**
 * The plates and the end screen in ranking order: live seats by stack (equal stacks share the place and the next
 * place is skipped), then the eliminated, then the forfeited, the later out higher. For a seat that is out the
 * place is the server's (it knows the hand of the elimination); a missing one follows the previous row.
 */
export function standingsOf(players: BlackjackSeat[]): StandingRow[] {
  const live = players.filter(p => statusOf(p) === 'live');
  const places = new Map<number, number>(
    live.map(p => [p.seat, 1 + live.filter(o => o.stack > p.stack).length]),
  );
  const ordered = [...players].sort((a, b) => {
    const sa = statusOf(a);
    const sb = statusOf(b);
    if (sa !== sb) return GROUP[sa] - GROUP[sb];
    if (sa === 'live') return b.stack - a.stack || a.seat - b.seat;
    return (a.place ?? Number.MAX_SAFE_INTEGER) - (b.place ?? Number.MAX_SAFE_INTEGER) || a.seat - b.seat;
  });
  const rows: StandingRow[] = [];
  ordered.forEach((player, i) => {
    const status = statusOf(player);
    const place = status === 'live' ? places.get(player.seat)! : player.place ?? (rows[i - 1]?.place ?? 0) + 1;
    rows.push({ player, place, shared: false, status });
  });
  return rows.map(r => ({ ...r, shared: rows.filter(o => o.place === r.place).length > 1 }));
}

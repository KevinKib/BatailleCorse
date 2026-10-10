import type { BullshitPlayer } from './BullshitState';

export interface SeatLabels {
  /** Fallback for a human without a name; '{n}' = 1-based seat number. */
  player: string;
  /** Computer players; '{n}' = 1-based position among the bots. */
  bot: string;
}

const fill = (template: string, n: number) => template.replace('{n}', String(n));

/**
 * The one place that decides how a seat is called: the name its player typed, "Bot N" for a computer
 * player, "Player N" when there is no name (or the seat is unknown). Names are user input: callers
 * must render the result as text, never as HTML.
 */
export function seatDisplayName(players: BullshitPlayer[], seat: number, labels: SeatLabels): string {
  const player = players.find(p => p.id === String(seat));
  if (player?.bot) {
    const botNumber = players.filter(p => p.bot && Number(p.id) <= seat).length;
    return fill(labels.bot, botNumber);
  }
  const name = player?.name?.trim();
  return name ? name : fill(labels.player, seat + 1);
}

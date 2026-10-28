import { format } from '../../locales/format';
import type { NinetyNineUiMessages } from '../../locales/Messages';
import { cardLabel } from '../president/cardLabel';
import type { NinetyNineLastPlay } from './NinetyNineState';

/** How close the running total is to 99, for its colour only (the rule that busts is the server's). */
export type TotalDanger = 'calm' | 'warm' | 'hot';

export function totalDanger(total: number): TotalDanger {
  if (total >= 90) return 'hot';
  if (total >= 80) return 'warm';
  return 'calm';
}

/** The line under the total, from the last play the state carries (so it reads the same after a reload). */
export function lastPlayLine(
  play: NinetyNineLastPlay | null, nameOf: (seat: number) => string, ui: NinetyNineUiMessages,
): string {
  if (!play) return '';
  const vars = { player: nameOf(play.seat), card: cardLabel(play.card, ui, false) };
  if (play.reversed) return format(ui.log.reversed, vars);
  if (play.aceValue !== null) return format(ui.log.playedAce, { ...vars, value: `+${play.aceValue}` });
  return format(ui.log.played, vars);
}

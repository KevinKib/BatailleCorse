import { format } from '../../locales/format';
import type { Messages } from '../../locales/Messages';
import type { LobbyPlayer, OptionDescriptor } from './LobbyView';
import { optionLabel } from './optionLabel';

/** A seat of a lobby or of a game view: the lobby says `bot` and `seatOptions`, the game view `bot` and `level`. */
export type BotSeatInfo = Pick<LobbyPlayer, 'seat' | 'bot' | 'seatOptions'> & { level?: string | null };

/** The computer seats of a lobby or of a game view. */
export function botSeatsOf(players: BotSeatInfo[]): number[] {
  return players.filter(p => p.bot === true).map(p => p.seat);
}

/**
 * The level of each computer seat (seat to level key): the `level` option of a lobby seat, the `level` of a game
 * view seat; seats without one are left out.
 */
export function botLevelsOf(players: BotSeatInfo[]): Record<number, string> {
  const levels: Record<number, string> = {};
  for (const p of players) {
    const level = p.bot === true ? (p.level ?? p.seatOptions?.level) : undefined;
    if (level != null) levels[p.seat] = level;
  }
  return levels;
}

/**
 * A computer seat's name followed by its level in the game's own words ("Bot 1 (Expert)"); the name alone when
 * the level is unknown (a game view from an older server, joined after a reload).
 * `labelKey` is the option group of the game (`options.mangola.level`); `template` has '{name}' and '{level}'.
 */
export function nameWithBotLevel(
  messages: Messages, labelKey: string, template: string, name: string, level: string | undefined,
): string {
  if (level === undefined) return name;
  const descriptor: OptionDescriptor = { key: 'level', type: 'CHOICE', values: [], defaultValue: level, labelKey };
  return format(template, { name, level: optionLabel(messages, descriptor, level, {}) });
}

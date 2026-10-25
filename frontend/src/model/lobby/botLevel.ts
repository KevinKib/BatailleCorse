import { format } from '../../locales/format';
import type { Messages } from '../../locales/Messages';
import type { LobbyPlayer, OptionDescriptor } from './LobbyView';
import { optionLabel } from './optionLabel';

/** The level of each computer seat of a lobby (seat to value of its `level` option); seats without one are left out. */
export function botLevelsOf(players: LobbyPlayer[]): Record<number, string> {
  const levels: Record<number, string> = {};
  for (const p of players) {
    const level = p.bot === true ? p.seatOptions?.level : undefined;
    if (level !== undefined) levels[p.seat] = level;
  }
  return levels;
}

/**
 * A computer seat's name followed by its level in the game's own words ("Bot 1 (Expert)"); the name alone when
 * the level is unknown (a reload mid-game: the game view does not carry it).
 * `labelKey` is the option group of the game (`options.mangola.level`); `template` has '{name}' and '{level}'.
 */
export function nameWithBotLevel(
  messages: Messages, labelKey: string, template: string, name: string, level: string | undefined,
): string {
  if (level === undefined) return name;
  const descriptor: OptionDescriptor = { key: 'level', type: 'CHOICE', values: [], defaultValue: level, labelKey };
  return format(template, { name, level: optionLabel(messages, descriptor, level, {}) });
}

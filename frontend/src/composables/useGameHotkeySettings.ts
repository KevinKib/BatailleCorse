import type { GameDefinition, GameKey } from '../model/games';
import { useHotkeySettings, type HotkeyPanelApi } from './useHotkeySettings';
import { useBatailleCorseHotkeySettings } from './useBatailleCorseHotkeySettings';

// Each game keeps its own storage (see `GameDefinition.hotkeys`); only the game asked for is read, so a
// screen that does not use the settings store never touches it.
const SETTINGS: { [K in GameKey]?: () => HotkeyPanelApi } = {
  bataillecorse: useBatailleCorseHotkeySettings,
  bullshit: useHotkeySettings,
};

/**
 * The local shortcut preferences of a game, as the settings panel needs them, or `null` for a game that
 * declares no shortcuts (no gear to show) or whose settings are not wired.
 */
export function useGameHotkeySettings(game: GameDefinition): HotkeyPanelApi | null {
  if (!game.hotkeys) return null;
  return SETTINGS[game.key]?.() ?? null;
}

import type { GameKey } from '../model/games';
import { useHotkeySettings, type HotkeyPanelApi } from './useHotkeySettings';
import { useBatailleCorseHotkeySettings } from './useBatailleCorseHotkeySettings';

// The local shortcut preferences of a game, as the settings panel needs them. Each game keeps its
// own storage (see `GameDefinition.hotkeys`); only the game asked for is read, so a screen that
// does not use the settings store never touches it.
export function useGameHotkeySettings(game: GameKey): HotkeyPanelApi {
  return game === 'bullshit' ? useHotkeySettings() : useBatailleCorseHotkeySettings();
}

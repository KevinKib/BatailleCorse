export type GameKey = 'bataillecorse' | 'bullshit';

/**
 * The local preferences (per device) a game declares: the remappable keyboard actions, the localStorage
 * entries they live in (one set per game) and whether the card digits are fixed. Action ids are the keys
 * of `hotkeys.actions` in the locale messages.
 */
export interface GameHotkeys {
  actions: readonly string[];
  storageKeys: readonly string[];
  /** Digits pick cards and cannot be changed: the settings panel says so. */
  fixedDigits: boolean;
}

/**
 * What the Game selection, the router and the Game home need to know about a game.
 * Exits from a game never use it: they always lead to the Game selection (`/games`).
 */
export interface GameDefinition {
  key: GameKey;
  /** The game's route prefix, also where the Game home lives. */
  gameHomeRoute: string;
  joinRoute: (id?: string) => string;
  roomRoute: (id: string) => string;
  /**
   * Where "New game" leads for a game that still has its own create page. A game without one creates its
   * lobby straight away (`POST /api/lobby`) and the player lands in it.
   */
  newGameRoute?: string;
  hotkeys: GameHotkeys;
}

function define(key: GameKey, hasCreatePage: boolean, hotkeys: GameHotkeys): GameDefinition {
  const base = `/games/${key}`;
  return {
    key,
    gameHomeRoute: base,
    joinRoute: (id) => (id ? `${base}/join/${id}` : `${base}/join`),
    roomRoute: (id) => `${base}/room/${id}`,
    ...(hasCreatePage ? { newGameRoute: `${base}/new` } : {}),
    hotkeys,
  };
}

export const GAMES: readonly GameDefinition[] = [
  // Bataille Corse keeps the keys it has always stored, one entry per action.
  define('bataillecorse', true, { actions: ['send', 'slap'], storageKeys: ['bc_sendKey', 'bc_slapKey'], fixedDigits: false }),
  define('bullshit', false, { actions: ['discard', 'call'], storageKeys: ['bullshit.hotkeys'], fixedDigits: true }),
];

export function findGame(key: unknown): GameDefinition | undefined {
  return GAMES.find(g => g.key === key);
}

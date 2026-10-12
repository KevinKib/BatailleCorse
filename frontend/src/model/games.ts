export type GameKey = 'bataillecorse' | 'bullshit';

/**
 * The local preferences (per device) a game declares: the remappable keyboard actions and the
 * localStorage entries they live in (one set per game). Action ids are the keys of `hotkeys.actions`
 * in the locale messages.
 */
export interface GameHotkeys {
  actions: readonly string[];
  storageKeys: readonly string[];
}

/**
 * What the Game selection, the router and the Game home need to know about a game. "New game" creates the
 * lobby straight away (`POST /api/lobby`) and the player lands in it.
 * Exits from a game never use it: they always lead to the Game selection (`/games`).
 */
export interface GameDefinition {
  key: GameKey;
  /** The game's route prefix, also where the Game home lives. */
  gameHomeRoute: string;
  joinRoute: (id?: string) => string;
  roomRoute: (id: string) => string;
  hotkeys: GameHotkeys;
}

function define(key: GameKey, hotkeys: GameHotkeys): GameDefinition {
  const base = `/games/${key}`;
  return {
    key,
    gameHomeRoute: base,
    joinRoute: (id) => (id ? `${base}/join/${id}` : `${base}/join`),
    roomRoute: (id) => `${base}/room/${id}`,
    hotkeys,
  };
}

export const GAMES: readonly GameDefinition[] = [
  // Bataille Corse keeps the keys it has always stored, one entry per action.
  define('bataillecorse', { actions: ['send', 'slap'], storageKeys: ['bc_sendKey', 'bc_slapKey'] }),
  define('bullshit', { actions: ['discard', 'call'], storageKeys: ['bullshit.hotkeys'] }),
];

export function findGame(key: unknown): GameDefinition | undefined {
  return GAMES.find(g => g.key === key);
}

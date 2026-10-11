export type GameKey = 'bataillecorse' | 'bullshit';

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
  /** Where "New game" leads until the lobby creates games straight away. */
  newGameRoute: string;
}

function define(key: GameKey): GameDefinition {
  const base = `/games/${key}`;
  return {
    key,
    gameHomeRoute: base,
    joinRoute: (id) => (id ? `${base}/join/${id}` : `${base}/join`),
    roomRoute: (id) => `${base}/room/${id}`,
    newGameRoute: `${base}/new`,
  };
}

export const GAMES: readonly GameDefinition[] = [define('bataillecorse'), define('bullshit')];

export function findGame(key: unknown): GameDefinition | undefined {
  return GAMES.find(g => g.key === key);
}

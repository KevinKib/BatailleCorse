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
  /**
   * Where "New game" leads for a game that still has its own create page. A game without one creates its
   * lobby straight away (`POST /api/lobby`) and the player lands in it.
   */
  newGameRoute?: string;
}

function define(key: GameKey, hasCreatePage: boolean): GameDefinition {
  const base = `/games/${key}`;
  return {
    key,
    gameHomeRoute: base,
    joinRoute: (id) => (id ? `${base}/join/${id}` : `${base}/join`),
    roomRoute: (id) => `${base}/room/${id}`,
    ...(hasCreatePage ? { newGameRoute: `${base}/new` } : {}),
  };
}

export const GAMES: readonly GameDefinition[] = [define('bataillecorse', true), define('bullshit', false)];

export function findGame(key: unknown): GameDefinition | undefined {
  return GAMES.find(g => g.key === key);
}

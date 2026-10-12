import type { GameDefinition, GameKey } from '../model/games';
import { useBullshitStore } from '../state/Bullshit.store';

/** Opens a lobby for the typed name and resolves with its game id. */
export type LobbyCreator = (name?: string) => Promise<string>;

/**
 * The games that create their lobby straight from the Game home (`POST /api/lobby`), each through its own store.
 * A game that still has its own create page (`newGameRoute`) has no entry; a game moving to the shared lobby adds one.
 */
export function useLobbyCreator(): (game: GameDefinition) => LobbyCreator | undefined {
  const bullshit = useBullshitStore();
  const creators: Partial<Record<GameKey, LobbyCreator>> = {
    bullshit: (name) => bullshit.create(name),
  };
  return (game) => creators[game.key];
}

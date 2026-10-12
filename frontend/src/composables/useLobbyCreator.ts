import type { GameDefinition, GameKey } from '../model/games';
import { useBullshitStore } from '../state/Bullshit.store';
import { useBatailleCorseStore } from '../state/BatailleCorse.store';

/** Opens a lobby for the typed name and resolves with its game id. */
export type LobbyCreator = (name?: string) => Promise<string>;

/** Every game creates its lobby straight from the Game home (`POST /api/lobby`), each through its own store. */
export function useLobbyCreator(): (game: GameDefinition) => LobbyCreator {
  const bullshit = useBullshitStore();
  const batailleCorse = useBatailleCorseStore();
  const creators: Record<GameKey, LobbyCreator> = {
    bataillecorse: (name) => batailleCorse.create(name),
    bullshit: (name) => bullshit.create(name),
  };
  return (game) => creators[game.key];
}

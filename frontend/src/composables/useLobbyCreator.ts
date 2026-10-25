import type { GameDefinition } from '../model/games';
import { useGameRooms } from './useGameRooms';

/** Opens a lobby for the typed name (unlisted when `privateGame`) and resolves with its game id. */
export type LobbyCreator = (name?: string, privateGame?: boolean) => Promise<string>;

/** Every game creates its lobby straight from the Game home (`POST /api/lobby`), each through its own store. */
export function useLobbyCreator(): (game: GameDefinition) => LobbyCreator {
  const rooms = useGameRooms();
  return (game) => rooms(game).create;
}

import type { GameDefinition, GameKey } from '../model/games';
import { useBullshitStore } from '../state/Bullshit.store';
import { useKoboStore } from '../state/Kobo.store';
import { useThreeMenMorrisStore } from '../state/ThreeMenMorris.store';
import { useBatailleCorseStore } from '../state/BatailleCorse.store';

/** What the Game home and the join page need from a game: open a lobby, claim a seat in one. */
export interface GameRoomClient {
  /** Opens a lobby for the typed name and resolves with its game id. */
  create: (name?: string) => Promise<string>;
  /** Claims a seat in the lobby `id`; a rejection keeps the player on the form. */
  join: (id: string, name?: string) => Promise<void>;
}

/**
 * The one place that maps a game to the store that talks to its server. Keys without a client yet (a game
 * whose frontend is not merged) are absent: asking for one fails loudly rather than joining another game.
 */
export function useGameRooms(): (game: GameDefinition) => GameRoomClient {
  const bullshit = useBullshitStore();
  const batailleCorse = useBatailleCorseStore();
  const kobo = useKoboStore();
  const morris = useThreeMenMorrisStore();
  const clients: { [K in GameKey]?: GameRoomClient } = {
    bataillecorse: { create: (name) => batailleCorse.create(name), join: (id, name) => batailleCorse.join(id, name) },
    bullshit: { create: (name) => bullshit.create(name), join: (id, name) => bullshit.join(id, name) },
    kobo: { create: (name) => kobo.create(name), join: (id, name) => kobo.join(id, name) },
    'three-men-morris': { create: (name) => morris.create(name), join: (id, name) => morris.join(id, name) },
  };
  return (game) => {
    const client = clients[game.key];
    if (!client) throw new Error(`No room client is wired for the game "${game.key}"`);
    return client;
  };
}

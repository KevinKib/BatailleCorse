import type { GameDefinition, GameKey } from '../model/games';
import { useBullshitStore } from '../state/Bullshit.store';
import { useKoboStore } from '../state/Kobo.store';
import { useThreeMenMorrisStore } from '../state/ThreeMenMorris.store';
import { usePresidentStore } from '../state/President.store';
import { useBatailleCorseStore } from '../state/BatailleCorse.store';
import { useMangolaStore } from '../state/Mangola.store';
import { useNinetyNineStore } from '../state/NinetyNine.store';
import { useDraughtsStore } from '../state/Draughts.store';
import { useBlackjackStore } from '../state/Blackjack.store';

/** What the Game home and the join page need from a game: open a lobby, claim a seat in one. */
export interface GameRoomClient {
  /** Opens a lobby for the typed name (unlisted when `privateGame`) and resolves with its game id. */
  create: (name?: string, privateGame?: boolean) => Promise<string>;
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
  const president = usePresidentStore();
  const mangola = useMangolaStore();
  const ninetyNine = useNinetyNineStore();
  const draughts = useDraughtsStore();
  const blackjack = useBlackjackStore();
  const clients: { [K in GameKey]?: GameRoomClient } = {
    bataillecorse: { create: (name, ...rest) => batailleCorse.create(name, ...rest), join: (id, name) => batailleCorse.join(id, name) },
    bullshit: { create: (name, ...rest) => bullshit.create(name, ...rest), join: (id, name) => bullshit.join(id, name) },
    kobo: { create: (name, ...rest) => kobo.create(name, ...rest), join: (id, name) => kobo.join(id, name) },
    'three-men-morris': { create: (name, ...rest) => morris.create(name, ...rest), join: (id, name) => morris.join(id, name) },
    president: { create: (name, ...rest) => president.create(name, ...rest), join: (id, name) => president.join(id, name) },
    mangola: { create: (name, ...rest) => mangola.create(name, ...rest), join: (id, name) => mangola.join(id, name) },
    'ninety-nine': { create: (name, ...rest) => ninetyNine.create(name, ...rest), join: (id, name) => ninetyNine.join(id, name) },
    draughts: { create: (name, ...rest) => draughts.create(name, ...rest), join: (id, name) => draughts.join(id, name) },
    blackjack: { create: (name, ...rest) => blackjack.create(name, ...rest), join: (id, name) => blackjack.join(id, name) },
  };
  return (game) => {
    const client = clients[game.key];
    if (!client) throw new Error(`No room client is wired for the game "${game.key}"`);
    return client;
  };
}

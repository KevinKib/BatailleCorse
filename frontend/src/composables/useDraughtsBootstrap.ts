import { findGame, type GameDefinition } from '../model/games';
import { DRAUGHTS_SESSION_CONFIG } from '../application/draughts/DraughtsSession';
import { useSeatGameBootstrap } from './useSeatGameBootstrap';
import { useDraughtsStore } from '../state/Draughts.store';

/**
 * On mount: re-attach to the game from localStorage, or lead to the join form / the Game home. The game
 * definition defaults to the registry's entry.
 */
export function useDraughtsBootstrap(gameId: string, game: GameDefinition | undefined = findGame('draughts')) {
  if (!game) throw new Error('Draughts is not registered in GAMES');
  useSeatGameBootstrap(game, gameId, DRAUGHTS_SESSION_CONFIG.tokenKeyPrefix, useDraughtsStore());
}

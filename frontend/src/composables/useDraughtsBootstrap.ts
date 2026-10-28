import { findGame, type GameDefinition } from '../model/games';
import { DRAUGHTS_SESSION_CONFIG } from '../application/draughts/DraughtsSession';
import { resolveSeatSession, useSeatGameBootstrap } from './useSeatGameBootstrap';
import { useDraughtsStore } from '../state/Draughts.store';

/** Reads the persisted seat+token for a game, or null if none stored. */
export function resolveDraughtsSession(gameId: string): { seat: number; token: string } | null {
  return resolveSeatSession(DRAUGHTS_SESSION_CONFIG.tokenKeyPrefix, gameId);
}

/**
 * On mount: re-attach to the game from localStorage, or lead to the join form / the Game home. The game
 * definition defaults to the registry's entry, which the entry PR adds to `GAMES`.
 */
export function useDraughtsBootstrap(gameId: string, game: GameDefinition | undefined = findGame('draughts')) {
  if (!game) throw new Error('Draughts is not registered in GAMES');
  useSeatGameBootstrap(game, gameId, DRAUGHTS_SESSION_CONFIG.tokenKeyPrefix, useDraughtsStore());
}

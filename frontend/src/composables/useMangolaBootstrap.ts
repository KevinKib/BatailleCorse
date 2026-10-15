import { findGame, type GameDefinition } from '../model/games';
import { MANGOLA_SESSION_CONFIG } from '../application/mangola/MangolaSession';
import { resolveSeatSession, useSeatGameBootstrap } from './useSeatGameBootstrap';
import { useMangolaStore } from '../state/Mangola.store';

/** Reads the persisted seat+token for a game, or null if none stored. */
export function resolveMangolaSession(gameId: string): { seat: number; token: string } | null {
  return resolveSeatSession(MANGOLA_SESSION_CONFIG.tokenKeyPrefix, gameId);
}

/**
 * On mount: re-attach to the game from localStorage, or lead to the join form / the Game home. The game
 * definition defaults to the registry's entry, which the entry PR adds to `GAMES`.
 */
export function useMangolaBootstrap(gameId: string, game: GameDefinition | undefined = findGame('mangola')) {
  if (!game) throw new Error('Mangola is not registered in GAMES');
  useSeatGameBootstrap(game, gameId, MANGOLA_SESSION_CONFIG.tokenKeyPrefix, useMangolaStore());
}

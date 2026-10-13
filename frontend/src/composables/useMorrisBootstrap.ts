import { findGame, type GameDefinition } from '../model/games';
import { MORRIS_SESSION_CONFIG } from '../application/threemenmorris/MorrisSession';
import { resolveSeatSession, useSeatGameBootstrap } from './useSeatGameBootstrap';
import { useThreeMenMorrisStore } from '../state/ThreeMenMorris.store';

/** Reads the persisted seat+token for a game, or null if none stored. */
export function resolveMorrisSession(gameId: string): { seat: number; token: string } | null {
  return resolveSeatSession(MORRIS_SESSION_CONFIG.tokenKeyPrefix, gameId);
}

/**
 * On mount: re-attach to the game from localStorage, or lead to the join form / the Game home. The game
 * definition defaults to the registry's entry, which the game's screen adds to `GAMES`.
 */
export function useMorrisBootstrap(gameId: string, game: GameDefinition | undefined = findGame('three-men-morris')) {
  if (!game) throw new Error('Three Men\'s Morris is not registered in GAMES');
  useSeatGameBootstrap(game, gameId, MORRIS_SESSION_CONFIG.tokenKeyPrefix, useThreeMenMorrisStore());
}

import { findGame, type GameDefinition } from '../model/games';
import { KOBO_SESSION_CONFIG } from '../application/KoboSession';
import { resolveSeatSession, useSeatGameBootstrap } from './useSeatGameBootstrap';
import { useKoboStore } from '../state/Kobo.store';

/** Reads the persisted seat+token for a Kobo game, or null if none stored. */
export function resolveKoboSession(gameId: string): { seat: number; token: string } | null {
  return resolveSeatSession(KOBO_SESSION_CONFIG.tokenKeyPrefix, gameId);
}

/**
 * On mount: re-attach to the Kobo game from localStorage, or lead to the join form / the Kobo home.
 * `game` is the registry entry of Kobo, which only exists once the game is registered on the Game selection.
 */
export function useKoboBootstrap(gameId: string, game: GameDefinition | undefined = findGame('kobo')) {
  if (!game) throw new Error('Kobo is not registered in the game registry');
  useSeatGameBootstrap(game, gameId, KOBO_SESSION_CONFIG.tokenKeyPrefix, useKoboStore());
}

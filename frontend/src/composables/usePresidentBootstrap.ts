import { findGame, type GameDefinition } from '../model/games';
import { PRESIDENT_SESSION_CONFIG } from '../application/PresidentSession';
import { resolveSeatSession, useSeatGameBootstrap } from './useSeatGameBootstrap';
import { usePresidentStore } from '../state/President.store';

/** Reads the persisted seat+token for a President game, or null if none stored. */
export function resolvePresidentSession(gameId: string): { seat: number; token: string } | null {
  return resolveSeatSession(PRESIDENT_SESSION_CONFIG.tokenKeyPrefix, gameId);
}

/**
 * On mount: re-attach to the President game from localStorage, or lead to the join form / the President home.
 * `game` is the registry entry of President, which only exists once the game is registered on the Game selection.
 */
export function usePresidentBootstrap(gameId: string, game: GameDefinition | undefined = findGame('president')) {
  if (!game) throw new Error('President is not registered in the game registry');
  useSeatGameBootstrap(game, gameId, PRESIDENT_SESSION_CONFIG.tokenKeyPrefix, usePresidentStore());
}

import { findGame, type GameDefinition } from '../model/games';
import { NINETY_NINE_SESSION_CONFIG } from '../application/NinetyNineSession';
import { resolveSeatSession, useSeatGameBootstrap } from './useSeatGameBootstrap';
import { useNinetyNineStore } from '../state/NinetyNine.store';

/** Reads the persisted seat+token for a 99 game, or null if none stored. */
export function resolveNinetyNineSession(gameId: string): { seat: number; token: string } | null {
  return resolveSeatSession(NINETY_NINE_SESSION_CONFIG.tokenKeyPrefix, gameId);
}

/**
 * On mount: re-attach to the 99 game from localStorage, or lead to the join form / the 99 home. `game` is the
 * registry entry of 99, which only exists once the game is registered on the Game selection.
 */
export function useNinetyNineBootstrap(gameId: string, game: GameDefinition | undefined = findGame('ninety-nine')) {
  if (!game) throw new Error('99 is not registered in the game registry');
  useSeatGameBootstrap(game, gameId, NINETY_NINE_SESSION_CONFIG.tokenKeyPrefix, useNinetyNineStore());
}

import { findGame, type GameDefinition } from '../model/games';
import { KILO_DE_MERDE_SESSION_CONFIG } from '../application/KiloDeMerdeSession';
import { resolveSeatSession, useSeatGameBootstrap } from './useSeatGameBootstrap';
import { useKiloDeMerdeStore } from '../state/KiloDeMerde.store';

/** Reads the persisted seat+token for a Kilo de merde game, or null if none stored. */
export function resolveKiloDeMerdeSession(gameId: string): { seat: number; token: string } | null {
  return resolveSeatSession(KILO_DE_MERDE_SESSION_CONFIG.tokenKeyPrefix, gameId);
}

/**
 * On mount: re-attach to the Kilo de merde game from localStorage, or lead to the join form / the game home.
 * `game` is the registry entry of Kilo de merde, which only exists once the game is registered on the Game
 * selection.
 */
export function useKiloDeMerdeBootstrap(
  gameId: string, game: GameDefinition | undefined = findGame('kilo-de-merde'),
) {
  if (!game) throw new Error('Kilo de merde is not registered in the game registry');
  useSeatGameBootstrap(game, gameId, KILO_DE_MERDE_SESSION_CONFIG.tokenKeyPrefix, useKiloDeMerdeStore());
}

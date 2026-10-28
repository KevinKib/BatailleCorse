import { findGame, type GameDefinition } from '../model/games';
import { BLACKJACK_SESSION_CONFIG } from '../application/BlackjackSession';
import { resolveSeatSession, useSeatGameBootstrap } from './useSeatGameBootstrap';
import { useBlackjackStore } from '../state/Blackjack.store';

/** Reads the persisted seat+token for a Blackjack game, or null if none stored. */
export function resolveBlackjackSession(gameId: string): { seat: number; token: string } | null {
  return resolveSeatSession(BLACKJACK_SESSION_CONFIG.tokenKeyPrefix, gameId);
}

/**
 * On mount: re-attach to the Blackjack game from localStorage, or lead to the join form / the game home.
 * `game` is the registry entry of Blackjack, which only exists once the game is registered on the Game selection.
 */
export function useBlackjackBootstrap(gameId: string, game: GameDefinition | undefined = findGame('blackjack')) {
  if (!game) throw new Error('Blackjack is not registered in the game registry');
  useSeatGameBootstrap(game, gameId, BLACKJACK_SESSION_CONFIG.tokenKeyPrefix, useBlackjackStore());
}

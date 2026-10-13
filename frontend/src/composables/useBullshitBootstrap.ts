import { findGame } from '../model/games';
import { BULLSHIT_SESSION_CONFIG } from '../application/BullshitSession';
import { resolveSeatSession, useSeatGameBootstrap } from './useSeatGameBootstrap';
import { useBullshitStore } from '../state/Bullshit.store';

/** Reads the persisted seat+token for a game, or null if none stored. */
export function resolveBullshitSession(gameId: string): { seat: number; token: string } | null {
  return resolveSeatSession(BULLSHIT_SESSION_CONFIG.tokenKeyPrefix, gameId);
}

/** On mount: re-attach to the game from localStorage, or lead to the join form / the Game home. */
export function useBullshitBootstrap(gameId: string) {
  useSeatGameBootstrap(findGame('bullshit')!, gameId, BULLSHIT_SESSION_CONFIG.tokenKeyPrefix, useBullshitStore());
}

import { ref } from 'vue';
import type { RematchState } from '../model/RematchButton';
import type { ForfeitReason } from '../model/ForfeitReason';
import type { ForfeitEventData } from '../model/SeatLifecycleEvents';

export type { RematchState };

/** The session commands the room state drives (a `SeatGameSession` fits). */
export interface SeatRoomSession {
  startGame(): void;
  leave(): void;
  playAgain(name?: string): Promise<void>;
}

/**
 * The part of a seat game's store every game shares, used inside its setup store: the lobby error, the Start
 * and Play again states, being removed by the host, the last forfeit, and the lobby commands that can fail
 * because the socket is down. The game's own store still decides what its views and events mean.
 */
export function useSeatRoom(session: SeatRoomSession) {
  // Last refusal of a lobby command (server message, or 'connection' when it could not be sent), cleared by the
  // next lobby refresh. Whatever the event type: a Start refusal is typed START, a Leave is typed JOIN.
  const lobbyError = ref<string | null>(null);
  // A Start is on its way: from the click until the game state or a refusal comes back.
  const starting = ref(false);
  // Rematch request of this client: pending while the HTTP call runs, failed when refused.
  const rematch = ref<RematchState>('idle');
  // The host removed me from the lobby: the screen leads to the Game selection with a notice.
  const removedByHost = ref(false);
  // Last seat that left (resigned or timed out), kept for the end screen after the notice is gone.
  const lastForfeit = ref<{ seat: number; reason: ForfeitReason | null } | null>(null);

  // Publishing throws while the socket is down (reconnecting): report it like a server refusal.
  // Returns whether the command was sent.
  function requestLobbyChange(send: () => void): boolean {
    lobbyError.value = null;
    try {
      send();
      return true;
    } catch {
      lobbyError.value = 'connection';
      return false;
    }
  }

  // One Start at a time: the button is busy until the answer, so a second click is not sent.
  function startGame(): boolean {
    if (starting.value) return false;
    starting.value = requestLobbyChange(() => session.startGame());
    return starting.value;
  }

  // Leaves the lobby, then lets the game's store forget everything about it (`clear`), so the next room starts
  // clean. Returns whether the lobby was left: false when the command could not be sent (still seated).
  function leave(clear: () => void): boolean {
    if (!requestLobbyChange(() => session.leave())) return false;
    clear();
    lobbyError.value = null;
    return true;
  }

  // `prepare` resets what belongs to the finished game (presence, selection...) before the request.
  async function playAgain(prepare?: () => void, name?: string): Promise<void> {
    prepare?.();
    rematch.value = 'pending';
    try {
      await session.playAgain(name);
    } catch {
      rematch.value = 'failed';
    }
  }

  /** A view came in: any wait for the server is over; a lobby ends the finished game's leftovers. */
  function onStateUpdate(started: boolean): void {
    lobbyError.value = null;
    starting.value = false;
    if (!started) {
      lastForfeit.value = null;
      rematch.value = 'idle';
    }
  }

  /** A refused command: only the lobby shows it, a refused move at the table is covered by the state it carries. */
  function onError(gameStarted: boolean, message: string): void {
    lobbyError.value = gameStarted ? null : message;
    starting.value = false;
  }

  function onRemoved(): void {
    lobbyError.value = null;
    starting.value = false;
    removedByHost.value = true;
  }

  function onForfeit(data: ForfeitEventData): void {
    lastForfeit.value = { seat: data.loserSeat, reason: data.reason ?? null };
  }

  return {
    lobbyError, starting, rematch, removedByHost, lastForfeit,
    requestLobbyChange, startGame, leave, playAgain,
    onStateUpdate, onError, onRemoved, onForfeit,
  };
}

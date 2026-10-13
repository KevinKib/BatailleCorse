import { describe, it, expect, vi } from 'vitest';
import { useSeatRoom } from './useSeatRoom';

function make(overrides: Partial<Parameters<typeof useSeatRoom>[0]> = {}) {
  const session = {
    startGame: vi.fn(),
    leave: vi.fn(),
    playAgain: vi.fn(async () => {}),
    ...overrides,
  };
  return { session, room: useSeatRoom(session) };
}

describe('useSeatRoom', () => {
  it('requestLobbyChange reports a socket that is down as a connection error and clears it on the next call', () => {
    const { room } = make();
    expect(room.requestLobbyChange(() => { throw new Error('down'); })).toBe(false);
    expect(room.lobbyError.value).toBe('connection');
    expect(room.requestLobbyChange(() => {})).toBe(true);
    expect(room.lobbyError.value).toBeNull();
  });

  it('startGame is busy until the answer: a second click is not sent', () => {
    const { session, room } = make();
    expect(room.startGame()).toBe(true);
    expect(room.starting.value).toBe(true);
    expect(room.startGame()).toBe(false);
    expect(session.startGame).toHaveBeenCalledTimes(1);
  });

  it('startGame is not busy when the command could not be sent', () => {
    const { room } = make({ startGame: () => { throw new Error('down'); } });
    expect(room.startGame()).toBe(false);
    expect(room.starting.value).toBe(false);
    expect(room.lobbyError.value).toBe('connection');
  });

  it('leave clears the store part only once the Leave command went out', () => {
    const clear = vi.fn();
    const failing = make({ leave: () => { throw new Error('down'); } });
    expect(failing.room.leave(clear)).toBe(false);
    expect(clear).not.toHaveBeenCalled();
    const { room } = make();
    room.lobbyError.value = 'x';
    expect(room.leave(clear)).toBe(true);
    expect(clear).toHaveBeenCalledTimes(1);
    expect(room.lobbyError.value).toBeNull();
  });

  it('playAgain goes pending, prepares first, and fails when the request is refused', async () => {
    const prepare = vi.fn();
    const { session, room } = make();
    await room.playAgain(prepare);
    expect(prepare).toHaveBeenCalled();
    expect(session.playAgain).toHaveBeenCalled();
    expect(room.rematch.value).toBe('pending');
    const refused = make({ playAgain: async () => { throw new Error('no'); } });
    await refused.room.playAgain();
    expect(refused.room.rematch.value).toBe('failed');
  });

  it('a started game state ends the Start wait and keeps the rematch state; a lobby resets it', () => {
    const { room } = make();
    room.starting.value = true;
    room.rematch.value = 'pending';
    room.lobbyError.value = 'old';
    room.onStateUpdate(true);
    expect(room.starting.value).toBe(false);
    expect(room.lobbyError.value).toBeNull();
    expect(room.rematch.value).toBe('pending');
    room.lastForfeit.value = { seat: 1, reason: null };
    room.onStateUpdate(false);
    expect(room.rematch.value).toBe('idle');
    expect(room.lastForfeit.value).toBeNull();
  });

  it('a refusal shows only in the lobby; a refused move in a running game says nothing', () => {
    const { room } = make();
    room.starting.value = true;
    room.onError(true, 'nope');
    expect(room.lobbyError.value).toBeNull();
    expect(room.starting.value).toBe(false);
    room.onError(false, 'nope');
    expect(room.lobbyError.value).toBe('nope');
  });

  it('removed by the host raises the flag and drops the pending state', () => {
    const { room } = make();
    room.starting.value = true;
    room.lobbyError.value = 'x';
    room.onRemoved();
    expect(room.removedByHost.value).toBe(true);
    expect(room.starting.value).toBe(false);
    expect(room.lobbyError.value).toBeNull();
  });

  it('a forfeit event is kept for the end screen', () => {
    const { room } = make();
    room.onForfeit({ loserSeat: 2, reason: 'DISCONNECTED' });
    expect(room.lastForfeit.value).toEqual({ seat: 2, reason: 'DISCONNECTED' });
  });
});

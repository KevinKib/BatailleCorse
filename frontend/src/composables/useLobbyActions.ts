export interface LobbyActionsContext {
  publish(destination: string, body: string): void;
  /** The seat this client holds, or null before it has one. */
  identity(): { gameId: string; token: string } | null;
}

/**
 * The lobby commands every game shares (`/app/lobby/*`). Each one only sends: the answer is the next lobby view,
 * there is no optimistic update. Publishing throws while the socket is down, which the caller reports.
 */
export function useLobbyActions(ctx: LobbyActionsContext) {
  function send(destination: string, extra: Record<string, unknown> = {}) {
    const identity = ctx.identity();
    if (!identity) return;
    ctx.publish(`/app/lobby/${destination}`, JSON.stringify({ ...identity, ...extra }));
  }
  return {
    setOption: (key: string, value: string) => send('setOptions', { values: { [key]: value } }),
    setSeatOption: (seat: number, key: string, value: string) => send('setSeatOption', { seat, key, value }),
    setPrivate: (privateGame: boolean) => send('setPrivate', { privateGame }),
    rename: (name: string) => send('rename', { name }),
    addBot: () => send('addBot'),
    removeBot: (seat: number) => send('removeBot', { seat }),
    removeSeat: (seat: number) => send('removeSeat', { seat }),
    leave: () => send('leave'),
  };
}

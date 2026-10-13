/**
 * Opponents in the order they play after me. The server lists players in turn order
 * (ascending seat, wrapping), so rotating that list to start just after my seat gives
 * the order the ring must follow (left to right = clockwise from me).
 */
export function opponentsInPlayOrder<T extends { id: string }>(players: T[], mySeat: string | number | null): T[] {
  const me = players.findIndex(p => p.id === String(mySeat));
  if (me < 0) return [...players];
  return [...players.slice(me + 1), ...players.slice(0, me)];
}

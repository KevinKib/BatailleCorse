/**
 * What the table shows: a state of the game, and the latest move whose picture has been played (it carries what the
 * state alone does not say, such as why the last trick closed). `entry` is kept when a state without a move follows.
 */
export interface Displayed<S, E> {
  state: S;
  entry: E | null;
}

/**
 * The states of the game, in the order the server sent them, held back until their picture is over. The table's
 * texts read the state shown here, never the latest one: a play is told (whose turn, "9 or nothing", the counts) once
 * its cards have landed. A state is reserved when it arrives and becomes ready when its picture is over; it is shown
 * only once every state before it is shown too, so a later state never overtakes an earlier one. Pure: the timers are
 * the caller's (`useDisplayedState`).
 */
export function createDisplayQueue<S, E>(initial: Displayed<S, E> | null = null) {
  let current: Displayed<S, E> | null = initial;
  let slots: { id: number; state: S; entry: E | null; ready: boolean }[] = [];
  let nextId = 0;

  /** Shows the ready states at the front; returns them in order, each with its own move (null for a state without one). */
  function advance(): Displayed<S, E>[] {
    const shown: Displayed<S, E>[] = [];
    while (slots.length > 0 && slots[0].ready) {
      const slot = slots.shift()!;
      shown.push({ state: slot.state, entry: slot.entry });
      current = { state: slot.state, entry: slot.entry ?? current?.entry ?? null };
    }
    return shown;
  }

  return {
    current: (): Displayed<S, E> | null => current,
    /** How many states wait to be shown. */
    waiting: (): number => slots.length,
    /** A state arrived with the move it played: it waits until `ready`. Returns its slot. */
    reserve(state: S, entry: E | null = null): number {
      const id = ++nextId;
      slots.push({ id, state, entry, ready: false });
      return id;
    },
    /** The picture of a slot is over; returns the states this shows (none while an earlier one still waits). */
    ready(id: number): Displayed<S, E>[] {
      const slot = slots.find(s => s.id === id);
      if (!slot) return [];
      slot.ready = true;
      return advance();
    },
    /** A state without a picture of its own: shown as soon as the states before it are. */
    show(state: S, entry: E | null = null): Displayed<S, E>[] {
      const id = ++nextId;
      slots.push({ id, state, entry, ready: true });
      return advance();
    },
    /** Shows every state that waits, now. */
    flush(): Displayed<S, E>[] {
      slots.forEach(s => { s.ready = true; });
      return advance();
    },
    /** Starts over from `state` (a first state, a lobby, a new game): nothing waits any more. */
    reset(state: S | null): void {
      slots = [];
      current = state === null ? null : { state, entry: null };
    },
  };
}

import { ref } from 'vue';

/**
 * The cards still flying from a seat to the pile, so that the seat's count drops when they land rather than when the
 * state arrives (the state already has the new count). Purely visual: the state's count is the truth, this only adds
 * the cards in flight to it for a moment, at most `maxLagMs`: behind a long queue of flights the count catches up with
 * the state rather than lag behind it.
 */
export function useInFlightCounts(options: { maxLagMs?: number } = {}): {
  add: (seat: number, cards: number, landsInMs: number) => void;
  shown: (seat: number, handSize: number) => number;
  cancel: () => void;
} {
  const flying = ref<Record<number, number>>({});
  const timers = new Set<ReturnType<typeof setTimeout>>();

  const change = (seat: number, by: number) => {
    const left = (flying.value[seat] ?? 0) + by;
    const { [seat]: _old, ...rest } = flying.value;
    flying.value = left > 0 ? { ...rest, [seat]: left } : rest;
  };

  function add(seat: number, cards: number, landsInMs: number) {
    if (cards <= 0) return;
    change(seat, cards);
    const timer = setTimeout(() => { timers.delete(timer); change(seat, -cards); }, Math.max(0, Math.min(landsInMs, options.maxLagMs ?? Infinity)));
    timers.add(timer);
  }

  function cancel() {
    for (const timer of timers) clearTimeout(timer);
    timers.clear();
    flying.value = {};
  }

  return { add, shown: (seat, handSize) => handSize + (flying.value[seat] ?? 0), cancel };
}

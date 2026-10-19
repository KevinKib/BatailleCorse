import type { PresidentState } from './PresidentState';

/** How long the summary of the exchange is on screen before the client says it has seen it (Normal speed). */
export const SUMMARY_READ_MS = 3000;

/**
 * The human seats, other than mine, that have not yet seen this round's exchange, in seat order. Empty outside the
 * exchange view (the server clears `sawExchange` there). Bots are never waited for: they acknowledge on their own. Pure.
 */
export function waitingFor(state: PresidentState, mySeat: number): number[] {
  if (state.phase !== 'EXCHANGE_VIEW') return [];
  return state.players
    .filter(p => !p.bot && p.seat !== mySeat && !p.sawExchange)
    .map(p => p.seat)
    .sort((a, b) => a - b);
}

/** What the phase line says during the exchange view; null outside it. */
export type ExchangeViewStatus =
  | { kind: 'watching' }
  | { kind: 'waiting'; seats: number[] }
  | { kind: 'starting' };

/**
 * Where I stand in the exchange view: still watching it (I have not acknowledged), waiting for the humans who have
 * not, or nobody left (the next state starts the first trick). Pure.
 */
export function exchangeViewStatus(state: PresidentState, mySeat: number): ExchangeViewStatus | null {
  if (state.phase !== 'EXCHANGE_VIEW') return null;
  const me = state.players.find(p => p.seat === mySeat);
  if (!me?.sawExchange) return { kind: 'watching' };
  const seats = waitingFor(state, mySeat);
  return seats.length > 0 ? { kind: 'waiting', seats } : { kind: 'starting' };
}

/**
 * When to acknowledge the exchange, counted from the state that opened the view: once the flights have landed
 * (`flightsEndMs`, 0 after a reload) and the summary has been on screen for 3 s scaled by the speed (`factor` from
 * `speedFactor`: 1, 0.5; 0 is Instant, which does not wait at all). Pure.
 */
export function ackDelayMs({ flightsEndMs, factor }: { flightsEndMs: number; factor: number }): number {
  return factor <= 0 ? 0 : flightsEndMs + SUMMARY_READ_MS * factor;
}

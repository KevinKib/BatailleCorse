import type { BlackjackBetLimits } from './BlackjackState';

/** The chip increments of the bet panel. */
export const BET_CHIPS = [1, 5, 10, 25] as const;

/** The bet offered on the first hand, before any "last bet" exists. */
export const DEFAULT_BET = 10;

/** Keeps a bet inside the limits the server sent (whole numbers only). */
export function clampBet(amount: number, limits: BlackjackBetLimits): number {
  const whole = Number.isFinite(amount) ? Math.floor(amount) : limits.min;
  return Math.min(limits.max, Math.max(limits.min, whole));
}

/** The largest bet allowed (`min(maxBet, stack)`, as the server computed it); 0 outside the betting phase. */
export function maxOf(limits: BlackjackBetLimits | null): number {
  return limits ? limits.max : 0;
}

/** Adds a chip to the bet being composed, never above the maximum. */
export function addChip(current: number, chip: number, limits: BlackjackBetLimits): number {
  return clampBet(current + chip, limits);
}

/** The amount of the one-tap "Bet N": the last bet clamped, else 10 clamped; 0 without limits. */
export function defaultBet(lastBet: number | null, limits: BlackjackBetLimits | null): number {
  if (!limits) return 0;
  return clampBet(lastBet ?? DEFAULT_BET, limits);
}

/** Whether the amount is a whole number inside the limits (the client only refuses what the server would). */
export function isBetAllowed(amount: number, limits: BlackjackBetLimits | null): boolean {
  return limits !== null && Number.isInteger(amount) && amount >= limits.min && amount <= limits.max;
}

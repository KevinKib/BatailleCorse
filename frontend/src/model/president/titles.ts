import type { PresidentPlayer, PresidentTitle } from './PresidentState';

/**
 * The titles, from the top of the table to the bottom. Which seat holds which title is the server's
 * (`PresidentPlayer.title`): this only orders and names them.
 */
export const PRESIDENT_TITLES: readonly PresidentTitle[] =
  ['PRESIDENT', 'VICE_PRESIDENT', 'NEUTRAL', 'VICE_SCUM', 'SCUM'];

const KEYS: Record<PresidentTitle, string> = {
  PRESIDENT: 'president',
  VICE_PRESIDENT: 'vicePresident',
  NEUTRAL: 'neutral',
  VICE_SCUM: 'viceScum',
  SCUM: 'scum',
};

/** The key of the title's text, at `presidentUi.titles.<key>` (President, Vice-President, Neutral, Vice-Scum, Scum). */
export function titleKey(title: PresidentTitle): string {
  return KEYS[title];
}

/** 0 for the President ... 4 for the Scum; no title sorts after all of them. */
export function titleOrder(title: PresidentTitle | null): number {
  return title === null ? PRESIDENT_TITLES.length : PRESIDENT_TITLES.indexOf(title);
}

/** The players in finishing order, those still holding cards after them, by seat. */
export function seatsByPlace(players: readonly PresidentPlayer[]): PresidentPlayer[] {
  return [...players].sort((a, b) =>
    (a.place ?? Number.MAX_SAFE_INTEGER) - (b.place ?? Number.MAX_SAFE_INTEGER) || a.seat - b.seat);
}

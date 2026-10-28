import type Card from '../Card';
import type { NinetyNinePlayable, NinetyNineState } from './NinetyNineState';

/**
 * The client holds no rule of 99: which card may be played and which Ace values are allowed come from the server's
 * `playable` list, looked up here by card name.
 */

/** The server's entry for a card of my hand; null for any other card. */
export function playableFor(state: NinetyNineState, card: Card): NinetyNinePlayable | null {
  return state.playable.find(p => p.card.name === card.name) ?? null;
}

/** The server says this card may be played (false for a card that is not in my hand). */
export function isSafe(state: NinetyNineState, card: Card): boolean {
  return playableFor(state, card)?.safe === true;
}

/** The Ace values the server allows for this card; empty for any other card. */
export function aceValuesFor(state: NinetyNineState, card: Card): number[] {
  return playableFor(state, card)?.aceValues ?? [];
}

/** The player must pick a value: more than one is allowed. */
export function needsAceChoice(state: NinetyNineState, card: Card): boolean {
  return aceValuesFor(state, card).length > 1;
}

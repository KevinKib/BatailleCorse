import { format } from '../../locales/format';
import type { BullshitUiMessages } from '../../locales/Messages';
import type { ClaimMode } from './claimMode';
import type { DeckSize } from './deckSize';

// The rank mode cycles through the ranks of the deck in play, so its label carries the
// range: A→K for 52 cards, 7→A for 32.
export function claimModeLabel(start: BullshitUiMessages['start'], mode: ClaimMode, deck: DeckSize): string {
  return format(start.claimModes[mode], { range: start.rankRanges[deck] });
}

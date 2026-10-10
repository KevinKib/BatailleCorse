export const DECK_SIZE_SHORT = '32';
export const DECK_SIZE_FULL = '52';

export type DeckSize = typeof DECK_SIZE_SHORT | typeof DECK_SIZE_FULL;

// Preselected on the create screen (the host can change it). The size is then fixed for the
// room: the lobby shows it read-only and a rematch keeps it. The rule for a payload without
// a size lives in the backend (`DeckSize.DEFAULT`), so the payload simply omits it.
export const DEFAULT_DECK_SIZE: DeckSize = DECK_SIZE_SHORT;

// Selectable sizes, in display order. Their labels live in the locale messages
// (`bullshitUi.start.deckSizes`), keyed by size.
export const DECK_SIZES: DeckSize[] = [DECK_SIZE_SHORT, DECK_SIZE_FULL];

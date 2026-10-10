export const DECK_SIZE_SHORT = '32';
export const DECK_SIZE_FULL = '52';

export type DeckSize = typeof DECK_SIZE_SHORT | typeof DECK_SIZE_FULL;

// Preselected on the create screen (the host can change it).
export const DEFAULT_DECK_SIZE: DeckSize = DECK_SIZE_SHORT;

// What the payload carries when the caller does not say: the backend's own default.
export const PAYLOAD_DEFAULT_DECK_SIZE: DeckSize = DECK_SIZE_FULL;

// Selectable sizes, in display order. Their labels live in the locale messages
// (`bullshitUi.start.deckSizes`), keyed by size.
export const DECK_SIZES: DeckSize[] = [DECK_SIZE_SHORT, DECK_SIZE_FULL];

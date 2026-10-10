export const DECK_SIZE_SHORT = '32';
export const DECK_SIZE_FULL = '52';
export const DECK_CHOICE_AUTO = 'auto';

export type DeckSize = typeof DECK_SIZE_SHORT | typeof DECK_SIZE_FULL;
// What the host picks on the create screen. Automatic means "no choice": the backend
// resolves the deck from the number of seated players (32 cards for 2-3, 52 from 4) when
// the game starts, and keeps the lobby informed. An explicit size always wins.
export type DeckChoice = DeckSize | typeof DECK_CHOICE_AUTO;

// Preselected on the create screen (the host can change it).
export const DEFAULT_DECK_CHOICE: DeckChoice = DECK_CHOICE_AUTO;

// Selectable choices, in display order. Their labels live in the locale messages
// (`bullshitUi.start.deckSizes`), keyed by choice.
export const DECK_CHOICES: DeckChoice[] = [DECK_CHOICE_AUTO, DECK_SIZE_SHORT, DECK_SIZE_FULL];

// Sizes the lobby can announce.
export const DECK_SIZES: DeckSize[] = [DECK_SIZE_SHORT, DECK_SIZE_FULL];

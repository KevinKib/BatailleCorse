export const CLAIM_MODE_RANK = 'rank';
export const CLAIM_MODE_SUIT = 'suit';

export type ClaimMode = typeof CLAIM_MODE_RANK | typeof CLAIM_MODE_SUIT;

export const DEFAULT_CLAIM_MODE: ClaimMode = CLAIM_MODE_RANK;

// Selectable modes, in display order. Their labels live in the locale messages
// (`bullshitUi.start.claimModes`), keyed by mode.
export const CLAIM_MODES: ClaimMode[] = [CLAIM_MODE_RANK, CLAIM_MODE_SUIT];

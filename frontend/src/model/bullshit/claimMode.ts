export const CLAIM_MODE_RANK = 'rank';
export const CLAIM_MODE_SUIT = 'suit';

export type ClaimMode = typeof CLAIM_MODE_RANK | typeof CLAIM_MODE_SUIT;

export const DEFAULT_CLAIM_MODE: ClaimMode = CLAIM_MODE_RANK;

export interface ClaimModeOption {
  key: ClaimMode;
  label: string;
}

export const CLAIM_MODE_OPTIONS: ClaimModeOption[] = [
  { key: CLAIM_MODE_RANK, label: 'By rank (A→K)' },
  { key: CLAIM_MODE_SUIT, label: 'By suit (♥→♠)' },
];

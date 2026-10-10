export interface ClaimSuit {
  glyph: string;
  red: boolean;
}

const SUITS: Record<string, ClaimSuit> = {
  SPADE: { glyph: '♠', red: false },
  HEART: { glyph: '♥', red: true },
  DIAMOND: { glyph: '♦', red: true },
  CLUB: { glyph: '♣', red: false },
};

/** The suit symbol of a claim label ("SPADE"), or null when the claim is a rank. */
export function claimSuit(label: string | undefined): ClaimSuit | null {
  return (label && SUITS[label.toUpperCase()]) || null;
}

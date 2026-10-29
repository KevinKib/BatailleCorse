/**
 * The tempo of Blackjack's table, in one place, in milliseconds at the Normal speed. Purely visual: no rule of the game
 * depends on it. The table tells a turn in this order, one beat after the other: bets, deal, each player's play, the
 * dealer's play, the results.
 */
export const BLACKJACK_TEMPO = {
  /** The bets of the table are shown together (they were sealed), then the old cards are cleared. */
  bets: { revealMs: 1300, clearMs: 350 },
  /** The deal: one card per leg, leaving this far apart (10 cards at 6 seats are about 3 s). */
  deal: { flightMs: 420, staggerMs: 260, endMs: 350 },
  /** A hit or the card of a double: the tag first, then the card flies. */
  draw: { tagLeadMs: 380, flightMs: 480, endMs: 500 },
  /** A split: the tag, the second card moves to its new hand, then each hand is dealt a card. */
  split: { tagLeadMs: 450, flightMs: 420, endMs: 450 },
  /** A stand or a bust: the tag is read before the next player plays. */
  say: { spanMs: 1000 },
  /** The dealer: the banner, the hole card turning over, then each draw one after the other. */
  dealer: { bannerMs: 1000, revealMs: 560, afterRevealMs: 650, drawGapMs: 950, flightMs: 480, endMs: 1100 },
  /** The results stay on the table this long before the next betting opens. */
  results: { holdMs: 4300 },
  /** How long a tag over a seat, the phase banner and a dealer line stay. */
  tagLifeMs: 1800,
  bannerLifeMs: 1500,
  /** The table lets go this long after its last beat. */
  releaseMs: 150,
  /** Never hold the table longer than this, whatever happens to the pictures. */
  maxHoldMs: 40000,
} as const;

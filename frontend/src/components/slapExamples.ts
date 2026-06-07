import type { SlapPatternLabels } from '../locales/Messages';

export interface ExampleCard {
  rank: string;
  suit: string;
}

export interface SlapExample {
  key: keyof SlapPatternLabels;
  cards: ExampleCard[];
  plus?: boolean; // render a "+" between the cards (sum of ten)
}

// Language-independent card examples illustrating each slap pattern. The order
// here drives the order shown in the rules panel; the labels come from the
// active locale (keyed by `key`).
export const SLAP_EXAMPLES: SlapExample[] = [
  { key: 'doubles',  cards: [{ rank: '7', suit: 'spade' }, { rank: '7', suit: 'heart' }] },
  { key: 'sandwich', cards: [{ rank: '9', suit: 'club' }, { rank: '4', suit: 'diamond' }, { rank: '9', suit: 'spade' }] },
  { key: 'sumOfTen', cards: [{ rank: '6', suit: 'heart' }, { rank: '4', suit: 'spade' }], plus: true },
  { key: 'tens',     cards: [{ rank: '10', suit: 'diamond' }] },
];

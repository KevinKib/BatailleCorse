import type Card from '../Card';

/** The kilos a card weighs: Ace 100, King 13, Queen 12, Jack 11, the others their face value, the 7 nothing (flush). */
export function kiloValue(card: Card): number {
  switch (card.rank) {
    case 'ACE': return 100;
    case 'KING': return 13;
    case 'QUEEN': return 12;
    case 'JACK': return 11;
    case '7': return 0;
    default: return Number(card.rank) || 0;
  }
}

/** The weight of a set of kilo cards. */
export function kiloTotal(cards: readonly Card[]): number {
  return cards.reduce((sum, card) => sum + kiloValue(card), 0);
}

/** The letters of the face cards in a language (the screen takes them from the card names of the locales). */
export type FaceLetters = Record<'ACE' | 'KING' | 'QUEEN' | 'JACK', string>;

const ENGLISH_LETTERS: FaceLetters = { ACE: 'A', KING: 'K', QUEEN: 'Q', JACK: 'J' };

/** The face letters of a language: the first letter of its rank names (`kiloDeMerdeUi.ranks`: Roi gives R, Valet V). */
export function faceLetters(ranks: Record<string, string>): FaceLetters {
  const first = (rank: keyof FaceLetters) => ranks[rank]?.charAt(0).toUpperCase() || ENGLISH_LETTERS[rank];
  return { ACE: first('ACE'), KING: first('KING'), QUEEN: first('QUEEN'), JACK: first('JACK') };
}

const SUIT_SYMBOLS: Record<string, string> = { SPADE: '♠', HEART: '♥', DIAMOND: '♦', CLUB: '♣' };

/** A card in the short form of a table: `K♠`, `9♣`, `10♥`. */
export function kiloLabel(card: Card, letters: FaceLetters = ENGLISH_LETTERS): string {
  const rank = letters[card.rank as keyof FaceLetters] ?? card.rank;
  return `${rank}${SUIT_SYMBOLS[card.suit] ?? ''}`;
}

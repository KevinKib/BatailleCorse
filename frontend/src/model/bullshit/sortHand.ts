import type Card from '../Card';

// Display order of a hand: by rank (ace low, then 2..10, jack, queen, king), suits grouped
// at equal rank. Ranks absent from the deck (32-card game) simply never show up. The result
// depends only on the cards, so it stays stable when the hand changes; the domain is untouched.
const RANK_ORDER = ['ACE', '2', '3', '4', '5', '6', '7', '8', '9', '10', 'JACK', 'QUEEN', 'KING'];
const SUIT_ORDER = ['SPADE', 'HEART', 'CLUB', 'DIAMOND'];

const position = (order: string[], value: string) => {
  const i = order.indexOf(value.toUpperCase());
  return i === -1 ? order.length : i;
};

export function sortHand(cards: readonly Card[]): Card[] {
  return [...cards].sort((a, b) =>
    position(RANK_ORDER, a.rank) - position(RANK_ORDER, b.rank)
    || position(SUIT_ORDER, a.suit) - position(SUIT_ORDER, b.suit)
    || a.name.localeCompare(b.name));
}

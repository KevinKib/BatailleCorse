// Fixed deals for the President exchange specs (PUT /api/test/president/{id}/deal, test profile only).

const RANKS = ['A', '2', '3', '4', '5', '6', '7', '8', '9', '10', 'J', 'Q', 'K'];
const DECK = RANKS.flatMap((rank) => ['H', 'D', 'C', 'S'].map((suit) => `${rank}${suit}`));

/** The deal order of a fixed three-player deal: `first` gets the extra card, then `second` and `third`, 18 + 17 + 17 cards. */
export function dealFrom(first: string[], second: string[], third: string[]): string[] {
  const deal: string[] = [];
  for (let i = 0; i < 17; i++) deal.push(first[i], second[i], third[i]);
  deal.push(first[17]);
  return deal;
}

/** The deck minus `taken`, in a fixed order. */
export const without = (...taken: string[][]) => DECK.filter((card) => !taken.some((list) => list.includes(card)));

/**
 * Round 1 where Bob (seat 1) goes out first, Carol second and the browser last: Bob holds the king of hearts and the
 * high triples, Carol the low ones and a pair of aces, the browser singles, the 2s and two aces.
 */
export function roundOneDealBrowserLast(): string[] {
  const triple = (rank: string) => [`${rank}H`, `${rank}D`, `${rank}C`];
  const bob = [...['K', 'Q', 'J', '10', '9'].flatMap(triple), '8H', '8D'];
  const carol = [...['7', '6', '5', '4', '3'].flatMap(triple), 'AH', 'AD'];
  const browser = [
    ...['K', 'Q', 'J', '10', '9', '8', '7', '6', '5', '4', '3'].map((r) => `${r}S`),
    '2H', '2D', '2C', '2S', 'AC', 'AS', '8C',
  ];
  return dealFrom(browser, bob, carol);
}

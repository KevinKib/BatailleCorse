/**
 * The server names the rank of a pattern by the enum (`TEN`) and the rank of a card by its label (`10`). Every
 * comparison and every text goes through this: `JACK`, `QUEEN`, `KING` and `ACE` are the same in both.
 */
const NUMBER_NAMES: Record<string, string> = {
  TWO: '2', THREE: '3', FOUR: '4', FIVE: '5', SIX: '6', SEVEN: '7', EIGHT: '8', NINE: '9', TEN: '10',
};

export function canonicalRank(rank: string): string {
  return NUMBER_NAMES[rank] ?? rank;
}

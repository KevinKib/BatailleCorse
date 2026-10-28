/** The key of the sentence that names what a card did in the log (the texts live in the locales). */
export type NinetyNineEffectCode =
  | 'face' | 'ace' | 'reverse' | 'pass' | 'minusTen' | 'plusTen' | 'ninetyNine';

const BY_RANK: Record<string, NinetyNineEffectCode> = {
  ACE: 'ace', '4': 'reverse', '9': 'pass', '10': 'minusTen', JACK: 'plusTen', QUEEN: 'plusTen', KING: 'ninetyNine',
};

/**
 * Kept on the client on purpose: the server DTO carries no per-card effect label (its `playable` list says only
 * safe or not, and the Ace values), and this is display text, not a rule. Adding a label to the contract would
 * mean one more field on every card of the hand and of every event for words the client must translate anyway.
 *
 * Which sentence describes a card in the log line. A label only: the total after the play comes from the server,
 * nothing here computes it.
 */
export function effectCode(rank: string): NinetyNineEffectCode {
  return BY_RANK[rank] ?? 'face';
}

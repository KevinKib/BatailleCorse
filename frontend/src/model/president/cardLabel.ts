import type Card from '../Card';
import { format } from '../../locales/format';

/** The words a card name is made of; the locales hold them (`presidentUi.table`). */
export interface CardLabelTexts {
  /** '{rank}', '{suit}' */
  cardLabel: string;
  /** '{card}' */
  cardSelected: string;
  /** By the backend rank label (`JACK`...); a number is its own name. */
  ranks: Record<string, string>;
  /** By the backend suit (`HEART`...). */
  suits: Record<string, string>;
}

export function rankLabel(rank: string, texts: Pick<CardLabelTexts, 'ranks'>): string {
  return texts.ranks[rank] ?? rank;
}

/** "Queen of hearts", or "Queen of hearts, selected": the accessible name of a card of the hand. */
export function cardLabel(card: Card, texts: CardLabelTexts, selected: boolean): string {
  const name = format(texts.cardLabel, { rank: rankLabel(card.rank, texts), suit: texts.suits[card.suit] ?? card.suit.toLowerCase() });
  return selected ? format(texts.cardSelected, { card: name }) : name;
}

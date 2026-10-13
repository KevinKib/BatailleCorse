import type Card from '../model/Card';
import { useI18n } from './useI18n';
import { format } from '../locales/format';

/** "king of heart": the spoken name of a card (the same wording as a Bullshit hand card). */
export function useKoboCardName() {
  const ui = useI18n().koboUi.table;
  return (card: Card) => format(ui.cardName, { rank: card.rank.toLowerCase(), suit: card.suit.toLowerCase() });
}

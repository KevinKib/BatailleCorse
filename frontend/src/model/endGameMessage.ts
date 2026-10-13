import type { ForfeitReason } from './ForfeitReason';
import type { EndGameMessages } from '../locales/Messages';
import { format } from '../locales/format';

/**
 * End-screen subtitle. Winner-perspective only: when the opponent forfeited we
 * say how; otherwise it was a normal card win. The loser always sees the plain
 * "{opponent} won." — telling a resigner they resigned adds nothing.
 */
export function endGameMessage(
  didIWin: boolean,
  opponentLabel: string,
  opponentForfeitReason: ForfeitReason | null,
  texts: EndGameMessages,
): string {
  const vars = { opponent: opponentLabel };
  if (!didIWin) {
    return format(texts.won, vars);
  }
  switch (opponentForfeitReason) {
    case 'RESIGNED':
      return format(texts.resigned, vars);
    case 'DISCONNECTED':
      return format(texts.disconnected, vars);
    default:
      return format(texts.beat, vars);
  }
}

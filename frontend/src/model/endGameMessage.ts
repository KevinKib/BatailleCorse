import type { ForfeitReason } from './ForfeitReason';

/**
 * End-screen subtitle. Winner-perspective only: when the opponent forfeited we
 * say how; otherwise it was a normal card win. The loser always sees the plain
 * "{opponent} won." — telling a resigner they resigned adds nothing.
 */
export function endGameMessage(
  didIWin: boolean,
  opponentLabel: string,
  opponentForfeitReason: ForfeitReason | null,
): string {
  if (!didIWin) {
    return `${opponentLabel} won.`;
  }
  switch (opponentForfeitReason) {
    case 'RESIGNED':
      return `${opponentLabel} resigned.`;
    case 'DISCONNECTED':
      return `${opponentLabel} disconnected.`;
    default:
      return `You beat ${opponentLabel}!`;
  }
}

import type { BlackjackTableEvent } from './motion';

/** One live-region line: a locale key and its parameters (seats are numbers, the screen names them). */
export interface BlackjackEventLine {
  key: string;
  params: Record<string, number>;
}

const line = (name: string, params: Record<string, number> = {}): BlackjackEventLine =>
  ({ key: `blackjackUi.events.${name}`, params });

/**
 * What a table event says to a screen reader, as a key and parameters; null when it has nothing to say (no seat to
 * name, an unknown event, or a settlement that does not concern me). Bet amounts are never read: bets are sealed.
 */
export function eventLine(event: BlackjackTableEvent, mySeat: number): BlackjackEventLine | null {
  const d = event.data;
  if (!d) return null;
  const seat = d.seat;
  // A line about my own seat uses its `…You` key: "you" takes its own verb form ("You bust", "Vous brûlez").
  const bySeat = (name: string, params: Record<string, number> = {}): BlackjackEventLine | null =>
    seat === null ? null : line(seat === mySeat ? `${name}You` : name, { seat, ...params });
  switch (event.eventType) {
    case 'BET': return bySeat('bet');
    case 'DEAL': return line('deal', { hand: d.handNumber ?? 0 });
    case 'DEALER_BLACKJACK': return line('dealerBlackjack');
    case 'HIT': return bySeat('hit', { total: d.value ?? 0 });
    case 'STAND': return bySeat('stand');
    case 'DOUBLE': return bySeat('double', { total: d.value ?? 0 });
    case 'SPLIT': return bySeat('split');
    case 'BUST': return bySeat('bust', { total: d.value ?? 0 });
    case 'DEALER_PLAY': {
      const total = d.value ?? 0;
      return line(total > 21 ? 'dealerBusts' : 'dealerStands', { total });
    }
    case 'SETTLE': {
      const net = d.nets?.[mySeat];
      if (net === undefined) return null;
      return net > 0 ? line('settleWin', { amount: net }) : net < 0 ? line('settleLoss', { amount: -net }) : line('settlePush');
    }
    case 'ELIMINATED': return bySeat('eliminated');
    case 'GAME_OVER': return line('gameOver');
    default: return null;
  }
}

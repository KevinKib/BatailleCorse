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
  switch (event.eventType) {
    case 'BET': return seat === null ? null : line('bet', { seat });
    case 'DEAL': return line('deal', { hand: d.handNumber ?? 0 });
    case 'DEALER_BLACKJACK': return line('dealerBlackjack');
    case 'HIT': return seat === null ? null : line('hit', { seat, total: d.value ?? 0 });
    case 'STAND': return seat === null ? null : line('stand', { seat });
    case 'DOUBLE': return seat === null ? null : line('double', { seat, total: d.value ?? 0 });
    case 'SPLIT': return seat === null ? null : line('split', { seat });
    case 'BUST': return seat === null ? null : line('bust', { seat, total: d.value ?? 0 });
    case 'DEALER_PLAY': {
      const total = d.value ?? 0;
      return line(total > 21 ? 'dealerBusts' : 'dealerStands', { total });
    }
    case 'SETTLE': {
      const net = d.nets?.[mySeat];
      if (net === undefined) return null;
      return net > 0 ? line('settleWin', { amount: net }) : net < 0 ? line('settleLoss', { amount: -net }) : line('settlePush');
    }
    case 'ELIMINATED': return seat === null ? null : line('eliminated', { seat });
    case 'GAME_OVER': return line('gameOver');
    default: return null;
  }
}

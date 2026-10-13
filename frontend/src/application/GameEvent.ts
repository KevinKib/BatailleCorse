import type BatailleCorse from '../model/BatailleCorse';
import type Card from '../model/Card';
import type SessionSeat from '../model/SessionSeat';
import type { LobbyView } from '../model/lobby/LobbyView';
import type { SeatLifecycleEventType } from '../model/SeatLifecycleEvents';

export type GameEvent =
  | { type: 'state-update'; state: BatailleCorse }
  | { type: 'lobby-update'; lobby: LobbyView }
  | { type: 'seats-change'; seats: SessionSeat[] }
  | { type: 'game-id-change'; gameId: string }
  | { type: 'my-index-change'; playerIndex: number }
  | { type: 'error'; eventType: string; message: string }
  | { type: 'removed' }
  | { type: 'send'; playerIndex: number; seq: number; topCard: Card | undefined }
  | { type: 'grab'; winnerPlayerIndex: number; seq: number; pileCards: Card[] }
  | { type: 'slap' }
  | { type: 'successful-slap'; winnerPlayerIndex: number; seq: number; pileCards: Card[] }
  | { type: 'erroneous-slap'; playerIndex: number; seq: number }
  | { type: 'presence-event'; eventType: SeatLifecycleEventType; eventData: unknown };

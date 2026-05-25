import type BatailleCorse from '../model/BatailleCorse';
import type Card from '../model/Card';

export type GameEvent =
  | { type: 'state-update'; state: BatailleCorse }
  | { type: 'game-id-change'; gameId: string }
  | { type: 'send'; playerIndex: number; seq: number; topCard: Card | undefined }
  | { type: 'grab'; winnerPlayerIndex: number; seq: number; pileCards: Card[] }
  | { type: 'slap' }
  | { type: 'successful-slap'; winnerPlayerIndex: number; seq: number; pileCards: Card[] }
  | { type: 'erroneous-slap'; playerIndex: number; seq: number };

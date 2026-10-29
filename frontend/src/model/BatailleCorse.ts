import Pile from "./Pile";
import Player from "./Player";
import type PlayerId from "./PlayerId";
import type { ForfeitReason } from "./ForfeitReason";
import type { Action } from "./Action";

export default class BatailleCorse {
  constructor(
    public readonly currentPlayer: Player,
    public readonly pile: Pile,
    public readonly players: Player[],
    public readonly winner: PlayerId | null,
  ) {}

  isOver(): boolean {
    return this.winner !== null;
  }

  isWinner(playerId: string | undefined): boolean {
    return this.winner !== null && this.winner.id === playerId;
  }

  isWinnerAt(playerIndex: number): boolean {
    return this.isWinner(this.players[playerIndex]?.id);
  }

  /**
   * The forfeit reason of the seat opposite the given one, or null. 2-player:
   * the single other seat. Used by the winner's end screen to explain a forfeit.
   */
  opponentForfeitReason(playerIndex: number): ForfeitReason | null {
    const opponent = this.players.find((_, i) => i !== playerIndex);
    return opponent?.forfeitReason ?? null;
  }

  /**
   * Whether the player at the given seat may take their turn right now, i.e. the
   * server currently offers them the SEND action. This is the authoritative
   * "whose turn" signal: the backend only offers SEND to the player whose turn it
   * is and only while a card can be added (not when the pile is full / grabbable
   * or the game is finished).
   */
  canSend(playerIndex: number): boolean {
    return this.canPlayerAct(playerIndex, 'send');
  }

  /**
   * Whether the player at the given seat currently has the given action available
   * (server-provided state, not client-side authorization — the server validates
   * every request independently). The view uses this to enable/disable action
   * buttons; the generalisation of {@link canSend} to any action.
   */
  canPlayerAct(playerIndex: number, action: Action): boolean {
    return this.players[playerIndex]?.hasAvailableAction(action.toUpperCase()) ?? false;
  }

  static fromJSON(data: {
    /** The game id (a bare UUID), as every game's state carries it. */
    id: string;
    /** Increases with every change of the game. */
    version: number;
    currentPlayer: { id: string; nbCards: number; availableActions: string[]; forfeitReason?: string | null };
    pile: {
      cards: { rank: string; suit: string; name: string }[];
      grabbable: boolean;
      nbCardsSinceLastHonourCard: number;
      /** Null until an honour card is on the pile. */
      playerThatAddedLastHonourCard: { id: string } | null;
    };
    players: { id: string; nbCards: number; availableActions: string[]; forfeitReason?: string | null }[];
    winner: { id: string } | null;
  }): BatailleCorse {
    return new BatailleCorse(
      Player.fromJSON(data.currentPlayer),
      Pile.fromJSON(data.pile),
      data.players.map(p => Player.fromJSON(p)),
      data.winner ?? null,
    );
  }
}
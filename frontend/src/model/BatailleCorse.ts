import Pile from "./Pile";
import Player from "./Player";
import type PlayerId from "./PlayerId";
import type { ForfeitReason } from "./ForfeitReason";

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
    return this.players[playerIndex]?.hasAvailableAction('SEND') ?? false;
  }

  static fromJSON(data: {
    currentPlayer: { id: string; nbCards: number; availableActions: string[] };
    pile: {
      cards: { rank: string; suit: string; name: string }[];
      grabbable: boolean;
      nbCardsSinceLastHonourCard: number;
      playerThatAddedLastHonourCard: { id: string };
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
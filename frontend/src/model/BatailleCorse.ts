import Pile from "./Pile";
import Player from "./Player";
import type PlayerId from "./PlayerId";

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

  isTurnOf(playerIndex: number): boolean {
    return this.currentPlayer.id === this.players[playerIndex]?.id;
  }

  static fromJSON(data: {
    currentPlayer: { id: string; nbCards: number; availableActions: string[] };
    pile: {
      cards: { rank: string; suit: string; name: string }[];
      grabbable: boolean;
      nbCardsSinceLastHonourCard: number;
      playerThatAddedLastHonourCard: { id: string };
    };
    players: { id: string; nbCards: number; availableActions: string[] }[];
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
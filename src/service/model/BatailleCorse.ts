import Card from "./Card";
import Player from "./Player";

export default interface BatailleCorse {

  currentPlayer: Player,
  pile: Card[],
  players: Player[],

}
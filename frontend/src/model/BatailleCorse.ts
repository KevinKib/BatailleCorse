import Card from "./Card";
import Pile from "./Pile";
import Player from "./Player";
import PlayerId from "./PlayerId";

export default class BatailleCorse {

  currentPlayer: Player;
  pile: Pile;
  players: Player[];
  winner: PlayerId | null;

}
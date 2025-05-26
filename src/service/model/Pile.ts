import Card from "./Card";
import PlayerId from "./PlayerId";

export default interface Pile {
  cards: Card[],
  grabbable: boolean,
  nbCardsSinceLastHonourCard: number,
  playerThatAddedLastHonourCard: PlayerId
}
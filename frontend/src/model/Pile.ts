import type Card from "./Card";
import type PlayerId from "./PlayerId";

export default class Pile {
  constructor(
    public readonly cards: Card[],
    public readonly grabbable: boolean,
    public readonly nbCardsSinceLastHonourCard: number,
    public readonly playerThatAddedLastHonourCard: PlayerId,
  ) {}

  /**
   * Returns the index of the player who should auto-grab the pile,
   * or null if the pile is not in a grabbable state.
   * Reads server-provided state — not client-side authorization.
   */
  getAutoGrabPlayer(): number | null {
    if (!this.grabbable) return null;
    const id = Number(this.playerThatAddedLastHonourCard?.id);
    return isNaN(id) ? null : id;
  }

  static fromJSON(data: {
    cards: Card[];
    grabbable: boolean;
    nbCardsSinceLastHonourCard: number;
    playerThatAddedLastHonourCard: PlayerId;
  }): Pile {
    return new Pile(
      data.cards,
      data.grabbable,
      data.nbCardsSinceLastHonourCard,
      data.playerThatAddedLastHonourCard,
    );
  }
}
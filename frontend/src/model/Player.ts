export default class Player {
  constructor(
    public readonly id: string,
    public readonly nbCards: number,
    public readonly availableActions: string[],
  ) {}

  /**
   * Returns whether this player has the given action available.
   * Reads server-provided state — not client-side authorization.
   * The server validates all action requests independently.
   */
  hasAvailableAction(action: string): boolean {
    return this.availableActions.includes(action);
  }

  static fromJSON(data: {
    id: string;
    nbCards: number;
    availableActions: string[];
  }): Player {
    return new Player(data.id, data.nbCards, data.availableActions);
  }
}

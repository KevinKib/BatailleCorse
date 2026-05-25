import type BatailleCorse from '../BatailleCorse';
import type Pile from '../Pile';

export default class AI {
  private timeoutId: ReturnType<typeof setTimeout> | undefined;

  constructor(
    private readonly playerIndex: number,
    private readonly reactionTime: number,
  ) {}

  /**
   * Schedules the AI's next action based on the current game state.
   * State and actions are injected at call time — no store dependency.
   * A second call before the timeout fires replaces the pending action.
   */
  play(state: BatailleCorse, actions: { send(): void; slap(): void }): void {
    clearTimeout(this.timeoutId);
    const variation = Math.floor(Math.random() * 200) - 100;
    const delay = Math.max(0, this.reactionTime + variation);
    this.timeoutId = setTimeout(() => {
      if (this.shouldAttemptSlap(state.pile)) {
        actions.slap(); // server validates; AI accepts any resulting penalty
      } else if (state.players[this.playerIndex]?.hasAvailableAction('SEND')) {
        actions.send();
      }
    }, delay);
  }

  /** Cancels the pending scheduled action, if any. */
  cancel(): void {
    clearTimeout(this.timeoutId);
    this.timeoutId = undefined;
  }

  /**
   * Internal AI heuristic for when to attempt a slap.
   * NOT authoritative game rules — the server validates all slap attempts.
   * These conditions are game-variant-specific and will need to become
   * configurable when game variants are introduced.
   */
  private shouldAttemptSlap(pile: Pile): boolean {
    const cards = pile.cards;
    if (cards.length >= 1 && cards[0].rank === '10') return true;
    if (cards.length >= 2 && cards[0].rank === cards[1].rank) return true;
    if (cards.length >= 3 && cards[0].rank === cards[2].rank) return true;
    if (cards.length >= 2) {
      const r0 = Number(cards[0].rank);
      const r1 = Number(cards[1].rank);
      if (!isNaN(r0) && !isNaN(r1) && r0 + r1 === 10) return true;
    }
    return false;
  }
}

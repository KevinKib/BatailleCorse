import type { KoboPending } from './interaction';
import type { KoboState } from './KoboState';

const NONE: KoboPending = { kind: 'none' };

/**
 * What is left of a local selection once a new state came in. A selection belongs to one situation: it goes
 * when the action that needs it is no longer listed, when the card it points at changed (revision) or left,
 * and on a new round. Pure: it checks the selection against what the server now says, no rule.
 */
export function reconcilePending(
  pending: KoboPending, previous: KoboState | null, next: KoboState | null, mySeat: number,
): KoboPending {
  if (pending.kind === 'none') return pending;
  if (!next) return NONE;
  if (previous && next.round !== previous.round) return NONE;

  switch (pending.kind) {
    case 'match-armed':
      return next.availableActions.includes('MATCH_DISCARD') ? pending : NONE;
    case 'match-give': {
      if (!next.availableActions.includes('MATCH_DISCARD')) return NONE;
      const slot = next.players.find(p => p.seat === pending.target.seat)
        ?.slots.find(s => s.slot === pending.target.slot);
      return slot && slot.occupied && slot.revision === pending.expectedRevision ? pending : NONE;
    }
    case 'blind-own': {
      if (!next.availableActions.includes('BLIND_SWAP')) return NONE;
      const mine = (state: KoboState | null) =>
        state?.players.find(p => p.seat === mySeat)?.slots.find(s => s.slot === pending.ownSlot);
      const after = mine(next);
      if (!after || !after.occupied) return NONE;
      const before = mine(previous);
      return before && before.revision !== after.revision ? NONE : pending;
    }
  }
}

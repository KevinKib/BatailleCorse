import { describe, it, expect, beforeEach } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';
import { useBullshitStore } from './Bullshit.store';
import type { BullshitState } from '../model/bullshit/BullshitState';

function state(overrides: Partial<BullshitState> = {}): BullshitState {
  return {
    id: 'g1', gameType: 'bullshit',
    myHand: [{ rank: 'ACE', suit: 'HEART', name: 'HEART_ACE' }, { rank: 'KING', suit: 'SPADE', name: 'SPADE_KING' }],
    availableActions: ['DISCARD'],
    players: [
      { id: '0', handCount: 2, isCurrentPlayer: true },
      { id: '1', handCount: 5, isCurrentPlayer: false },
    ],
    currentTarget: { label: 'ACE' },
    discardPileSize: 0,
    table: { state: 'NO_CLAIM' },
    pendingWinner: { state: 'NONE' },
    outcome: { status: 'ONGOING' },
    ...overrides,
  };
}

describe('Bullshit store', () => {
  beforeEach(() => { setActivePinia(createPinia()); localStorage.clear(); });

  it('isMyTurn / canDiscard reflect my seat and available actions', () => {
    const store = useBullshitStore();
    store.applyEvent({ type: 'seat-change', seat: 0 });
    store.applyEvent({ type: 'state-update', state: state() });
    expect(store.isMyTurn).toBe(true);
    expect(store.canDiscard).toBe(true);
    expect(store.canCallBullshit).toBe(false);
  });

  it('phase is waiting until a JOIN event arrives, then playing', () => {
    const store = useBullshitStore();
    store.markCreated();
    store.applyEvent({ type: 'state-update', state: state() });
    expect(store.phase).toBe('waiting');
    store.applyEvent({ type: 'event', eventType: 'JOIN', eventData: {}, message: '' });
    expect(store.phase).toBe('playing');
  });

  it('phase is finished and iWon true when I am the winner', () => {
    const store = useBullshitStore();
    store.applyEvent({ type: 'seat-change', seat: 0 });
    store.applyEvent({ type: 'state-update', state: state({ outcome: { status: 'FINISHED', winnerId: '0' } }) });
    expect(store.phase).toBe('finished');
    expect(store.iWon).toBe(true);
  });

  it('records a CALL_BULLSHIT reveal', () => {
    const store = useBullshitStore();
    const reveal = { callerSeat: 1, claimantSeat: 0, truthful: false, pickerSeat: 0, revealedCards: [] };
    store.applyEvent({ type: 'event', eventType: 'CALL_BULLSHIT', eventData: reveal, message: 'm' });
    expect(store.reveal).toEqual(reveal);
  });

  it('toggleCard selects and deselects from the hand', () => {
    const store = useBullshitStore();
    const card = { rank: 'ACE', suit: 'HEART', name: 'HEART_ACE' };
    store.toggleCard(card);
    expect(store.selectedCards).toHaveLength(1);
    store.toggleCard(card);
    expect(store.selectedCards).toHaveLength(0);
  });
});

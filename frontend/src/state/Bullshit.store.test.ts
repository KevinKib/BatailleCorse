import { describe, it, expect, beforeEach, vi } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';
import { useBullshitStore } from './Bullshit.store';
import type { BullshitState } from '../model/bullshit/BullshitState';
import type { LobbyView } from '../model/bullshit/LobbyView';
import webSocketService from '../service/WebSocketService';

function state(overrides: Partial<BullshitState> = {}): BullshitState {
  return {
    started: true,
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

function lobbyView(overrides: Partial<LobbyView> = {}): LobbyView {
  return {
    started: false,
    gameId: 'g1',
    players: [
      { seat: 0, name: 'Alice', joined: true },
      { seat: 1, name: null, joined: false },
    ],
    hostSeat: 0,
    mySeat: 0,
    minPlayers: 2,
    maxPlayers: 6,
    canStart: false,
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

  it('a lobby view yields phase lobby and exposes isHost/canStart', () => {
    const store = useBullshitStore();
    store.applyEvent({ type: 'seat-change', seat: 0 });
    store.applyEvent({ type: 'state-update', state: lobbyView({ canStart: true }) });
    expect(store.phase).toBe('lobby');
    expect(store.lobby).not.toBeNull();
    expect(store.game).toBeNull();
    expect(store.isHost).toBe(true);
    expect(store.canStart).toBe(true);
  });

  it('a started view yields phase playing and exposes game', () => {
    const store = useBullshitStore();
    store.applyEvent({ type: 'seat-change', seat: 0 });
    store.applyEvent({ type: 'state-update', state: state() });
    expect(store.phase).toBe('playing');
    expect(store.game).not.toBeNull();
    expect(store.lobby).toBeNull();
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

  it('tracks rematch progress from a PENDING REMATCH event', () => {
    const store = useBullshitStore();
    store.applyEvent({ type: 'event', eventType: 'REMATCH',
      eventData: { status: 'PENDING', ready: 1, eligible: 3 }, message: '' });

    expect(store.rematchReady).toBe(1);
    expect(store.rematchEligible).toBe(3);
  });

  it('rematch() marks me as requested so the button shows waiting', () => {
    const store = useBullshitStore();
    vi.spyOn(webSocketService, 'publish').mockImplementation(() => {});
    store.applyEvent({ type: 'event', eventType: 'REMATCH',
      eventData: { status: 'PENDING', ready: 1, eligible: 2 }, message: '' });

    store.rematch();

    expect(store.rematchButton).toEqual({ label: 'Waiting… 1/2 ready', disabled: true });
  });

  it('leaveRematch() publishes the leave frame', () => {
    const store = useBullshitStore();
    const spy = vi.spyOn(webSocketService, 'publish').mockImplementation(() => {});

    store.leaveRematch();

    expect(spy).toHaveBeenCalledWith('/app/bullshit/leaveRematch', expect.any(String));
  });
});

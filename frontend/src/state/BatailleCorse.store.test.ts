import { describe, it, expect, beforeEach, vi } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';

vi.mock('../service/WebSocketService', () => ({
  default: {
    subscribeToGame: vi.fn(),
    unsubscribeFromGame: vi.fn(),
    setPresence: vi.fn(),
    clearPresence: vi.fn(),
    publish: vi.fn(),
  },
}));

import { useBatailleCorseStore } from './BatailleCorse.store';

describe('BatailleCorse store — opponent presence', () => {
  beforeEach(() => { setActivePinia(createPinia()); localStorage.clear(); });

  function disconnect(store: ReturnType<typeof useBatailleCorseStore>, seat: number, deadline = 1_060_000) {
    store.applyEvent({ type: 'presence-event', eventType: 'OPPONENT_DISCONNECTED',
      eventData: { disconnectedSeat: seat, deadlineEpochMs: deadline } });
  }

  it('exposes a multiplayer opponent disconnect via liveDisconnections', () => {
    const store = useBatailleCorseStore();
    store.mode = 'multiplayer';
    store.myPlayerIndex = 0;
    disconnect(store, 1);
    expect(store.liveDisconnections).toEqual({ 1: 1_060_000 });
  });

  it('clears the disconnect on reconnect', () => {
    const store = useBatailleCorseStore();
    store.mode = 'multiplayer';
    store.myPlayerIndex = 0;
    disconnect(store, 1);
    store.applyEvent({ type: 'presence-event', eventType: 'OPPONENT_RECONNECTED',
      eventData: { reconnectedSeat: 1 } });
    expect(store.liveDisconnections).toEqual({});
  });

  it('filters out disconnects in solo mode', () => {
    const store = useBatailleCorseStore();
    store.mode = 'solo';
    store.myPlayerIndex = 0;
    disconnect(store, 1);
    expect(store.liveDisconnections).toEqual({});
  });

  it('filters out a disconnect carrying my own seat', () => {
    const store = useBatailleCorseStore();
    store.mode = 'multiplayer';
    store.myPlayerIndex = 0;
    disconnect(store, 0);
    expect(store.liveDisconnections).toEqual({});
  });

  it('resets presence when a rematch starts', () => {
    const store = useBatailleCorseStore();
    store.mode = 'multiplayer';
    store.myPlayerIndex = 0;
    disconnect(store, 1);
    expect(store.liveDisconnections).toEqual({ 1: 1_060_000 });

    store.applyEvent({ type: 'rematch', status: 'started' });
    expect(store.liveDisconnections).toEqual({});
  });
});

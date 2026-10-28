import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';
import webSocketService from '../service/WebSocketService';
import { useBatailleCorseStore } from './BatailleCorse.store';
import { useBlackjackStore } from './Blackjack.store';
import { useBullshitStore } from './Bullshit.store';
import { useDraughtsStore } from './Draughts.store';
import { useKiloDeMerdeStore } from './KiloDeMerde.store';
import { useKoboStore } from './Kobo.store';
import { useMangolaStore } from './Mangola.store';
import { useNinetyNineStore } from './NinetyNine.store';
import { usePresidentStore } from './President.store';
import { useThreeMenMorrisStore } from './ThreeMenMorris.store';

// The Private game switch of every lobby goes through its game's store to the shared /app/lobby/setPrivate command.
const stores = {
  bataillecorse: useBatailleCorseStore,
  blackjack: useBlackjackStore,
  bullshit: useBullshitStore,
  draughts: useDraughtsStore,
  kilodemerde: useKiloDeMerdeStore,
  kobo: useKoboStore,
  mangola: useMangolaStore,
  ninetynine: useNinetyNineStore,
  president: usePresidentStore,
  threemenmorris: useThreeMenMorrisStore,
} as const;

describe('setPrivate through each game store', () => {
  beforeEach(() => { setActivePinia(createPinia()); localStorage.clear(); });
  afterEach(() => { vi.restoreAllMocks(); });

  for (const [key, useStore] of Object.entries(stores)) {
    it(`${key}: sends privateGame with the seat token to /app/lobby/setPrivate`, () => {
      const store = useStore();
      store.restore('g1', 0, 'tok');
      const publish = vi.spyOn(webSocketService, 'publish').mockImplementation(() => {});
      publish.mockClear();
      store.setPrivate(true);
      expect(publish).toHaveBeenCalledTimes(1);
      const [destination, body] = publish.mock.calls[0];
      expect(destination).toBe('/app/lobby/setPrivate');
      expect(JSON.parse(body as string)).toEqual({ gameId: 'g1', token: 'tok', privateGame: true });
    });
  }
});

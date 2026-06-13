import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { shallowMount, flushPromises } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { nextTick } from 'vue';

// Captures the guard registered via onBeforeRouteLeave so we can invoke it directly.
const routeLeaveGuards: Array<(...args: unknown[]) => unknown> = [];

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { id: 'g1' } }),
  useRouter: () => ({ replace: vi.fn(), push: vi.fn() }),
  onBeforeRouteLeave: (cb: (...args: unknown[]) => unknown) => { routeLeaveGuards.push(cb); },
  RouterLink: { name: 'RouterLink', template: '<a><slot/></a>' },
}));

vi.mock('../../service/WebSocketService', () => ({
  default: {
    subscribeToGame: vi.fn(),
    unsubscribeFromGame: vi.fn(),
    setPresence: vi.fn(),
    clearPresence: vi.fn(),
    publish: vi.fn(),
  },
}));

import GameScreen from './GameScreen.vue';
import DisconnectOverlay from '../../components/DisconnectOverlay.vue';
import { useBatailleCorseStore } from '../../state/BatailleCorse.store';
import { buildGame } from '../../model/fixtures';

async function mountGameScreen() {
  const wrapper = shallowMount(GameScreen, {
    global: { stubs: { RouterLink: true } },
  });
  await flushPromises(); // let onMounted settle (fetch is stubbed to fail -> early return)
  return wrapper;
}

describe('GameScreen', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    routeLeaveGuards.length = 0;
    localStorage.setItem('tokens:g1', JSON.stringify({ 0: 'tok' }));
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false }));
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    localStorage.clear();
  });

  describe('opponent-disconnect countdown', () => {
    it('shows the countdown banner while the opponent is disconnected', async () => {
      const wrapper = await mountGameScreen();
      const store = useBatailleCorseStore();
      store.mode = 'multiplayer';
      store.myPlayerIndex = 0;
      store.state = buildGame();
      store.opponentConnection = { status: 'disconnected', seat: 1, deadlineEpochMs: Date.now() + 60_000 };
      await nextTick();

      const overlay = wrapper.findComponent(DisconnectOverlay);
      expect(overlay.exists()).toBe(true);
      expect(overlay.props('secondsRemaining')).toBeGreaterThan(0);
    });

    it('hides the banner once the opponent reconnects', async () => {
      const wrapper = await mountGameScreen();
      const store = useBatailleCorseStore();
      store.mode = 'multiplayer';
      store.myPlayerIndex = 0;
      store.state = buildGame();
      store.opponentConnection = { status: 'connected', seat: 1 };
      await nextTick();

      expect(wrapper.findComponent(DisconnectOverlay).exists()).toBe(false);
    });
  });

  describe('leave confirmation guard', () => {
    it('forfeits and allows leaving when confirmed in multiplayer', async () => {
      await mountGameScreen();
      const store = useBatailleCorseStore();
      store.mode = 'multiplayer';
      store.myPlayerIndex = 0;
      store.waiting = false;
      store.state = buildGame();
      const forfeitSpy = vi.spyOn(store, 'forfeit');
      window.confirm = vi.fn().mockReturnValue(true);

      const result = routeLeaveGuards[0]();

      expect(result).toBe(true);
      expect(forfeitSpy).toHaveBeenCalledWith(0);
    });

    it('blocks leaving and does not forfeit when cancelled', async () => {
      await mountGameScreen();
      const store = useBatailleCorseStore();
      store.mode = 'multiplayer';
      store.waiting = false;
      store.state = buildGame();
      const forfeitSpy = vi.spyOn(store, 'forfeit');
      window.confirm = vi.fn().mockReturnValue(false);

      const result = routeLeaveGuards[0]();

      expect(result).toBe(false);
      expect(forfeitSpy).not.toHaveBeenCalled();
    });
  });
});

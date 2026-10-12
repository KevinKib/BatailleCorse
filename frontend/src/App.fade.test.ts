import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { defineComponent, h } from 'vue';
import { createMemoryHistory, createRouter } from 'vue-router';
import App from './App.vue';
import { ROUTE_FADE_MS } from './composables/routeFade';

// Two routed views, like the Game selection and a Bullshit menu. A navigation that lands in the
// middle of a fade (select a game, then Back at once) must never leave two views on screen.
const view = (name: string) => defineComponent({ render: () => h('section', { 'data-view': name }, name) });

function setup() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/games', component: view('picker') },
      { path: '/games/bullshit', component: view('bullshit') },
    ],
  });
  const wrapper = mount(App, { global: { plugins: [router], stubs: { transition: false } }, attachTo: document.body });
  const views = () => [...document.querySelectorAll<HTMLElement>('[data-view]')];
  const go = async (path: string) => { await router.push(path); await flushPromises(); };
  return { wrapper, views, go };
}

describe('App route fade', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.stubGlobal('requestAnimationFrame', (cb: FrameRequestCallback) => setTimeout(() => cb(0), 0));
  });
  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
    document.body.innerHTML = '';
  });

  it('shows a single view once the fades are over, after Back right after a selection', async () => {
    const { wrapper, views, go } = setup();
    await go('/games');
    vi.advanceTimersByTime(ROUTE_FADE_MS + 300);
    await go('/games/bullshit');
    vi.advanceTimersByTime(100);
    await go('/games');
    // The Bullshit enter fade would end now: the view is leaving and must stay faded out, not pop back.
    vi.advanceTimersByTime(ROUTE_FADE_MS + 150);
    const bullshit = views().find(v => v.dataset.view === 'bullshit');
    expect(bullshit?.style.opacity).toBe('0');
    vi.advanceTimersByTime(ROUTE_FADE_MS + 300);
    await flushPromises();
    expect(views().map(v => v.dataset.view)).toEqual(['picker']);
    expect(views()[0].style.position).toBe('');
    expect(views()[0].style.opacity).toBe('');
    wrapper.unmount();
  });

  it('never keeps more than the entering view and the one just left, however fast it goes back and forth', async () => {
    const { wrapper, views, go } = setup();
    await go('/games');
    vi.advanceTimersByTime(ROUTE_FADE_MS + 300);
    for (let i = 0; i < 6; i++) {
      await go(i % 2 === 0 ? '/games/bullshit' : '/games');
      vi.advanceTimersByTime(60);
      expect(views().length).toBeLessThanOrEqual(2);
    }
    vi.advanceTimersByTime(ROUTE_FADE_MS + 300);
    await flushPromises();
    expect(views().map(v => v.dataset.view)).toEqual(['picker']);
    expect(views()[0].style.position).toBe('');
    wrapper.unmount();
  });
});

import { describe, it, expect } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { createMemoryHistory } from 'vue-router';
import PrimeVue from 'primevue/config';
import EndGameOverlay from './EndGameOverlay.vue';
import { createAppRouter } from '../router';

async function mountOverlay() {
  const router = createAppRouter(createMemoryHistory());
  await router.push('/games/bataillecorse/room/g1');
  await router.isReady();
  const wrapper = mount(EndGameOverlay, {
    props: { didIWin: true, subtitle: 'sub', rematchButton: { label: 'Play again', disabled: false } },
    global: { plugins: [router, PrimeVue] },
  });
  return { wrapper, router };
}

describe('EndGameOverlay', () => {
  it('givenHomeButton_thenAlwaysLeadsToTheGameSelection', async () => {
    const { wrapper, router } = await mountOverlay();
    expect(wrapper.get('a.end-home-button').attributes('href')).toBe('/games');
    await wrapper.get('a.end-home-button').trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.path).toBe('/games');
  });

  it('givenPlayAgain_thenEmitsWithoutNavigatingAway', async () => {
    const { wrapper, router } = await mountOverlay();
    await wrapper.get('[data-cy="play-again"]').trigger('click');
    expect(wrapper.emitted('playAgain')).toHaveLength(1);
    expect(router.currentRoute.value.path).toBe('/games/bataillecorse/room/g1');
  });
});

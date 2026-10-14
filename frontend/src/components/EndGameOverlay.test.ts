import { describe, it, expect } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { createMemoryHistory } from 'vue-router';
import PrimeVue from 'primevue/config';
import EndGameOverlay from './EndGameOverlay.vue';
import { createAppRouter } from '../router';
import { messagesEn } from '../locales/en';

async function mountOverlay(props: Record<string, unknown> = {}, slots: Record<string, string> = {}) {
  const router = createAppRouter(createMemoryHistory());
  await router.push('/games/bataillecorse/room/g1');
  await router.isReady();
  const wrapper = mount(EndGameOverlay, {
    props: { didIWin: true, subtitle: 'sub', rematchButton: { label: 'Play again', disabled: false }, ...props },
    slots,
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

  it('givenAWin_thenTheTitleAndTheHomeLabelComeFromTheLocales', async () => {
    const { wrapper } = await mountOverlay({ didIWin: true });
    expect(wrapper.get('.end-title').text()).toBe(messagesEn.endScreen.victory);
    expect(wrapper.get('a.end-home-button').text()).toBe(messagesEn.endScreen.backToHome);
  });

  it('givenADefeat_thenTheTitleComesFromTheLocales', async () => {
    const { wrapper } = await mountOverlay({ didIWin: false });
    expect(wrapper.get('.end-title').text()).toBe(messagesEn.endScreen.defeat);
  });

  it('givenADrawResult_thenTheTitleIsNeutralWithoutTrophy', async () => {
    const { wrapper } = await mountOverlay({ didIWin: false, result: 'draw' });
    expect(wrapper.get('.end-title').text()).toBe(messagesEn.endScreen.draw);
    expect(wrapper.find('[data-cy="victory-flourish"]').exists()).toBe(false);
    expect(wrapper.get('.end-card').classes()).toContain('end-card--draw');
    expect(wrapper.get('.end-card').classes()).not.toContain('end-card--defeat');
  });

  it('givenNoResult_thenItFollowsDidIWin', async () => {
    const win = await mountOverlay({ didIWin: true });
    expect(win.wrapper.get('.end-card').classes()).toContain('end-card--victory');
    const loss = await mountOverlay({ didIWin: false });
    expect(loss.wrapper.get('.end-card').classes()).toContain('end-card--defeat');
  });

  it('givenAnExplicitVictoryResult_thenItOverridesDidIWin', async () => {
    const { wrapper } = await mountOverlay({ didIWin: false, result: 'victory' });
    expect(wrapper.get('.end-title').text()).toBe(messagesEn.endScreen.victory);
  });

  it('givenNoSlotContent_thenNothingIsRenderedUnderTheSubtitle', async () => {
    const { wrapper } = await mountOverlay();
    expect(wrapper.find('[data-test="end-extra"]').exists()).toBe(false);
  });

  it('givenSlotContent_thenItIsShownUnderTheSubtitleAndBeforeTheButtons', async () => {
    const { wrapper } = await mountOverlay({}, { default: '<ol data-test="scores"><li>Alice 12</li></ol>' });
    expect(wrapper.get('[data-test="end-extra"]').text()).toContain('Alice 12');
    const html = wrapper.html();
    expect(html.indexOf('end-sub')).toBeLessThan(html.indexOf('data-test="scores"'));
    expect(html.indexOf('data-test="scores"')).toBeLessThan(html.indexOf('end-actions'));
  });
});

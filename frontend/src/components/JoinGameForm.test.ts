import { describe, it, expect, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { setActivePinia, createPinia } from 'pinia';
import { createMemoryHistory } from 'vue-router';
import PrimeVue from 'primevue/config';
import JoinGameForm from './JoinGameForm.vue';
import { createAppRouter } from '../router';
import { findGame } from '../model/games';
import { messagesEn } from '../locales/en';
import { HttpError } from '../model/HttpError';

async function mountForm(key: 'bataillecorse' | 'bullshit', join: (id: string, name?: string) => Promise<void>, path = `/games/${key}/join`) {
  setActivePinia(createPinia());
  localStorage.clear();
  const router = createAppRouter(createMemoryHistory());
  await router.push(path);
  await router.isReady();
  const id = (router.currentRoute.value.params.id as string | undefined) ?? '';
  const wrapper = mount(JoinGameForm, {
    props: { game: findGame(key)!, join, initialId: id },
    global: { plugins: [router, PrimeVue] },
  });
  return { wrapper, router };
}

describe('JoinGameForm', () => {
  it('givenInviteLink_thenIdIsPrefilled', async () => {
    const { wrapper } = await mountForm('bullshit', vi.fn(), '/games/bullshit/join/abc');
    expect((wrapper.get('[data-test="game-id"]').element as HTMLInputElement).value).toBe('abc');
  });

  it('givenEmptyId_thenSubmitIsDisabled', async () => {
    const { wrapper } = await mountForm('bullshit', vi.fn());
    expect((wrapper.get('[data-test="submit"]').element as HTMLButtonElement).disabled).toBe(true);
    await wrapper.get('[data-test="game-id"]').setValue('abc');
    expect((wrapper.get('[data-test="submit"]').element as HTMLButtonElement).disabled).toBe(false);
  });

  for (const key of ['bataillecorse', 'bullshit'] as const) {
    it(`given_${key}_whenFullLinkPasted_thenJoinsWithBareIdAndEntersTheRoom`, async () => {
      const join = vi.fn().mockResolvedValue(undefined);
      const { wrapper, router } = await mountForm(key, join);
      await wrapper.get('[data-test="game-id"]').setValue(`http://localhost:5173/games/${key}/join/xyz`);
      await wrapper.get('[data-test="name"]').setValue('Zoe');
      await wrapper.get('form').trigger('submit');
      await flushPromises();
      expect(join).toHaveBeenCalledWith('xyz', 'Zoe');
      expect(router.currentRoute.value.path).toBe(`/games/${key}/room/xyz`);
    });
  }

  it('givenBlankName_thenJoinsWithoutName', async () => {
    const join = vi.fn().mockResolvedValue(undefined);
    const { wrapper } = await mountForm('bullshit', join);
    await wrapper.get('[data-test="game-id"]').setValue('abc');
    await wrapper.get('form').trigger('submit');
    await flushPromises();
    expect(join).toHaveBeenCalledWith('abc', undefined);
  });

  it('givenRefusedJoin_thenStaysOnTheFormWithItsErrorLine', async () => {
    const join = vi.fn().mockRejectedValue(new Error('Join failed: 404'));
    const { wrapper, router } = await mountForm('bataillecorse', join, '/games/bataillecorse/join/abc');
    await wrapper.get('form').trigger('submit');
    await flushPromises();
    expect(wrapper.get('[data-test="join-error"]').text()).toBe(messagesEn.join.failed);
    expect(wrapper.get('[data-test="join-error"]').attributes('role')).toBe('alert');
    expect(router.currentRoute.value.path).toBe('/games/bataillecorse/join/abc');
  });

  it('givenInviteToADeletedLobby_thenTheFormSaysTheGameNoLongerExists', async () => {
    const join = vi.fn().mockRejectedValue(new HttpError('Join failed: 404', 404));
    const { wrapper, router } = await mountForm('bullshit', join, '/games/bullshit/join/abc');
    await wrapper.get('form').trigger('submit');
    await flushPromises();
    expect(wrapper.get('[data-test="join-error"]').text()).toBe(messagesEn.join.notFound);
    expect(router.currentRoute.value.path).toBe('/games/bullshit/join/abc');
  });

  it('givenAFullGame_thenTheFormKeepsTheGenericLine', async () => {
    const join = vi.fn().mockRejectedValue(new HttpError('Join failed: 409', 409));
    const { wrapper } = await mountForm('bullshit', join, '/games/bullshit/join/abc');
    await wrapper.get('form').trigger('submit');
    await flushPromises();
    expect(wrapper.get('[data-test="join-error"]').text()).toBe(messagesEn.join.failed);
  });

  it('givenBack_thenLeadsToTheGameHome', async () => {
    const { wrapper, router } = await mountForm('bullshit', vi.fn());
    await wrapper.get('[data-test="back"]').trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.path).toBe('/games/bullshit');
  });
});

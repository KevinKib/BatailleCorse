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

const OPEN_ROW = { gameId: 'row1', gameType: 'bullshit', hostName: 'Zoe', joined: 1, bots: 0, maxPlayers: 4, state: 'OPEN' as const };
const publicGames = { lobbies: [OPEN_ROW], total: 1, truncated: false, loading: false, online: true, announcement: '' };

async function mountForm(key: 'bataillecorse' | 'bullshit', join: (id: string, name?: string) => Promise<void>, path = `/${key}/join`, withList = false) {
  setActivePinia(createPinia());
  localStorage.clear();
  const router = createAppRouter(createMemoryHistory());
  await router.push(path);
  await router.isReady();
  const id = (router.currentRoute.value.params.id as string | undefined) ?? '';
  const wrapper = mount(JoinGameForm, {
    props: { game: findGame(key)!, join, initialId: id, ...(withList ? { publicGames } : {}) },
    global: { plugins: [router, PrimeVue] },
  });
  return { wrapper, router };
}

describe('JoinGameForm', () => {
  it('givenInviteLink_thenIdIsPrefilled', async () => {
    const { wrapper } = await mountForm('bullshit', vi.fn(), '/bullshit/join/abc');
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
      await wrapper.get('[data-test="game-id"]').setValue(`http://localhost:5173/${key}/join/xyz`);
      await wrapper.get('[data-test="name"]').setValue('Zoe');
      await wrapper.get('form').trigger('submit');
      await flushPromises();
      expect(join).toHaveBeenCalledWith('xyz', 'Zoe');
      expect(router.currentRoute.value.path).toBe(`/${key}/room/xyz`);
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
    const { wrapper, router } = await mountForm('bataillecorse', join, '/bataillecorse/join/abc');
    await wrapper.get('form').trigger('submit');
    await flushPromises();
    expect(wrapper.get('[data-test="join-error"]').text()).toBe(messagesEn.join.failed);
    expect(wrapper.get('[data-test="join-error"]').attributes('role')).toBe('alert');
    expect(router.currentRoute.value.path).toBe('/bataillecorse/join/abc');
  });

  it('givenInviteToADeletedLobby_thenTheFormSaysTheGameNoLongerExists', async () => {
    const join = vi.fn().mockRejectedValue(new HttpError('Join failed: 404', 404));
    const { wrapper, router } = await mountForm('bullshit', join, '/bullshit/join/abc');
    await wrapper.get('form').trigger('submit');
    await flushPromises();
    expect(wrapper.get('[data-test="join-error"]').text()).toBe(messagesEn.join.notFound);
    expect(router.currentRoute.value.path).toBe('/bullshit/join/abc');
  });

  it('givenAFullLobby_thenTheFormSaysItIsFull', async () => {
    const join = vi.fn().mockRejectedValue(new HttpError('Join failed: 409', 409, 'full'));
    const { wrapper, router } = await mountForm('bullshit', join, '/bullshit/join/abc');
    await wrapper.get('form').trigger('submit');
    await flushPromises();
    expect(wrapper.get('[data-test="join-error"]').text()).toBe('This lobby is full.');
    expect(messagesEn.join.full).toBe('This lobby is full.');
    expect(router.currentRoute.value.path).toBe('/bullshit/join/abc');
  });

  it('givenAStartedGame_thenTheFormSaysItAlreadyStarted', async () => {
    const join = vi.fn().mockRejectedValue(new HttpError('Join failed: 409', 409, 'started'));
    const { wrapper } = await mountForm('bataillecorse', join, '/bataillecorse/join/abc');
    await wrapper.get('form').trigger('submit');
    await flushPromises();
    expect(wrapper.get('[data-test="join-error"]').text()).toBe('This game has already started.');
  });

  it('givenAConflictWithoutReason_thenTheFormKeepsTheGenericLine', async () => {
    const join = vi.fn().mockRejectedValue(new HttpError('Join failed: 409', 409));
    const { wrapper } = await mountForm('bullshit', join, '/bullshit/join/abc');
    await wrapper.get('form').trigger('submit');
    await flushPromises();
    expect(wrapper.get('[data-test="join-error"]').text()).toBe(messagesEn.join.failed);
  });

  it('givenBack_thenLeadsToTheGameHome', async () => {
    const { wrapper, router } = await mountForm('bullshit', vi.fn());
    await wrapper.get('[data-test="back"]').trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.path).toBe('/bullshit');
  });

  it('putsYourNameBeforeTheGameId', async () => {
    const { wrapper } = await mountForm('bullshit', vi.fn());
    const fields = wrapper.findAll('input[data-test="name"], input[data-test="game-id"]');
    expect(fields.map(f => f.attributes('data-test'))).toEqual(['name', 'game-id']);
  });

  it('givenNoPublicGamesData_thenShowsNoListSection', async () => {
    const { wrapper } = await mountForm('bullshit', vi.fn());
    expect(wrapper.find('[data-test="public-games"]').exists()).toBe(false);
  });

  it('givenPublicGames_thenShowsTheSeparatorAndTheListBetweenJoinAndBack', async () => {
    const { wrapper } = await mountForm('bullshit', vi.fn(), '/bullshit/join', true);
    expect(wrapper.get('[data-test="join-or"]').text()).toBe(messagesEn.join.or);
    const order = wrapper.findAll('[data-test="submit"], [data-test="join-or"], [data-test="public-games"], [data-test="back"]')
      .map(e => e.attributes('data-test'));
    expect(order).toEqual(['submit', 'join-or', 'public-games', 'back']);
  });

  it('keepsTheTitleSubtitleAndEveryControlWithTheList', async () => {
    const { wrapper } = await mountForm('bullshit', vi.fn(), '/bullshit/join', true);
    expect(wrapper.text()).toContain(messagesEn.join.title);
    for (const t of ['game-id', 'name', 'submit', 'back']) expect(wrapper.find(`[data-test="${t}"]`).exists()).toBe(true);
    expect(wrapper.text()).toContain(messagesEn.join.gameId);
    expect(wrapper.text()).toContain(messagesEn.join.yourName);
  });

  it('whenJoiningFromARow_thenJoinsWithTheNameAndEntersTheRoom', async () => {
    const join = vi.fn().mockResolvedValue(undefined);
    const { wrapper, router } = await mountForm('bullshit', join, '/bullshit/join', true);
    await wrapper.get('[data-test="name"]').setValue('Ana');
    await wrapper.get('[data-test="pg-join"]').trigger('click');
    await flushPromises();
    expect(join).toHaveBeenCalledWith('row1', 'Ana');
    expect(router.currentRoute.value.path).toBe('/bullshit/room/row1');
  });

  it('whenARowJoinIsRefusedAsFull_thenTheMessageIsInThatRowAndNotInTheForm', async () => {
    const join = vi.fn().mockRejectedValue(new HttpError('Join failed: 409', 409, 'full'));
    const { wrapper, router } = await mountForm('bullshit', join, '/bullshit/join', true);
    await wrapper.get('[data-test="pg-join"]').trigger('click');
    await flushPromises();
    expect(wrapper.get('[data-test="pg-row-error"]').text()).toBe(messagesEn.join.full);
    expect(wrapper.find('[data-test="join-error"]').exists()).toBe(false);
    expect(router.currentRoute.value.path).toBe('/bullshit/join');
  });
});

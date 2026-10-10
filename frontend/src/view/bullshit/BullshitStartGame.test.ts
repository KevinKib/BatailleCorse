import { describe, it, expect, beforeEach, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { setActivePinia, createPinia } from 'pinia';
import { createRouter, createMemoryHistory } from 'vue-router';
import PrimeVue from 'primevue/config';
import BullshitStartGame from './BullshitStartGame.vue';
import { useBullshitStore } from '../../state/Bullshit.store';
import { messagesEn } from '../../locales/en';

const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/', name: 'bullshit-create', component: { template: '<div/>' } },
    { path: '/join/:id?', name: 'bullshit-join', component: { template: '<div/>' } },
    { path: '/games/bullshit/room/:id', component: { template: '<div/>' } },
  ],
});

async function mountAt(path: string) {
  await router.push(path);
  await router.isReady();
  return mount(BullshitStartGame, { global: { plugins: [router, PrimeVue] } });
}
const mountCreate = () => mountAt('/');
const mountJoin = (id = '') => mountAt(`/join/${id}`);

describe('BullshitStartGame claim mode', () => {
  beforeEach(() => { setActivePinia(createPinia()); });

  it('creates with rank by default', async () => {
    const wrapper = await mountCreate();
    const store = useBullshitStore();
    const create = vi.spyOn(store, 'create').mockImplementation(() => {});

    await wrapper.find('input[type="text"]').setValue('Alice');
    await wrapper.get('form').trigger('submit');

    expect(create).toHaveBeenCalledWith('Alice', 'rank');
  });

  it('creates with suit when the suit radio is selected', async () => {
    const wrapper = await mountCreate();
    const store = useBullshitStore();
    const create = vi.spyOn(store, 'create').mockImplementation(() => {});

    await wrapper.find('input[type="text"]').setValue('Alice');
    await wrapper.find('input[type="radio"][value="suit"]').setValue();
    await wrapper.get('form').trigger('submit');

    expect(create).toHaveBeenCalledWith('Alice', 'suit');
  });

  it('marks the chosen claim mode as active', async () => {
    const wrapper = await mountCreate();
    await wrapper.find('input[type="radio"][value="suit"]').setValue();
    const active = wrapper.findAll('.mode-option--active');
    expect(active).toHaveLength(1);
    expect(active[0].text()).toBe(messagesEn.bullshitUi.start.claimModes.suit);
  });

  it('labels every field for assistive tech', async () => {
    const wrapper = await mountCreate();
    const input = wrapper.get('#bullshit-name');
    expect(wrapper.get('label[for="bullshit-name"]').text()).toBe(messagesEn.bullshitUi.start.yourName);
    expect(input.attributes('placeholder')).toBe(messagesEn.bullshitUi.start.namePlaceholder);
  });
});

describe('BullshitStartGame errors', () => {
  beforeEach(() => { setActivePinia(createPinia()); vi.useRealTimers(); });

  it('shows an error and stays on the page when joining fails', async () => {
    const wrapper = await mountJoin('abc');
    const store = useBullshitStore();
    vi.spyOn(store, 'join').mockRejectedValue(new Error('Join failed: 404'));
    const push = vi.spyOn(router, 'push');
    push.mockClear();

    await wrapper.get('form').trigger('submit');
    await flushPromises();

    expect(wrapper.get('[data-test="start-error"]').text()).toBe(messagesEn.bullshitUi.start.errors.joinFailed);
    expect(push).not.toHaveBeenCalled();
  });

  it('joins and navigates to the room on success, accepting a pasted link', async () => {
    const wrapper = await mountJoin();
    const store = useBullshitStore();
    const join = vi.spyOn(store, 'join').mockResolvedValue();
    const push = vi.spyOn(router, 'push');
    push.mockClear();

    await wrapper.get('[data-test="game-id"]').setValue('http://localhost:5173/games/bullshit/join/abc');
    await wrapper.get('form').trigger('submit');
    await flushPromises();

    expect(join).toHaveBeenCalledWith('abc', undefined);
    expect(push).toHaveBeenCalledWith('/games/bullshit/room/abc');
    expect(wrapper.find('[data-test="start-error"]').exists()).toBe(false);
  });

  it('disables Join until a game id is entered', async () => {
    const wrapper = await mountJoin();
    expect((wrapper.get('[data-test="submit"]').element as HTMLButtonElement).disabled).toBe(true);
    await wrapper.get('[data-test="game-id"]').setValue('abc');
    expect((wrapper.get('[data-test="submit"]').element as HTMLButtonElement).disabled).toBe(false);
  });

  it('shows an error when the create request cannot be sent', async () => {
    const wrapper = await mountCreate();
    const store = useBullshitStore();
    vi.spyOn(store, 'create').mockImplementation(() => { throw new Error('no STOMP connection'); });

    await wrapper.get('form').trigger('submit');

    expect(wrapper.get('[data-test="start-error"]').text()).toBe(messagesEn.bullshitUi.start.errors.createFailed);
  });

  it('shows an error when the server never confirms the new game', async () => {
    vi.useFakeTimers();
    const wrapper = await mountCreate();
    const store = useBullshitStore();
    vi.spyOn(store, 'create').mockImplementation(() => {});

    await wrapper.get('form').trigger('submit');
    await vi.advanceTimersByTimeAsync(8100);

    expect(wrapper.get('[data-test="start-error"]').text()).toBe(messagesEn.bullshitUi.start.errors.createFailed);
    vi.useRealTimers();
  });
});

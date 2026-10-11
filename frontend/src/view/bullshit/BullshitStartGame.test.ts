import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { mount } from '@vue/test-utils';
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
    { path: '/games/bullshit', name: 'game-home', component: { template: '<div/>' } },
    { path: '/games/bullshit/room/:id', component: { template: '<div/>' } },
  ],
});

async function mountAt(path: string) {
  await router.push(path);
  await router.isReady();
  return mount(BullshitStartGame, { global: { plugins: [router, PrimeVue] } });
}
const mountCreate = () => mountAt('/');

describe('BullshitStartGame claim mode', () => {
  beforeEach(() => { setActivePinia(createPinia()); });

  it('creates with rank by default', async () => {
    const wrapper = await mountCreate();
    const store = useBullshitStore();
    const create = vi.spyOn(store, 'create').mockImplementation(() => {});

    await wrapper.find('input[type="text"]').setValue('Alice');
    await wrapper.get('form').trigger('submit');

    expect(create).toHaveBeenCalledWith('Alice', 'rank', '32');
  });

  it('creates with 52 cards when the 52 radio is selected', async () => {
    const wrapper = await mountCreate();
    const store = useBullshitStore();
    const create = vi.spyOn(store, 'create').mockImplementation(() => {});

    await wrapper.find('input[type="radio"][value="52"]').setValue();
    await wrapper.get('form').trigger('submit');

    expect(create).toHaveBeenCalledWith(undefined, 'rank', '52');
  });

  it('shows the rank range of the chosen deck', async () => {
    const wrapper = await mountCreate();
    const rank = () => wrapper.get('[data-test="claim-mode-field"] .mode-option').text();
    expect(rank()).toBe('By rank (7→A)');
    await wrapper.find('input[type="radio"][value="52"]').setValue();
    expect(rank()).toBe('By rank (A→K)');
  });

  it('preselects 32 cards and shows localised deck size labels', async () => {
    const wrapper = await mountCreate();
    const active = wrapper.findAll('[data-test="deck-size"] .mode-option--active');
    expect(active).toHaveLength(1);
    expect(active[0].text()).toBe(messagesEn.bullshitUi.start.deckSizes['32']);
    expect(wrapper.get('[data-test="deck-size"]').text()).toContain(messagesEn.bullshitUi.start.deckSizes['52']);
  });

  it('creates with suit when the suit radio is selected', async () => {
    const wrapper = await mountCreate();
    const store = useBullshitStore();
    const create = vi.spyOn(store, 'create').mockImplementation(() => {});

    await wrapper.find('input[type="text"]').setValue('Alice');
    await wrapper.find('input[type="radio"][value="suit"]').setValue();
    await wrapper.get('form').trigger('submit');

    expect(create).toHaveBeenCalledWith('Alice', 'suit', '32');
  });

  it('marks the chosen claim mode as active', async () => {
    const wrapper = await mountCreate();
    await wrapper.find('input[type="radio"][value="suit"]').setValue();
    const active = wrapper.findAll('[data-test="claim-mode-field"] .mode-option--active');
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

describe('BullshitStartGame name', () => {
  beforeEach(() => { setActivePinia(createPinia()); localStorage.setItem('bc_playerName', 'Alice'); setActivePinia(createPinia()); });
  afterEach(() => { localStorage.clear(); });

  it('prefills the name saved on the Game home', async () => {
    const wrapper = await mountCreate();
    expect((wrapper.get('[data-test="name"]').element as HTMLInputElement).value).toBe('Alice');
  });

  it('goes back to the Game home', async () => {
    const wrapper = await mountCreate();
    await wrapper.get('[data-test="back"]').trigger('click');
    await new Promise(r => setTimeout(r));
    expect(router.currentRoute.value.path).toBe('/games/bullshit');
  });
});

describe('BullshitStartGame errors', () => {
  beforeEach(() => { setActivePinia(createPinia()); vi.useRealTimers(); });

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

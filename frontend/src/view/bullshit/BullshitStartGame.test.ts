import { describe, it, expect, beforeEach, vi } from 'vitest';
import { mount } from '@vue/test-utils';
import { setActivePinia, createPinia } from 'pinia';
import { createRouter, createMemoryHistory } from 'vue-router';
import BullshitStartGame from './BullshitStartGame.vue';
import { useBullshitStore } from '../../state/Bullshit.store';

const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/', name: 'bullshit-create', component: { template: '<div/>' } },
    { path: '/join/:id?', name: 'bullshit-join', component: { template: '<div/>' } },
    { path: '/games/bullshit/room/:id', component: { template: '<div/>' } },
  ],
});

async function mountCreate() {
  await router.push('/');
  await router.isReady();
  return mount(BullshitStartGame, { global: { plugins: [router] } });
}

describe('BullshitStartGame claim mode', () => {
  beforeEach(() => { setActivePinia(createPinia()); });

  it('creates with rank by default', async () => {
    const wrapper = await mountCreate();
    const store = useBullshitStore();
    const create = vi.spyOn(store, 'create').mockImplementation(() => {});

    await wrapper.find('input[type="text"]').setValue('Alice');
    await wrapper.find('button.primary').trigger('click');

    expect(create).toHaveBeenCalledWith('Alice', 'rank');
  });

  it('creates with suit when the suit radio is selected', async () => {
    const wrapper = await mountCreate();
    const store = useBullshitStore();
    const create = vi.spyOn(store, 'create').mockImplementation(() => {});

    await wrapper.find('input[type="text"]').setValue('Alice');
    await wrapper.find('input[type="radio"][value="suit"]').setValue();
    await wrapper.find('button.primary').trigger('click');

    expect(create).toHaveBeenCalledWith('Alice', 'suit');
  });
});

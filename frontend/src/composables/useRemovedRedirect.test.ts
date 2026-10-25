import { describe, it, expect } from 'vitest';
import { defineComponent, h, ref, nextTick } from 'vue';
import { mount, flushPromises } from '@vue/test-utils';
import { createRouter, createMemoryHistory } from 'vue-router';
import { useRemovedRedirect } from './useRemovedRedirect';

describe('useRemovedRedirect', () => {
  it('leads to the Game selection with a notice once the flag turns true, and consumes the flag', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/', name: 'games', component: { render: () => h('div') } },
        { path: '/room', component: { render: () => h('div') } },
      ],
    });
    await router.push('/room');
    const removed = ref(false);
    const Host = defineComponent({
      setup() {
        useRemovedRedirect(removed, () => { removed.value = false; });
        return () => h('div');
      },
    });
    mount(Host, { global: { plugins: [router] } });
    expect(router.currentRoute.value.path).toBe('/room');

    removed.value = true;
    await nextTick();
    await flushPromises();

    expect(router.currentRoute.value.path).toBe('/');
    expect(router.currentRoute.value.query.removed).toBe('1');
    expect(removed.value).toBe(false);
  });
});

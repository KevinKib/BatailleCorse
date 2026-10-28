<script setup lang="ts">
import { computed, nextTick, useTemplateRef, watch } from 'vue';
import { useI18n } from '../../composables/useI18n';
import { format } from '../../locales/format';

// The notation of every move so far, two to a row (White, then Black), newest last. The server writes the notation
// (`32-28`, `19x30`); this only lays it out. The table decides where it sits and whether it shows.
const props = defineProps<{ history: string[] }>();
const emit = defineEmits<{ close: [] }>();

const ui = useI18n().draughtsUi.moves;
const rows = computed(() => Array.from({ length: Math.ceil(props.history.length / 2) }, (_, i) => ({
  n: i + 1,
  white: props.history[2 * i],
  black: props.history[2 * i + 1] ?? '',
})));

// The newest move stays in view.
const scroller = useTemplateRef<HTMLElement>('scroller');
watch(() => props.history.length, async () => {
  await nextTick();
  if (scroller.value) scroller.value.scrollTop = scroller.value.scrollHeight;
});
</script>

<template>
  <section class="moves" data-test="moves" :aria-label="format(ui.label, { n: history.length })">
    <div class="moves-header">
      <h2 class="moves-title">{{ ui.title }}</h2>
      <button type="button" class="moves-hide" data-test="moves-hide" :aria-label="ui.hide" @click="emit('close')">
        <i class="pi pi-minus" aria-hidden="true"></i>
      </button>
    </div>
    <p v-if="history.length === 0" class="moves-empty" data-test="moves-empty">{{ ui.empty }}</p>
    <div v-else ref="scroller" class="moves-scroll">
      <div class="moves-grid">
        <span class="moves-head">{{ ui.number }}</span>
        <span class="moves-head" data-test="moves-head-white">{{ ui.white }}</span>
        <span class="moves-head" data-test="moves-head-black">{{ ui.black }}</span>
        <template v-for="row in rows" :key="row.n">
          <div class="moves-row" data-test="moves-row">
            <span class="moves-n" data-test="moves-cell-n">{{ row.n }}</span>
            <span data-test="moves-cell-white">{{ row.white }}</span>
            <span data-test="moves-cell-black">{{ row.black }}</span>
          </div>
        </template>
      </div>
    </div>
  </section>
</template>

<style scoped>
.moves {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  min-height: 0;
  box-sizing: border-box;
  padding: var(--space-3) var(--space-4);
  border-radius: 12px;
  color: #ffffff;
  background: rgba(0, 0, 0, 0.38);
  border: 1px solid rgba(255, 255, 255, 0.1);
}
.moves-header { display: flex; align-items: center; justify-content: space-between; }
.moves-title { margin: 0; font-size: 0.95rem; font-weight: 700; }
.moves-hide {
  width: 44px;
  height: 44px;
  margin: calc(var(--space-2) * -1) calc(var(--space-3) * -1) calc(var(--space-2) * -1) 0;
  display: grid;
  place-items: center;
  background: none;
  border: 0;
  color: rgba(255, 255, 255, 0.8);
  cursor: pointer;
}
.moves-hide:focus-visible { outline: 2px solid var(--gold); outline-offset: -4px; }
.moves-empty { margin: 0; font-size: 0.85rem; color: rgba(255, 255, 255, 0.7); }
.moves-scroll { min-height: 0; overflow-y: auto; }
.moves-grid {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr) minmax(0, 1fr);
  row-gap: var(--space-1);
  column-gap: var(--space-2);
  font-size: 0.875rem;
  font-variant-numeric: tabular-nums;
}
.moves-head { font-size: 0.75rem; color: rgba(255, 255, 255, 0.6); }
/* A row is a contents wrapper: its three cells sit in the grid's columns. */
.moves-row { display: contents; }
.moves-n { color: rgba(255, 255, 255, 0.6); }
</style>

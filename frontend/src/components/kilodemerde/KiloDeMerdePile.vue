<script setup lang="ts">
import { computed } from 'vue';
import PlayingCard from '../PlayingCard.vue';
import { useI18n } from '../../composables/useI18n';
import { plural } from '../../locales/format';

// The kilo pile, face down: it is the one target of the slap. A button of at least 96 px that keeps its name in every
// state ("Slap the pile" and the cards left); the server says whether a slap is possible, the screen only enables it.
const props = defineProps<{
  count: number;
  canSlap: boolean;
  /** A race is open and I have not slapped: the button pulses. */
  racing?: boolean;
}>();
defineEmits<{ slap: [] }>();
const ui = useI18n().kiloDeMerdeUi;

const name = computed(() => `${ui.table.slap}, ${plural(ui.table.pileCount, props.count)}`);
</script>

<template>
  <button type="button" class="pile" :class="{ 'pile--racing': racing }" data-test="pile"
          :aria-label="name" :disabled="!canSlap" @click="$emit('slap')">
    <PlayingCard :hidden="true" rank="ace" suit="spade" />
    <span class="pile-count" data-test="pile-count" aria-hidden="true">{{ count }}</span>
    <span class="pile-label" aria-hidden="true">{{ ui.table.slap }}</span>
  </button>
</template>

<style scoped>
.pile {
  position: relative;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-1);
  min-width: 96px;
  min-height: 96px;
  padding: var(--space-1);
  background: rgba(0, 0, 0, 0.35);
  border: 1px solid rgba(255, 255, 255, 0.25);
  border-radius: 12px;
  color: #fff;
  font: inherit;
  cursor: pointer;
  transition: transform 0.12s ease-out, box-shadow 0.12s ease-out;
}
.pile:disabled { cursor: default; opacity: 0.8; }
.pile:not(:disabled):active { transform: scale(0.97); }
.pile:focus-visible { outline: 2px solid var(--gold); outline-offset: 2px; }
.pile :deep(.playing_card) { display: block; width: 44px; height: auto; aspect-ratio: 167.575 / 243.1375; }
.pile-label { font-size: 0.75rem; font-weight: 700; letter-spacing: 0.04em; }
.pile-count {
  position: absolute; top: 2px; right: 2px;
  min-width: 22px; height: 22px; padding: 0 5px; box-sizing: border-box;
  border-radius: 11px; background: #141413; border: 1px solid rgba(255, 255, 255, 0.3);
  font-size: 0.75rem; line-height: 20px; text-align: center; font-variant-numeric: tabular-nums;
}
.pile--racing { border-color: var(--gold); animation: pile-pulse 0.9s ease-in-out infinite; }
@keyframes pile-pulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(245, 200, 66, 0.6); }
  50% { box-shadow: 0 0 0 10px rgba(245, 200, 66, 0); }
}
@media (prefers-reduced-motion: reduce) {
  .pile { transition: none; }
  .pile--racing { animation: none; box-shadow: 0 0 0 3px rgba(245, 200, 66, 0.7); }
}
</style>

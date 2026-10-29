<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useI18n } from '../../composables/useI18n';
import { format } from '../../locales/format';
import { kiloLabel, kiloTotal } from '../../model/kilodemerde/kilo';
import type Card from '../../model/Card';

// The kilo cards a seat holds and their weight, opened from a plate. Escape or the close button closes it.
const props = defineProps<{ name: string; cards: Card[]; mine?: boolean }>();
const emit = defineEmits<{ close: [] }>();
const ui = useI18n().kiloDeMerdeUi;

const title = computed(() => props.mine ? ui.kiloPopover.titleYou : format(ui.kiloPopover.title, { player: props.name }));

const total = computed(() => kiloTotal(props.cards));
const root = ref<HTMLElement | null>(null);
onMounted(() => root.value?.focus());
</script>

<template>
  <div ref="root" class="popover" data-test="kilo-popover" role="dialog" tabindex="-1"
       :aria-label="title" @keydown.esc="emit('close')">
    <div class="popover-head">
      <strong class="popover-title">{{ title }}</strong>
      <button type="button" class="popover-close" data-test="kilo-close" :aria-label="ui.back" @click="emit('close')">
        <i class="pi pi-times" aria-hidden="true"></i>
      </button>
    </div>
    <p v-if="cards.length === 0" class="popover-none" data-test="kilo-none">{{ ui.kiloPopover.none }}</p>
    <ul v-else class="popover-cards">
      <li v-for="card in cards" :key="card.name" class="popover-card" data-test="kilo-card">{{ kiloLabel(card) }}</li>
    </ul>
    <p class="popover-total" data-test="kilo-total">{{ format(ui.kiloPopover.total, { kg: total }) }}</p>
  </div>
</template>

<style scoped>
.popover {
  box-sizing: border-box;
  width: max-content;
  max-width: min(16rem, calc(100% - 16px));
  padding: var(--space-2) var(--space-3);
  border-radius: 12px;
  background: rgba(0, 0, 0, 0.9);
  border: 1px solid rgba(255, 255, 255, 0.25);
  color: #fff;
  box-shadow: 0 6px 22px rgba(0, 0, 0, 0.55);
}
.popover:focus-visible { outline: 2px solid var(--gold); }
.popover-head { display: flex; align-items: center; justify-content: space-between; gap: var(--space-2); }
.popover-title { font-size: 0.875rem; overflow-wrap: anywhere; }
.popover-close {
  flex: none; width: 44px; height: 44px; margin: -8px -8px -8px 0;
  background: none; border: none; color: inherit; cursor: pointer;
}
.popover-cards { margin: var(--space-1) 0; padding: 0; list-style: none; display: flex; flex-wrap: wrap; gap: var(--space-1); }
.popover-card {
  padding: 0 8px; border-radius: 6px; background: rgba(255, 255, 255, 0.12);
  font-size: 0.875rem; font-weight: 700; line-height: 1.75rem;
}
.popover-none { margin: var(--space-1) 0; font-size: 0.8125rem; color: rgba(255, 255, 255, 0.75); }
.popover-total { margin: 0; font-size: 0.8125rem; font-variant-numeric: tabular-nums; color: var(--gold); }
</style>

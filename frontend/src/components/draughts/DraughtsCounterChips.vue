<script setup lang="ts">
import { computed } from 'vue';
import { useI18n } from '../../composables/useI18n';
import { plural } from '../../locales/format';
import type { DraughtsDrawCounters } from '../../model/draughts/DraughtsState';

// The draw counts that have started, as the server sends them (each part is present only once it matters, so no
// limit or threshold lives here): the kings-only count and the short ending, in moves each player has left, and the
// warning that a position has been seen twice.
const props = defineProps<{
  counters?: DraughtsDrawCounters;
  repeatedTwice: boolean;
}>();

const ui = useI18n().draughtsUi.table;
// The server counts plies; a player thinks in moves of their own, two plies each.
const movesLeft = (plies: number, limit: number): number => Math.max(Math.ceil((limit - plies) / 2), 0);

const kingsOnly = computed(() => {
  const c = props.counters;
  return c?.kingOnlyPlies !== undefined && c.kingOnlyLimit !== undefined
    ? plural(ui.kingsOnly, movesLeft(c.kingOnlyPlies, c.kingOnlyLimit))
    : null;
});
const shortEnding = computed(() => {
  const endgame = props.counters?.endgame;
  return endgame ? plural(ui.shortEnding, movesLeft(endgame.plies, endgame.limit)) : null;
});
</script>

<template>
  <div class="chips">
    <span v-if="shortEnding" class="chip chip--warn" data-test="short-ending" :data-case="counters?.endgame?.case" role="status">
      <svg class="chip-icon" viewBox="0 0 12 12" aria-hidden="true" focusable="false">
        <circle cx="6" cy="6" r="5" fill="none" stroke="currentColor" stroke-width="1.5" />
        <path d="M6 3 V6 L8 7.5" stroke="currentColor" stroke-width="1.5" fill="none" stroke-linecap="round" />
      </svg>
      {{ shortEnding }}
    </span>
    <span v-if="kingsOnly" class="chip" data-test="kings-only" role="status">{{ kingsOnly }}</span>
    <span v-if="repeatedTwice" class="chip chip--note" data-test="repetition-hint" role="status">{{ ui.repetition }}</span>
  </div>
</template>

<style scoped>
.chips {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: var(--space-2);
}
.chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 28px;
  box-sizing: border-box;
  max-width: 100%;
  padding: 2px 10px;
  border-radius: 14px;
  font-size: 0.75rem;
  font-variant-numeric: tabular-nums;
  color: rgba(255, 255, 255, 0.85);
  background: rgba(0, 0, 0, 0.5);
  border: 1px solid rgba(255, 255, 255, 0.2);
  overflow-wrap: anywhere;
}
.chip--warn { color: #fecaca; border-color: rgba(248, 113, 113, 0.7); }
.chip--note { color: #f5e29a; border-color: rgba(245, 226, 154, 0.45); }
.chip-icon { flex: none; width: 12px; height: 12px; }
</style>

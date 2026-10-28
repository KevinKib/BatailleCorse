<script setup lang="ts">
import { computed } from 'vue';
import { useI18n } from '../../composables/useI18n';
import { plural } from '../../locales/format';

// The lives of a seat as hearts: full for a life left, empty for one lost. The row is at least as long as the lives
// the game started with (so a lost life leaves its empty heart), and never shorter than the lives held.
const props = defineProps<{
  lives: number;
  /** The lives each player started with (the `lives` option). */
  max: number;
  /** Heart size in px. */
  size?: number;
}>();
const ui = useI18n().ninetyNineUi;
const hearts = computed(() => Array.from({ length: Math.max(props.max, props.lives) }, (_, i) => i < props.lives));
const label = computed(() => plural(ui.table.lives, props.lives));
</script>

<template>
  <span class="lives" role="img" :aria-label="label" data-test="lives">
    <svg v-for="(full, i) in hearts" :key="i" class="heart" data-test="heart" :data-full="full"
         :width="size ?? 12" :height="(size ?? 12) * 0.9" viewBox="0 0 10 9" aria-hidden="true">
      <path d="M5 8.4 L1.2 4.6 A2.2 2.2 0 0 1 5 1.6 A2.2 2.2 0 0 1 8.8 4.6 Z"
            :fill="full ? 'currentColor' : 'transparent'" stroke="currentColor" stroke-width="1" />
    </svg>
  </span>
</template>

<style scoped>
.lives { display: inline-flex; align-items: center; gap: 3px; color: #f87171; line-height: 1; }
.heart { flex: none; }
</style>

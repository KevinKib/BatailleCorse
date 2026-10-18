<script setup lang="ts">
import { computed } from 'vue';
import PlayingCard from '../PlayingCard.vue';
import { fanLayout } from '../../model/president/handFan';
import { useI18n } from '../../composables/useI18n';
import { plural } from '../../locales/format';

// How many cards a seat still holds, as a picture: a small fan of card backs (one per card up to a cap, tighter as it
// grows), with the number written under it ("7 cards"), which is also what assistive technology reads (the picture
// is hidden from it).
const props = defineProps<{ count: number; narrow?: boolean }>();
const ui = useI18n().presidentUi;
const layout = computed(() => fanLayout(props.count, props.narrow ?? false));
const width = computed(() => layout.value.backs > 0
  ? layout.value.cardW + (layout.value.backs - 1) * layout.value.stepPx : 0);
</script>

<template>
  <span class="hand-fan" data-test="seat-fan" :data-count="count">
    <span class="fan-picture" aria-hidden="true">
      <span v-if="layout.backs > 0" class="fan-backs" :style="{ width: `${width}px`, '--fan-card-w': `${layout.cardW}px` }">
        <span v-for="i in layout.backs" :key="i" class="fan-back" data-test="fan-back"
              :style="{ left: `${(i - 1) * layout.stepPx}px`, transform: `rotate(${(i - (layout.backs + 1) / 2) * 3}deg)` }">
          <PlayingCard :hidden="true" rank="ace" suit="spade" />
        </span>
      </span>
    </span>
    <span class="fan-text" data-test="seat-cards">{{ plural(ui.table.cards, count) }}</span>
  </span>
</template>

<style scoped>
.hand-fan { display: inline-flex; flex-direction: column; align-items: center; gap: 2px; max-width: 100%; }
.fan-picture { display: inline-flex; align-items: center; }
.fan-backs { position: relative; display: block; height: calc(var(--fan-card-w) * 1.451); }
.fan-back { position: absolute; top: 0; width: var(--fan-card-w); transform-origin: 50% 100%; }
.fan-back :deep(.playing_card) {
  display: block; width: 100%; height: auto; aspect-ratio: 167.575 / 243.1375;
  border-radius: 8%; box-shadow: 0 1px 3px rgba(0, 0, 0, 0.55);
}
.fan-text {
  font-size: 0.68rem;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  color: rgba(255, 255, 255, 0.85);
  white-space: nowrap;
}
@media (max-width: 767px) { .fan-text { font-size: 0.6rem; } }
</style>

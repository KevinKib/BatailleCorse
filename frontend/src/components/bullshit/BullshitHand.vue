<script setup lang="ts">
import { computed, ref } from 'vue';
import PlayingCard from '../PlayingCard.vue';
import { useHandLayout } from '../../composables/useHandLayout';
import { useI18n } from '../../composables/useI18n';
import { format } from '../../locales/format';
import type Card from '../../model/Card';
import { sortHand } from '../../model/bullshit/sortHand';

// The player's hand, sorted by rank (suits grouped) for display only. Cards overlap on one
// row; when that would hide too much of each card they go on two overlapping rows instead,
// so any hand fits without scrolling. A selected card is raised above its neighbours.
const props = defineProps<{
  cards: Card[];
  selected: Card[];
}>();
const emit = defineEmits<{ toggle: [card: Card] }>();

const ui = useI18n().bullshitUi;
const root = ref<HTMLElement | null>(null);
const { layout } = useHandLayout(root, () => props.cards.length);

const sorted = computed(() => sortHand(props.cards));
// The cards of each row with their displayed index (also the test id), the first row taking the extra card.
const rows = computed(() => {
  const indexed = sorted.value.map((card, index) => ({ card, index }));
  return layout.value.rows === 1
    ? [indexed]
    : [indexed.slice(0, layout.value.perRow), indexed.slice(layout.value.perRow)];
});

const isSelected = (card: Card) => props.selected.some(c => c.name === card.name);
const cardLabel = (card: Card) =>
  format(ui.table.cardLabel, { rank: card.rank.toLowerCase(), suit: card.suit.toLowerCase() });
</script>

<template>
  <div
    ref="root"
    class="hand"
    :style="{ '--card-w': layout.cardWidth + 'px', '--step': layout.step + 'px' }">
    <div v-for="(row, r) in rows" :key="r" class="hand-row">
      <button
        v-for="{ card, index } in row"
        :key="card.name"
        :data-test="`hand-card-${index}`"
        class="hand-card"
        :class="{ selected: isSelected(card) }"
        type="button"
        :aria-label="cardLabel(card)"
        :aria-pressed="isSelected(card)"
        @click="emit('toggle', card)">
        <PlayingCard :rank="card.rank" :suit="card.suit" />
      </button>
    </div>
  </div>
</template>

<style scoped>
.hand {
  display: flex;
  flex-direction: column;
  width: 100%;
  /* Room above the row for the raised (selected) card. */
  padding-top: var(--space-3);
  box-sizing: border-box;
}
.hand-row {
  display: flex;
  flex-wrap: nowrap;
  justify-content: center;
}
/* Second row: tucked under the first, which keeps the top part of its cards (rank and suit)
   visible. Later in the DOM, so it paints over the first row. */
.hand-row + .hand-row {
  position: relative;
  margin-top: calc(var(--card-w) * -0.7);
}
.hand-card {
  flex: none;
  /* Positioned for every card, so a raised (transformed) card does not paint over the
     cards after it: stacking stays in DOM order. */
  position: relative;
  width: var(--card-w);
  background: none;
  border: none;
  padding: 0;
  cursor: pointer;
  border-radius: 6%;
  transition: transform 0.16s ease-out, box-shadow 0.16s ease-out;
}
/* Neighbours overlap by (card width - step); a negative margin, or the plain gap when spaced. */
.hand-card + .hand-card { margin-left: calc(var(--step) - var(--card-w)); }
.hand-card:focus-visible { outline: 2px solid var(--gold); outline-offset: 2px; }
/* Raised, but kept in DOM order: bringing it to the front would hide the index of the cards
   it overlaps and make them untappable in a crowded hand. */
.hand-card.selected {
  transform: translateY(calc(-1 * var(--space-3)));
  box-shadow: 0 4px 6px rgba(0, 0, 0, 0.4);
}
.hand-card :deep(.playing_card) {
  display: block;
  width: 100%;
  height: auto;
  aspect-ratio: 167.575 / 243.1375;
}

@media (prefers-reduced-motion: reduce) {
  .hand-card { transition: none; }
}
</style>
